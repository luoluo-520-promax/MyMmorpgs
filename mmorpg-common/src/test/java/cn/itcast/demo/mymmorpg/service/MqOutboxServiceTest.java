package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.MqOutboxEvent;
import cn.itcast.demo.mymmorpg.repository.MqOutboxEventRepository;
import cn.itcast.demo.mymmorpg.shop.ShopOrderPaidEventPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Pageable;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MqOutbox：入队 NEW → 投递 SENT；失败 FAILED + 指数退避。
 */
public class MqOutboxServiceTest {

    private MqOutboxEventRepository outboxRepository;
    private ShopOrderPaidEventPublisher publisher;
    private MqOutboxService service;
    private final AtomicReference<MqOutboxEvent> lastSaved = new AtomicReference<>();

    @BeforeMethod
    @SuppressWarnings("unchecked")
    public void setUp() {
        lastSaved.set(null);
        outboxRepository = mock(MqOutboxEventRepository.class);
        publisher = mock(ShopOrderPaidEventPublisher.class);
        ObjectProvider<ShopOrderPaidEventPublisher> publisherProvider = mock(ObjectProvider.class);
        ObjectProvider<MeterRegistry> meterProvider = mock(ObjectProvider.class);
        ObjectProvider<?> rewardProvider = mock(ObjectProvider.class);
        ObjectProvider<?> stockProvider = mock(ObjectProvider.class);
        when(publisherProvider.getIfAvailable()).thenReturn(publisher);
        when(meterProvider.getIfAvailable()).thenReturn(null);
        when(rewardProvider.getIfAvailable()).thenReturn(null);
        when(stockProvider.getIfAvailable()).thenReturn(null);
        when(outboxRepository.save(any(MqOutboxEvent.class))).thenAnswer(inv -> {
            MqOutboxEvent e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(1L);
            }
            lastSaved.set(e);
            return e;
        });
        @SuppressWarnings({"unchecked", "rawtypes"})
        MqOutboxService created = new MqOutboxService(
                outboxRepository, publisherProvider, meterProvider,
                (ObjectProvider) rewardProvider, (ObjectProvider) stockProvider,
                "SHOP_EVENTS", "ACTIVITY_EVENTS");
        service = created;
    }

    @Test
    public void enqueueShopOrderPaid_persistsNew() {
        service.enqueueShopOrderPaid(9L, "O-1", 10001, 600L, "DIRECT_TOPUP");

        MqOutboxEvent row = lastSaved.get();
        assertThat(row).isNotNull();
        assertThat(row.getStatus()).isEqualTo(MqOutboxEvent.STATUS_NEW);
        assertThat(row.getRetryCount()).isEqualTo(0);
        assertThat(row.getTopic()).isEqualTo("SHOP_EVENTS");
        assertThat(row.getTag()).isEqualTo("paid");
        assertThat(row.getPayload()).contains("playerId=9").contains("orderId=O-1").contains("amount=600");
    }

    @Test
    public void pollAndDispatch_success_marksSent() {
        MqOutboxEvent row = readyEvent();
        when(outboxRepository.findReady(eq(MqOutboxEvent.STATUS_NEW), any(Long.class), any(Pageable.class)))
                .thenReturn(List.of(row));

        service.pollAndDispatch();

        verify(publisher).publishPaid(9L, "O-1", 10001, 600L, "DIRECT_TOPUP");
        assertThat(row.getStatus()).isEqualTo(MqOutboxEvent.STATUS_SENT);
    }

    @Test
    public void pollAndDispatch_publishFails_marksFailedWithBackoff() {
        MqOutboxEvent row = readyEvent();
        when(outboxRepository.findReady(eq(MqOutboxEvent.STATUS_NEW), any(Long.class), any(Pageable.class)))
                .thenReturn(List.of(row));
        doThrow(new RuntimeException("mq down")).when(publisher)
                .publishPaid(any(Long.class), any(), any(Integer.class), any(Long.class), any());

        long before = System.currentTimeMillis();
        service.pollAndDispatch();

        assertThat(row.getStatus()).isEqualTo(MqOutboxEvent.STATUS_FAILED);
        assertThat(row.getRetryCount()).isEqualTo(1);
        assertThat(row.getNextRetryAt()).isGreaterThan(before);
    }

    @Test
    public void enqueueActivityRewardAndStockDepleted() {
        service.enqueueActivityRewardCommand(1L, 2L, "idem-1", "10:1");
        MqOutboxEvent reward = lastSaved.get();
        assertThat(reward.getTopic()).isEqualTo("ACTIVITY_EVENTS");
        assertThat(reward.getTag()).isEqualTo("reward_grant");
        assertThat(reward.getPayload()).contains("idempotencyKey=idem-1").contains("items=10:1");

        service.enqueueShopStockDepleted(99, 123L);
        MqOutboxEvent stock = lastSaved.get();
        assertThat(stock.getTopic()).isEqualTo("SHOP_EVENTS");
        assertThat(stock.getTag()).isEqualTo("stock_depleted");
        assertThat(stock.getPayload()).contains("productId=99");
    }

    private static MqOutboxEvent readyEvent() {
        MqOutboxEvent row = new MqOutboxEvent();
        row.setId(10L);
        row.setTopic("SHOP_EVENTS");
        row.setTag("paid");
        row.setPayload("shopOrderPaid|playerId=9|orderId=O-1|productId=10001|amount=600|productType=DIRECT_TOPUP");
        row.setStatus(MqOutboxEvent.STATUS_NEW);
        row.setRetryCount(0);
        row.setNextRetryAt(0L);
        row.setCreatedAt(1L);
        row.setUpdatedAt(1L);
        return row;
    }
}
