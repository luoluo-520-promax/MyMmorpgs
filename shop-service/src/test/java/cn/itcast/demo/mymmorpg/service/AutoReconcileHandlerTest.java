package cn.itcast.demo.mymmorpg.service;

import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 对账后自动补发 / 人工队列闭环。
 */
public class AutoReconcileHandlerTest {

    @Test
    public void autoFulfill_paidStuckAndStatusMismatch_channelOnlyGoesManual() {
        ShopService shop = mock(ShopService.class);
        when(shop.fulfillByOps("O-STUCK")).thenReturn(Map.of("ok", true, "status", "DELIVERED"));
        when(shop.fulfillByOps("O-MISMATCH")).thenReturn(Map.of("ok", true));
        when(shop.fulfillByOps("O-FAIL")).thenReturn(Map.of("ok", false, "retcode", 156));

        AutoReconcileHandler handler = new AutoReconcileHandler(shop);
        handler.clearForTests();

        Map<String, Object> reconcile = Map.of(
                "ok", true,
                "paidStuckOrderIds", List.of("O-STUCK", "O-FAIL"),
                "diffPaidStuckVsChannelSuccess", List.of("O-MISMATCH"),
                "retryFulfillCandidates", List.of(),
                "diffChannelOnly", List.of("CH-ONLY-1"));

        Map<String, Object> out = handler.handleAfterReconcile(reconcile);
        assertThat(out.get("ok")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<String> fixed = (List<String>) out.get("autoFixed");
        assertThat(fixed).containsExactlyInAnyOrder("O-STUCK", "O-MISMATCH");
        @SuppressWarnings("unchecked")
        List<String> failed = (List<String>) out.get("autoFailed");
        assertThat(failed).contains("O-FAIL");
        @SuppressWarnings("unchecked")
        List<String> manual = (List<String>) out.get("manualQueue");
        assertThat(manual).contains("O-FAIL", "CH-ONLY-1");
        assertThat(handler.auditLogSnapshot()).isNotEmpty();
        assertThat(handler.manualQueueSnapshot()).isNotEmpty();
        verify(shop, times(3)).fulfillByOps(anyString());
    }

    @Test
    public void reconcileFailed_shortCircuits() {
        AutoReconcileHandler handler = new AutoReconcileHandler(mock(ShopService.class));
        Map<String, Object> out = handler.handleAfterReconcile(Map.of("ok", false));
        assertThat(out.get("ok")).isEqualTo(false);
        assertThat(out.get("error")).isEqualTo("reconcile_failed");
    }
}
