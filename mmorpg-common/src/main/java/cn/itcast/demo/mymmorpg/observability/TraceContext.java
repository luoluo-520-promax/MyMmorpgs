package cn.itcast.demo.mymmorpg.observability;

import cn.itcast.demo.mymmorpg.web.TraceIdFilter;
import org.slf4j.MDC;

/**
 * TraceId MDC 传播助手：与 {@link TraceIdFilter} 共用键名 {@code traceId}，
 * 供异步线程、MQ 消费、Feign 拦截器在跨线程边界 get/set/clear。
 */
public final class TraceContext {

    private TraceContext() {
    }

    public static String getTraceId() {
        return MDC.get(TraceIdFilter.TRACE_ID);
    }

    public static void setTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            clear();
            return;
        }
        MDC.put(TraceIdFilter.TRACE_ID, traceId);
    }

    public static void clear() {
        MDC.remove(TraceIdFilter.TRACE_ID);
    }

    /**
     * 若当前无 traceId 则生成并写入 MDC，返回最终 traceId。
     */
    public static String ensureTraceId() {
        String existing = getTraceId();
        if (existing != null && !existing.isBlank()) {
            return existing;
        }
        String generated = java.util.UUID.randomUUID().toString().replace("-", "");
        setTraceId(generated);
        return generated;
    }
}
