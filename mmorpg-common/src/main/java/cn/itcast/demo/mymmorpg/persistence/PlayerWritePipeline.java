package cn.itcast.demo.mymmorpg.persistence;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 玩家数据流水线写入：本地积攒变更，达到阈值后 JDBC Batch 一次性刷入 MySQL，减少连接池穿刺。
 * 集成 DeduplicateFilter：同一字段 100ms 内重复变化只保留最后一次。
 */
@Component
public class PlayerWritePipeline {

    public record WriteOp(long playerId, String opType, Map<String, Object> payload, long queuedAtMs) {
        public WriteOp {
            opType = opType == null ? "UNKNOWN" : opType.trim().toUpperCase();
            payload = payload == null ? Map.of() : Map.copyOf(payload);
        }
    }

    private final ConcurrentHashMap<Long, List<WriteOp>> pending = new ConcurrentHashMap<>();
    private final AtomicLong enqueued = new AtomicLong();
    private final AtomicLong flushed = new AtomicLong();
    private final AtomicLong flushBatches = new AtomicLong();
    private final AtomicLong dedupeSkipped = new AtomicLong();
    private final DeduplicateFilter dedupeFilter;
    private volatile int batchThreshold = 50;
    private volatile long flushIntervalMs = 1000L;
    private volatile long lastFlushMs = System.currentTimeMillis();

    public PlayerWritePipeline() {
        this.dedupeFilter = new DeduplicateFilter();
    }

    public PlayerWritePipeline(ObjectProvider<DeduplicateFilter> dedupeFilter) {
        this.dedupeFilter = dedupeFilter.getIfAvailable(DeduplicateFilter::new);
    }

    public void configure(int batchThreshold, long flushIntervalMs) {
        this.batchThreshold = Math.max(10, batchThreshold);
        this.flushIntervalMs = Math.max(200L, flushIntervalMs);
    }

    public void enqueue(long playerId, String opType, Map<String, Object> payload) {
        long now = System.currentTimeMillis();
        String field = opType + ":" + payload.hashCode();
        Object sample = payload.isEmpty() ? opType : payload.values().iterator().next();
        if (!dedupeFilter.shouldWrite(playerId, field, sample, now)) {
            dedupeSkipped.incrementAndGet();
            return;
        }
        WriteOp op = new WriteOp(playerId, opType, payload, now);
        pending.computeIfAbsent(playerId, id -> new ArrayList<>()).add(op);
        enqueued.incrementAndGet();
    }

    public boolean shouldFlush(long nowMs) {
        int total = pending.values().stream().mapToInt(List::size).sum();
        return total >= batchThreshold || (total > 0 && nowMs - lastFlushMs >= flushIntervalMs);
    }

    /**
     * 批量 drain 并交给 handler 执行 JDBC batch；主链路不持有 @Transactional 连接。
     */
    public Map<String, Object> flush(Consumer<List<WriteOp>> batchHandler) {
        List<WriteOp> batch = new ArrayList<>();
        for (var entry : pending.entrySet()) {
            List<WriteOp> ops = entry.getValue();
            if (!ops.isEmpty()) {
                batch.addAll(ops);
                ops.clear();
            }
        }
        pending.entrySet().removeIf(e -> e.getValue().isEmpty());
        if (batch.isEmpty()) {
            return Map.of("ok", true, "flushed", 0);
        }
        if (batchHandler != null) {
            batchHandler.accept(batch);
        }
        flushed.addAndGet(batch.size());
        flushBatches.incrementAndGet();
        lastFlushMs = System.currentTimeMillis();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("flushed", batch.size());
        body.put("players", batch.stream().map(WriteOp::playerId).distinct().count());
        body.put("dedupeSkipped", dedupeSkipped.get());
        return body;
    }

    public int pendingCount() {
        return pending.values().stream().mapToInt(List::size).sum();
    }

    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pending", pendingCount());
        m.put("enqueued", enqueued.get());
        m.put("flushed", flushed.get());
        m.put("flushBatches", flushBatches.get());
        m.put("dedupeSkipped", dedupeSkipped.get());
        m.put("batchThreshold", batchThreshold);
        m.put("flushIntervalMs", flushIntervalMs);
        m.put("dedupeFilter", dedupeFilter.stats());
        return m;
    }
}
