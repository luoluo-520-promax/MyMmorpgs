package cn.itcast.demo.mymmorpg.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 对账后自动补发：paidStuck / statusMismatch 走 fulfillByOps；
 * channelOnly（渠道有单、游戏无单）进入人工队列。
 */
@Component
public class AutoReconcileHandler {

    private static final Logger log = LoggerFactory.getLogger(AutoReconcileHandler.class);

    private final ShopService shopService;
    private final List<Map<String, Object>> auditLog = new CopyOnWriteArrayList<>();
    private final List<Map<String, Object>> manualQueue = new CopyOnWriteArrayList<>();

    public AutoReconcileHandler(ShopService shopService) {
        this.shopService = shopService;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> handleAfterReconcile(Map<String, Object> reconcileResult) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (reconcileResult == null || !Boolean.TRUE.equals(reconcileResult.get("ok"))) {
            out.put("ok", false);
            out.put("error", "reconcile_failed");
            return out;
        }
        List<String> autoFixed = new ArrayList<>();
        List<String> autoFailed = new ArrayList<>();
        List<String> manual = new ArrayList<>();

        List<String> paidStuck = asStringList(reconcileResult.get("paidStuckOrderIds"));
        List<String> statusMismatch = asStringList(reconcileResult.get("diffPaidStuckVsChannelSuccess"));
        List<String> retryCandidates = asStringList(reconcileResult.get("retryFulfillCandidates"));
        List<String> channelOnly = asStringList(reconcileResult.get("diffChannelOnly"));

        java.util.LinkedHashSet<String> toFulfill = new java.util.LinkedHashSet<>();
        toFulfill.addAll(paidStuck);
        toFulfill.addAll(statusMismatch);
        toFulfill.addAll(retryCandidates);

        for (String orderId : toFulfill) {
            try {
                Map<String, Object> result = shopService.fulfillByOps(orderId);
                Map<String, Object> audit = new LinkedHashMap<>();
                audit.put("atMs", System.currentTimeMillis());
                audit.put("orderId", orderId);
                audit.put("action", "auto_fulfill");
                audit.put("result", result);
                auditLog.add(audit);
                if (Boolean.TRUE.equals(result.get("ok"))) {
                    autoFixed.add(orderId);
                } else {
                    autoFailed.add(orderId);
                    enqueueManual("fulfill_failed", orderId, result);
                    manual.add(orderId);
                }
            } catch (Exception e) {
                log.warn("auto fulfill failed orderId={}", orderId, e);
                autoFailed.add(orderId);
                enqueueManual("fulfill_exception", orderId, Map.of("error", e.getMessage()));
                manual.add(orderId);
            }
        }

        for (String channelOrderId : channelOnly) {
            enqueueManual("channel_only", channelOrderId, Map.of("hint", "channel paid, no local order"));
            manual.add(channelOrderId);
        }

        out.put("ok", true);
        out.put("autoFixed", autoFixed);
        out.put("autoFailed", autoFailed);
        out.put("manualQueue", manual);
        out.put("manualQueueSize", manualQueue.size());
        return out;
    }

    public List<Map<String, Object>> auditLogSnapshot() {
        return List.copyOf(auditLog);
    }

    public List<Map<String, Object>> manualQueueSnapshot() {
        return List.copyOf(manualQueue);
    }

    void clearForTests() {
        auditLog.clear();
        manualQueue.clear();
    }

    private void enqueueManual(String reason, String id, Map<String, Object> detail) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("atMs", System.currentTimeMillis());
        row.put("reason", reason);
        row.put("id", id);
        row.put("detail", detail);
        manualQueue.add(row);
        log.warn("RECONCILE_MANUAL_QUEUE reason={} id={}", reason, id);
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object value) {
        if (value instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) {
                if (o != null) {
                    out.add(String.valueOf(o));
                }
            }
            return out;
        }
        return List.of();
    }
}
