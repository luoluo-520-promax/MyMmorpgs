package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.shop.ShopConfigService;
import cn.itcast.demo.mymmorpg.shop.ShopOrder;
import cn.itcast.demo.mymmorpg.shop.ShopOrderPaidEventPublisher;
import cn.itcast.demo.mymmorpg.shop.ShopPaymentVerifierRegistry;
import cn.itcast.demo.mymmorpg.shop.ShopProductConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 支付履约后战令进度联动。
 */
public class ShopPassProgressTest {

    @Test
    @SuppressWarnings("unchecked")
    public void applyPassProgress_unlocksPassAndMonthlyByTags() {
        PassService pass = mock(PassService.class);
        ShopConfigService configs = mock(ShopConfigService.class);
        ObjectProvider<PassService> passProvider = mock(ObjectProvider.class);
        when(passProvider.getIfAvailable()).thenReturn(pass);

        ShopProductConfig cfg = new ShopProductConfig();
        cfg.productId = 40001;
        cfg.tags = List.of("battle_pass", "monthly_card");
        when(configs.findById(40001)).thenReturn(Optional.of(cfg));

        ShopService shop = new ShopService(
                configs,
                mock(StringRedisTemplate.class),
                new ObjectMapper(),
                mock(ObjectProvider.class),
                mock(ObjectProvider.class),
                mock(ObjectProvider.class),
                mock(ObjectProvider.class),
                mock(ObjectProvider.class),
                mock(ShopOrderPaidEventPublisher.class),
                new ShopPaymentVerifierRegistry(List.of()),
                mock(ObjectProvider.class),
                mock(ObjectProvider.class),
                mock(ObjectProvider.class),
                mock(ObjectProvider.class),
                passProvider,
                true,
                1);

        ShopOrder order = new ShopOrder();
        order.playerId = 9L;
        order.orderId = "S-PASS-1";
        order.productId = 40001;
        order.productType = "OTHER_PACK";
        order.payAmount = 6800L;

        shop.applyPassProgress(order, false);

        verify(pass).addDailyXp(eq(9L), eq(1), anyInt());
        verify(pass).unlockPaid(9L, 1);
        verify(pass).activateMonthlyCard(9L, 1, 30);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void applyPassProgress_noopWithoutPassBean() {
        ShopService shop = new ShopService(
                mock(ShopConfigService.class),
                mock(StringRedisTemplate.class),
                new ObjectMapper(),
                mock(ObjectProvider.class),
                mock(ObjectProvider.class),
                mock(ObjectProvider.class),
                mock(ObjectProvider.class),
                mock(ObjectProvider.class),
                mock(ShopOrderPaidEventPublisher.class),
                new ShopPaymentVerifierRegistry(List.of()),
                mock(ObjectProvider.class),
                mock(ObjectProvider.class),
                mock(ObjectProvider.class),
                true);

        ShopOrder order = new ShopOrder();
        order.playerId = 1L;
        order.orderId = "S-1";
        order.productId = 1;
        order.payAmount = 100L;
        shop.applyPassProgress(order, false);
        assertThat(Map.of("ok", true)).containsEntry("ok", true);
    }
}
