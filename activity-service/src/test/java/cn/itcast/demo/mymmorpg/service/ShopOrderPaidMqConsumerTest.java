package cn.itcast.demo.mymmorpg.service;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 商城支付 MQ → 活动累充的消息体解析与转发。
 */
public class ShopOrderPaidMqConsumerTest {

    private ActivityShopRechargeService rechargeService;
    private ShopOrderPaidMqConsumer consumer;

    @BeforeMethod
    public void setUp() {
        rechargeService = mock(ActivityShopRechargeService.class);
        consumer = new ShopOrderPaidMqConsumer(rechargeService, "127.0.0.1:9876", "SHOP_EVENTS", "activity-shop-consumer");
    }

    @Test
    public void handle_validBody_forwardsApplyRecharge() {
        consumer.handle("playerId=9|amount=100|orderId=O1");
        verify(rechargeService).applyRecharge(9L, 100L, "O1");
    }

    @Test
    public void handle_missingFields_skips() {
        consumer.handle("playerId=9|orderId=O1");
        verify(rechargeService, never()).applyRecharge(anyLong(), anyLong(), anyString());
    }

    @Test
    public void handle_blankBody_skips() {
        consumer.handle("   ");
        verify(rechargeService, never()).applyRecharge(anyLong(), anyLong(), anyString());
    }

    @Test
    public void handle_partialOrderId_defaultsEmpty() {
        consumer.handle("playerId=9|amount=50");
        verify(rechargeService).applyRecharge(eq(9L), eq(50L), eq(""));
    }
}
