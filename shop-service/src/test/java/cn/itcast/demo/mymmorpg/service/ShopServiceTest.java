package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.client.ShopActivityRechargeClient;
import cn.itcast.demo.mymmorpg.client.ShopBagGrantClient;
import cn.itcast.demo.mymmorpg.client.ShopSkinGrantClient;
import cn.itcast.demo.mymmorpg.port.PlayerNotificationPort;
import cn.itcast.demo.mymmorpg.skin.SkinConfigRepository;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.protocol.protobuf.CreateShopOrderCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.CreateShopOrderScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetShopShelfCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetShopShelfScRsp;
import cn.itcast.demo.mymmorpg.shop.MockShopPaymentVerifier;
import cn.itcast.demo.mymmorpg.shop.ShopConfigService;
import cn.itcast.demo.mymmorpg.shop.ShopLimitType;
import cn.itcast.demo.mymmorpg.shop.ShopOrderPaidEventPublisher;
import cn.itcast.demo.mymmorpg.shop.ShopPaymentReceipt;
import cn.itcast.demo.mymmorpg.shop.ShopPaymentVerifierRegistry;
import cn.itcast.demo.mymmorpg.shop.ShopProductConfig;
import cn.itcast.demo.mymmorpg.shop.ShopRewardConfig;
import cn.itcast.demo.mymmorpg.support.ShopMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ListOperations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ShopServiceTest {

    private ShopConfigService shopConfigService;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private ListOperations<String, String> listOps;
    private ShopBagGrantClient bagGrantClient;
    private ShopActivityRechargeClient rechargeClient;
    private ShopOrderPaidEventPublisher paidPublisher;
    private ShopService shopService;
    private MqOutboxService outboxService;
    private final Map<String, String> store = new ConcurrentHashMap<>();

    @BeforeMethod
    public void setUp() {
        store.clear();
        outboxService = null;
        shopConfigService = mock(ShopConfigService.class);
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        listOps = mock(ListOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.opsForList()).thenReturn(listOps);
        when(redis.expire(anyString(), any())).thenReturn(true);
        when(valueOps.get(anyString())).thenAnswer(inv -> store.get(inv.getArgument(0)));
        doAnswer(inv -> {
            store.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(valueOps).set(anyString(), anyString(), any());
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenAnswer(inv -> {
            String k = inv.getArgument(0);
            if (store.containsKey(k)) {
                return false;
            }
            store.put(k, inv.getArgument(1));
            return true;
        });
        when(valueOps.increment(anyString())).thenAnswer(inv -> {
            String k = inv.getArgument(0);
            int n = Integer.parseInt(store.getOrDefault(k, "0")) + 1;
            store.put(k, String.valueOf(n));
            return (long) n;
        });
        when(listOps.leftPush(anyString(), anyString())).thenReturn(1L);
        bagGrantClient = mock(ShopBagGrantClient.class);
        rechargeClient = mock(ShopActivityRechargeClient.class);
        paidPublisher = mock(ShopOrderPaidEventPublisher.class);
        when(bagGrantClient.grant(anyLong(), anyMap())).thenReturn(0);
        when(rechargeClient.addRecharge(anyLong(), anyMap())).thenReturn(Map.of("ok", true));

        @SuppressWarnings("unchecked")
        ObjectProvider<ShopBagGrantClient> bagProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ShopActivityRechargeClient> rechargeProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ShopSkinGrantClient> skinProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<SkinConfigRepository> skinConfigProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<PlayerNotificationPort> notificationProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ShopMetrics> metricsProvider = mock(ObjectProvider.class);
        when(bagProvider.getIfAvailable()).thenReturn(bagGrantClient);
        when(rechargeProvider.getIfAvailable()).thenReturn(rechargeClient);
        when(skinProvider.getIfAvailable()).thenReturn(null);
        when(skinConfigProvider.getIfAvailable()).thenReturn(null);
        when(notificationProvider.getIfAvailable()).thenReturn(null);
        when(metricsProvider.getIfAvailable()).thenReturn(null);

        ShopPaymentVerifierRegistry verifierRegistry =
                new ShopPaymentVerifierRegistry(List.of(new MockShopPaymentVerifier()));
        shopService = new ShopService(shopConfigService, redis, new ObjectMapper(),
                bagProvider, rechargeProvider, skinProvider, skinConfigProvider,
                notificationProvider, paidPublisher, verifierRegistry, metricsProvider,
                mock(ObjectProvider.class), mock(ObjectProvider.class), true);
    }

    @Test
    public void confirmPay_withOutbox_enqueuesInsteadOfDirectPublish() throws Exception {
        outboxService = mock(MqOutboxService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<MqOutboxService> outboxProvider = mock(ObjectProvider.class);
        when(outboxProvider.getIfAvailable()).thenReturn(outboxService);

        @SuppressWarnings("unchecked")
        ObjectProvider<ShopBagGrantClient> bagProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ShopActivityRechargeClient> rechargeProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ShopSkinGrantClient> skinProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<SkinConfigRepository> skinConfigProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<PlayerNotificationPort> notificationProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ShopMetrics> metricsProvider = mock(ObjectProvider.class);
        when(bagProvider.getIfAvailable()).thenReturn(bagGrantClient);
        when(rechargeProvider.getIfAvailable()).thenReturn(rechargeClient);
        when(skinProvider.getIfAvailable()).thenReturn(null);
        when(skinConfigProvider.getIfAvailable()).thenReturn(null);
        when(notificationProvider.getIfAvailable()).thenReturn(null);
        when(metricsProvider.getIfAvailable()).thenReturn(null);

        ShopPaymentVerifierRegistry verifierRegistry =
                new ShopPaymentVerifierRegistry(List.of(new MockShopPaymentVerifier()));
        shopService = new ShopService(shopConfigService, redis, new ObjectMapper(),
                bagProvider, rechargeProvider, skinProvider, skinConfigProvider,
                notificationProvider, paidPublisher, verifierRegistry, metricsProvider,
                mock(ObjectProvider.class), outboxProvider, true);

        ShopProductConfig topup = product(10001, "DIRECT_TOPUP", "topup", 600);
        when(shopConfigService.findById(10001)).thenReturn(Optional.of(topup));
        ProtocolMessage created = shopService.handleCreateOrder(9L, CreateShopOrderCsReq.newBuilder()
                .setProductId(10001)
                .setChannel("MOCK")
                .build());
        String orderId = CreateShopOrderScRsp.parseFrom(created.payload()).getOrderId();

        ShopPaymentReceipt receipt = new ShopPaymentReceipt();
        receipt.orderId = orderId;
        receipt.channel = "MOCK";
        receipt.channelOrderId = "CH-OUTBOX-1";
        receipt.payAmount = 600L;
        Map<String, Object> pay = shopService.confirmPay(receipt);

        assertThat(pay.get("ok")).isEqualTo(true);
        assertThat(pay.get("status")).isEqualTo("DELIVERED");
        verify(outboxService).enqueueShopOrderPaid(eq(9L), eq(orderId), eq(10001), eq(600L), eq("DIRECT_TOPUP"));
        verify(paidPublisher, never()).publishPaid(anyLong(), anyString(), anyInt(), anyLong(), anyString());
        verify(rechargeClient).addRecharge(eq(9L), ArgumentMatchers.argThat(body ->
                ((Number) body.get("amount")).longValue() == 600L));
    }

    @Test
    public void shelf_filtersByTabAndDiscountWindow() throws Exception {
        ShopProductConfig topup = product(10001, "DIRECT_TOPUP", "topup", 600);
        ShopProductConfig discount = product(20001, "DISCOUNT_PACK", "discount", 1200);
        discount.originalPrice = 3000;
        discount.discountBegin = "2026-08-01T00:00:00+08:00";
        discount.discountEnd = "2027-08-03T23:59:59+08:00";
        when(shopConfigService.listOnSale(eq("topup"), anyLong())).thenReturn(List.of(topup));
        when(shopConfigService.version()).thenReturn(1);

        ProtocolMessage msg = shopService.handleGetShelf(1L, GetShopShelfCsReq.newBuilder().setTabId("topup").build());
        assertThat(msg.msgId()).isEqualTo(MessageId.GET_SHOP_SHELF_SC_RSP);
        GetShopShelfScRsp rsp = GetShopShelfScRsp.parseFrom(msg.payload());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(rsp.getProductsCount()).isEqualTo(1);
        assertThat(rsp.getProducts(0).getProductId()).isEqualTo(10001);
    }

    @Test
    public void createAndMockPay_fulfillsAndNotifies() throws Exception {
        ShopProductConfig topup = product(10001, "DIRECT_TOPUP", "topup", 600);
        when(shopConfigService.findById(10001)).thenReturn(Optional.of(topup));
        when(shopConfigService.listOnSale(any(), anyLong())).thenReturn(List.of(topup));

        ProtocolMessage created = shopService.handleCreateOrder(9L, CreateShopOrderCsReq.newBuilder()
                .setProductId(10001)
                .setChannel("MOCK")
                .build());
        CreateShopOrderScRsp createRsp = CreateShopOrderScRsp.parseFrom(created.payload());
        assertThat(createRsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(createRsp.getOrderId()).isNotBlank();

        Map<String, Object> pay = shopService.mockPay(createRsp.getOrderId(), "MOCK-1");
        assertThat(pay.get("ok")).isEqualTo(true);
        assertThat(pay.get("status")).isEqualTo("DELIVERED");
        assertThat(pay.get("firstChargeDouble")).isEqualTo(true);
        verify(bagGrantClient).grant(eq(9L), ArgumentMatchers.argThat(body -> {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rewards = (List<Map<String, Object>>) body.get("rewards");
            return rewards != null && ((Number) rewards.get(0).get("count")).intValue() == 120;
        }));
        verify(rechargeClient).addRecharge(eq(9L), ArgumentMatchers.argThat(body ->
                ((Number) body.get("amount")).longValue() == 600L));
        verify(paidPublisher).publishPaid(eq(9L), anyString(), eq(10001), eq(600L), eq("DIRECT_TOPUP"));
    }

    @Test
    public void periodKey_weeklyUsesChinaWeek() {
        // 2026-08-01 Saturday Asia/Shanghai
        String key = ShopService.periodKey(ShopLimitType.WEEKLY, 1753977600000L);
        assertThat(key).startsWith("W:");
    }

    @Test
    public void doubledRewards_multipliesCounts() {
        ShopRewardConfig r = new ShopRewardConfig();
        r.itemId = 9001;
        r.count = 60;
        List<ShopRewardConfig> doubled = ShopService.doubledRewards(List.of(r));
        assertThat(doubled.get(0).count).isEqualTo(120);
    }

    @Test
    public void confirmPay_withMockChannel_fulfills() throws Exception {
        ShopProductConfig topup = product(10001, "DIRECT_TOPUP", "topup", 600);
        when(shopConfigService.findById(10001)).thenReturn(Optional.of(topup));

        ProtocolMessage created = shopService.handleCreateOrder(9L, CreateShopOrderCsReq.newBuilder()
                .setProductId(10001)
                .setChannel("MOCK")
                .build());
        CreateShopOrderScRsp createRsp = CreateShopOrderScRsp.parseFrom(created.payload());

        ShopPaymentReceipt receipt = new ShopPaymentReceipt();
        receipt.orderId = createRsp.getOrderId();
        receipt.channel = "MOCK";
        receipt.channelOrderId = "CH-CONFIRM-1";
        receipt.payAmount = 600L;
        Map<String, Object> pay = shopService.confirmPay(receipt);
        assertThat(pay.get("ok")).isEqualTo(true);
        assertThat(pay.get("status")).isEqualTo("DELIVERED");
    }

    @Test
    public void refundOrder_marksRefunded() throws Exception {
        ShopProductConfig topup = product(10001, "DIRECT_TOPUP", "topup", 600);
        when(shopConfigService.findById(10001)).thenReturn(Optional.of(topup));
        ProtocolMessage created = shopService.handleCreateOrder(9L, CreateShopOrderCsReq.newBuilder()
                .setProductId(10001)
                .setChannel("MOCK")
                .build());
        String orderId = CreateShopOrderScRsp.parseFrom(created.payload()).getOrderId();
        shopService.mockPay(orderId, "MOCK-REFUND-1");

        Map<String, Object> refund = shopService.refundOrder(orderId, "chargeback", false);
        assertThat(refund.get("ok")).isEqualTo(true);
        assertThat(refund.get("status")).isEqualTo("REFUNDED");
        assertThat(refund.get("rechargeRollback")).isEqualTo(false);

        Map<String, Object> again = shopService.refundOrder(orderId, "chargeback", false);
        assertThat(again.get("idempotent")).isEqualTo(true);
    }

    private static ShopProductConfig product(int id, String type, String tab, long price) {
        ShopProductConfig p = new ShopProductConfig();
        p.productId = id;
        p.productType = type;
        p.name = "p" + id;
        p.channelSku = "sku." + id;
        p.price = price;
        p.originalPrice = price;
        p.tabId = tab;
        p.opened = true;
        p.limitType = "NONE";
        p.currency = "CNY";
        p.tags = List.of("diamond");
        ShopRewardConfig r = new ShopRewardConfig();
        r.itemId = 9001;
        r.count = 60;
        p.rewards = List.of(r);
        return p;
    }
}
