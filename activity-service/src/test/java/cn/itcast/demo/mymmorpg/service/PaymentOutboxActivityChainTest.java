package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.MqOutboxEvent;
import cn.itcast.demo.mymmorpg.repository.MqOutboxEventRepository;
import cn.itcast.demo.mymmorpg.shop.ShopOrderPaidEventPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Pageable;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 支付 Outbox 投递 → 活动累充消费串联（单测级贯通）。
 */
public class PaymentOutboxActivityChainTest {

    @Test
    @SuppressWarnings("unchecked")
    public void outboxDispatch_forwardsToActivityRechargeConsumer() {
        List<MqOutboxEvent> store = new ArrayList<>();
        MqOutboxEventRepository outboxRepo = mock(MqOutboxEventRepository.class);
        when(outboxRepo.save(any(MqOutboxEvent.class))).thenAnswer(inv -> {
            MqOutboxEvent e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId((long) (store.size() + 1));
                store.add(e);
            }
            return e;
        });
        when(outboxRepo.findReady(eq(MqOutboxEvent.STATUS_NEW), anyLong(), any(Pageable.class)))
                .thenAnswer(inv -> store.stream()
                        .filter(e -> MqOutboxEvent.STATUS_NEW.equals(e.getStatus()))
                        .toList());

        ActivityShopRechargeService rechargeService = mock(ActivityShopRechargeService.class);
        ShopOrderPaidMqConsumer consumer = new ShopOrderPaidMqConsumer(
                rechargeService, "127.0.0.1:9876", "SHOP_EVENTS", "activity-shop-consumer");

        ShopOrderPaidEventPublisher publisher = (playerId, orderId, productId, amount, productType) ->
                consumer.handle("playerId=" + playerId + "|orderId=" + orderId
                        + "|productId=" + productId + "|amount=" + amount
                        + "|productType=" + productType);

        ObjectProvider<ShopOrderPaidEventPublisher> publisherProvider = mock(ObjectProvider.class);
        ObjectProvider<MeterRegistry> meterProvider = mock(ObjectProvider.class);
        ObjectProvider<?> rewardPublisherProvider = mock(ObjectProvider.class);
        ObjectProvider<?> stockPublisherProvider = mock(ObjectProvider.class);
        when(publisherProvider.getIfAvailable()).thenReturn(publisher);
        when(meterProvider.getIfAvailable()).thenReturn(null);
        when(rewardPublisherProvider.getIfAvailable()).thenReturn(null);
        when(stockPublisherProvider.getIfAvailable()).thenReturn(null);

        @SuppressWarnings({"unchecked", "rawtypes"})
        MqOutboxService outbox = new MqOutboxService(
                outboxRepo, publisherProvider, meterProvider,
                (ObjectProvider) rewardPublisherProvider, (ObjectProvider) stockPublisherProvider,
                "SHOP_EVENTS", "ACTIVITY_EVENTS");
        outbox.enqueueShopOrderPaid(9L, "ORDER-CHAIN-1", 10001, 600L, "DIRECT_TOPUP");
        outbox.pollAndDispatch();

        verify(rechargeService).applyRecharge(9L, 600L, "ORDER-CHAIN-1");
        verify(rechargeService, org.mockito.Mockito.never()).applyRecharge(anyLong(), anyLong(), eq(""));
        assert store.get(0).getStatus().equals(MqOutboxEvent.STATUS_SENT);
    }
}
