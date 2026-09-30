package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.ShopOrderRecord;
import cn.itcast.demo.mymmorpg.repository.ShopOrderRecordRepository;
import cn.itcast.demo.mymmorpg.shop.ChannelBillProvider;
import cn.itcast.demo.mymmorpg.shop.InMemoryChannelBillProvider;
import cn.itcast.demo.mymmorpg.shop.ShopConfigService;
import cn.itcast.demo.mymmorpg.shop.ShopOrderPaidEventPublisher;
import cn.itcast.demo.mymmorpg.shop.ShopOrderStatus;
import cn.itcast.demo.mymmorpg.shop.ShopPaymentVerifierRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 商城对账：扫描 PAID 卡单与缺渠道单号的已履约订单。
 */
public class ShopServiceReconcileTest {

    private ShopService shopService;
    private ShopOrderRecordRepository orderRepo;
    private ObjectProvider<ShopOrderRecordRepository> orderProvider;

    @BeforeMethod
    @SuppressWarnings("unchecked")
    public void setUp() {
        orderRepo = mock(ShopOrderRecordRepository.class);
        orderProvider = mock(ObjectProvider.class);
        when(orderProvider.getIfAvailable()).thenReturn(orderRepo);
        shopService = new ShopService(
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
                orderProvider,
                mock(ObjectProvider.class),
                true);
    }

    @Test
    public void reconcile_reportsPaidStuckAndMissingChannel() {
        ShopOrderRecord paid = order("P1", ShopOrderStatus.PAID.name(), "ch-1");
        ShopOrderRecord deliveredOk = order("D1", ShopOrderStatus.DELIVERED.name(), "ch-2");
        ShopOrderRecord deliveredMissing = order("D2", ShopOrderStatus.DELIVERED.name(), "");
        ShopOrderRecord fulfilledMissing = order("F1", ShopOrderStatus.FULFILLED.name(), "  ");

        when(orderRepo.findByStatusAndPaidAtBetween(eq(ShopOrderStatus.PAID.name()), anyLong(), anyLong()))
                .thenReturn(List.of(paid));
        when(orderRepo.findByStatusAndPaidAtBetween(eq(ShopOrderStatus.DELIVERED.name()), anyLong(), anyLong()))
                .thenReturn(List.of(deliveredOk, deliveredMissing));
        when(orderRepo.findByStatusAndPaidAtBetween(eq(ShopOrderStatus.FULFILLED.name()), anyLong(), anyLong()))
                .thenReturn(List.of(fulfilledMissing));

        Map<String, Object> result = shopService.reconcile(1L, 999L);

        assertThat(result.get("ok")).isEqualTo(true);
        assertThat(result.get("paidStuckCount")).isEqualTo(1);
        @SuppressWarnings("unchecked")
        List<String> paidStuck = (List<String>) result.get("paidStuckOrderIds");
        assertThat(paidStuck).containsExactly("P1");
        assertThat(result.get("deliveredCount")).isEqualTo(3);
        @SuppressWarnings("unchecked")
        List<String> missing = (List<String>) result.get("missingChannelOrderIds");
        assertThat(missing).containsExactly("D2", "F1");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void reconcile_comparesChannelBillDiff() {
        InMemoryChannelBillProvider bills = new InMemoryChannelBillProvider();
        bills.seed(new ChannelBillProvider.ChannelBillLine("ch-only", "M1", 100, "SUCCESS"));
        bills.seed(new ChannelBillProvider.ChannelBillLine("ch-1", "P1", 100, "SUCCESS"));
        ObjectProvider<ChannelBillProvider> billProvider = mock(ObjectProvider.class);
        when(billProvider.orderedStream()).thenReturn(Stream.of(bills));

        ShopOrderRecord paid = order("P1", ShopOrderStatus.PAID.name(), "ch-1");
        when(orderRepo.findByStatusAndPaidAtBetween(eq(ShopOrderStatus.PAID.name()), anyLong(), anyLong()))
                .thenReturn(List.of(paid));
        when(orderRepo.findByStatusAndPaidAtBetween(eq(ShopOrderStatus.DELIVERED.name()), anyLong(), anyLong()))
                .thenReturn(List.of());
        when(orderRepo.findByStatusAndPaidAtBetween(eq(ShopOrderStatus.FULFILLED.name()), anyLong(), anyLong()))
                .thenReturn(List.of());

        shopService = new ShopService(
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
                orderProvider,
                mock(ObjectProvider.class),
                billProvider,
                true);

        Map<String, Object> result = shopService.reconcile(1L, 999L);
        assertThat(result.get("ok")).isEqualTo(true);
        assertThat((Integer) result.get("channelBillLineCount")).isEqualTo(2);
        List<String> channelOnly = (List<String>) result.get("diffChannelOnly");
        assertThat(channelOnly).contains("ch-only");
        List<String> mismatch = (List<String>) result.get("diffPaidStuckVsChannelSuccess");
        assertThat(mismatch).contains("P1");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void reconcile_repoUnavailable() {
        ObjectProvider<ShopOrderRecordRepository> empty = mock(ObjectProvider.class);
        when(empty.getIfAvailable()).thenReturn(null);
        shopService = new ShopService(
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
                empty,
                mock(ObjectProvider.class),
                true);

        Map<String, Object> result = shopService.reconcile(1L, 2L);
        assertThat(result.get("ok")).isEqualTo(false);
    }

    private static ShopOrderRecord order(String id, String status, String channelOrderId) {
        ShopOrderRecord r = new ShopOrderRecord();
        r.setOrderId(id);
        r.setStatus(status);
        r.setChannelOrderId(channelOrderId);
        r.setPlayerId(1L);
        r.setProductId(1);
        r.setPaidAt(100L);
        return r;
    }
}
