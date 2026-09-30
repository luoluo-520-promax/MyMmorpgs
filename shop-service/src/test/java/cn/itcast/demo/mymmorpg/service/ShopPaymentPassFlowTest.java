package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.client.ShopActivityRechargeClient;
import cn.itcast.demo.mymmorpg.client.ShopBagGrantClient;
import cn.itcast.demo.mymmorpg.client.ShopSkinGrantClient;
import cn.itcast.demo.mymmorpg.port.PlayerNotificationPort;
import cn.itcast.demo.mymmorpg.protocol.protobuf.CreateShopOrderCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.CreateShopOrderScRsp;
import cn.itcast.demo.mymmorpg.shop.MockShopPaymentVerifier;
import cn.itcast.demo.mymmorpg.shop.ShopConfigService;
import cn.itcast.demo.mymmorpg.shop.ShopOrderPaidEventPublisher;
import cn.itcast.demo.mymmorpg.shop.ShopPaymentVerifierRegistry;
import cn.itcast.demo.mymmorpg.shop.ShopProductConfig;
import cn.itcast.demo.mymmorpg.shop.ShopRewardConfig;
import cn.itcast.demo.mymmorpg.skin.SkinConfigRepository;
import cn.itcast.demo.mymmorpg.support.ShopMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 支付履约 → 战令进度完整链路：下单 → mockPay → XP/付费档/月卡生效。
 */
public class ShopPaymentPassFlowTest {

    private final Map<String, String> redisStore = new ConcurrentHashMap<>();
    private ShopConfigService shopConfigService;
    private PassService passService;
    private ShopService shopService;

    @BeforeMethod
    @SuppressWarnings("unchecked")
    public void setUp() {
        redisStore.clear();
        shopConfigService = mock(ShopConfigService.class);
        ObjectProvider tlog = mock(ObjectProvider.class);
        when(tlog.getIfAvailable()).thenReturn(null);
        passService = new PassService(tlog);

        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        ListOperations<String, String> listOps = mock(ListOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.opsForList()).thenReturn(listOps);
        when(valueOps.get(anyString())).thenAnswer(inv -> redisStore.get(inv.getArgument(0)));
        lenient().doAnswer(inv -> {
            redisStore.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(valueOps).set(anyString(), anyString());
        lenient().doAnswer(inv -> {
            redisStore.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(valueOps).set(anyString(), anyString(), any(Duration.class));
        lenient().when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenAnswer(inv -> redisStore.putIfAbsent(inv.getArgument(0), inv.getArgument(1)) == null);
        lenient().when(listOps.leftPush(anyString(), anyString())).thenReturn(1L);

        ShopBagGrantClient bag = mock(ShopBagGrantClient.class);
        when(bag.grant(anyLong(), anyMap())).thenReturn(0);
        ShopActivityRechargeClient recharge = mock(ShopActivityRechargeClient.class);
        when(recharge.addRecharge(anyLong(), anyMap())).thenReturn(Map.of("ok", true));

        ObjectProvider<ShopBagGrantClient> bagProvider = mock(ObjectProvider.class);
        ObjectProvider<ShopActivityRechargeClient> rechargeProvider = mock(ObjectProvider.class);
        ObjectProvider<ShopSkinGrantClient> skinProvider = mock(ObjectProvider.class);
        ObjectProvider<SkinConfigRepository> skinConfigProvider = mock(ObjectProvider.class);
        ObjectProvider<PlayerNotificationPort> notificationProvider = mock(ObjectProvider.class);
        ObjectProvider<ShopMetrics> metricsProvider = mock(ObjectProvider.class);
        ObjectProvider<PassService> passProvider = mock(ObjectProvider.class);
        when(bagProvider.getIfAvailable()).thenReturn(bag);
        when(rechargeProvider.getIfAvailable()).thenReturn(recharge);
        when(skinProvider.getIfAvailable()).thenReturn(null);
        when(skinConfigProvider.getIfAvailable()).thenReturn(null);
        when(notificationProvider.getIfAvailable()).thenReturn(null);
        when(metricsProvider.getIfAvailable()).thenReturn(null);
        when(passProvider.getIfAvailable()).thenReturn(passService);

        shopService = new ShopService(
                shopConfigService, redis, new ObjectMapper(),
                bagProvider, rechargeProvider, skinProvider, skinConfigProvider,
                notificationProvider, mock(ShopOrderPaidEventPublisher.class),
                new ShopPaymentVerifierRegistry(List.of(new MockShopPaymentVerifier())),
                metricsProvider, mock(ObjectProvider.class), mock(ObjectProvider.class),
                mock(ObjectProvider.class), passProvider, true, 1);
    }

    @Test
    public void buyBattlePass_unlocksPaidTierAndAddsXp() throws Exception {
        ShopProductConfig passPack = product(40001, "OTHER_PACK", 6800, List.of("battle_pass"));
        when(shopConfigService.findById(40001)).thenReturn(Optional.of(passPack));

        String orderId = createAndPay(42L, 40001, "PAY-PASS-1");
        assertThat(orderId).isNotBlank();

        Map<String, Object> status = passService.status(42L, 1);
        assertThat(status.get("paidUnlocked")).isEqualTo(true);
        assertThat((Integer) status.get("xp")).isGreaterThan(0);
        assertThat((Integer) status.get("level")).isGreaterThanOrEqualTo(1);
    }

    @Test
    public void buyMonthlyCard_activatesAndAllowsDailyClaim() throws Exception {
        ShopProductConfig monthly = product(40002, "OTHER_PACK", 3000, List.of("monthly_card"));
        when(shopConfigService.findById(40002)).thenReturn(Optional.of(monthly));

        createAndPay(77L, 40002, "PAY-MONTH-1");

        Map<String, Object> status = passService.status(77L, 1);
        assertThat(status.get("monthlyCardActive")).isEqualTo(true);
        Map<String, Object> daily = passService.claimMonthlyDaily(77L, 1);
        assertThat(daily.get("dailyGem")).isEqualTo(90);
        assertThat(passService.claimMonthlyDaily(77L, 1).get("error")).isEqualTo("already_claimed_today");
    }

    @Test
    public void topup_thenBattlePass_journey() throws Exception {
        ShopProductConfig diamond = product(10001, "DIRECT_TOPUP", 600, List.of("diamond"));
        diamond.tags = List.of("diamond");
        ShopProductConfig passPack = product(40001, "OTHER_PACK", 6800, List.of("battle_pass"));
        when(shopConfigService.findById(10001)).thenReturn(Optional.of(diamond));
        when(shopConfigService.findById(40001)).thenReturn(Optional.of(passPack));

        createAndPay(88L, 10001, "PAY-DIA-1");
        int xpAfterTopup = (Integer) passService.status(88L, 1).get("xp");
        assertThat(xpAfterTopup).isGreaterThanOrEqualTo(50);

        createAndPay(88L, 40001, "PAY-PASS-2");
        Map<String, Object> after = passService.status(88L, 1);
        assertThat(after.get("paidUnlocked")).isEqualTo(true);
        int xpAfterPass = (Integer) after.get("xp") + ((Integer) after.get("level") - 1) * 1000;
        assertThat(xpAfterPass).isGreaterThan(xpAfterTopup);
    }

    private String createAndPay(long playerId, int productId, String channelOrderId) throws Exception {
        CreateShopOrderScRsp created = CreateShopOrderScRsp.parseFrom(
                shopService.handleCreateOrder(playerId, CreateShopOrderCsReq.newBuilder()
                        .setProductId(productId)
                        .setChannel("MOCK")
                        .build()).payload());
        assertThat(created.getRetcode()).isEqualTo(0);
        Map<String, Object> pay = shopService.mockPay(created.getOrderId(), channelOrderId);
        assertThat(pay.get("ok")).isEqualTo(true);
        assertThat(pay.get("status")).isEqualTo("DELIVERED");
        return created.getOrderId();
    }

    private static ShopProductConfig product(int id, String type, long price, List<String> tags) {
        ShopProductConfig p = new ShopProductConfig();
        p.productId = id;
        p.productType = type;
        p.name = "p" + id;
        p.channelSku = "sku." + id;
        p.price = price;
        p.originalPrice = price;
        p.tabId = "pass";
        p.opened = true;
        p.limitType = "NONE";
        p.currency = "CNY";
        p.tags = tags;
        ShopRewardConfig r = new ShopRewardConfig();
        r.itemId = 9001;
        r.count = (int) Math.max(1, price / 100);
        p.rewards = List.of(r);
        return p;
    }
}
