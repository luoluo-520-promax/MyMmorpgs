package cn.itcast.demo.mymmorpg.persistence;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 写去重：同一 playerId + field 在 dedupeWindowMs 内多次变化，只保留最后一次值。
 */
@Component
public class DeduplicateFilter {

    public record DedupeKey(long playerId, String field) {
        public DedupeKey {
            field = field == null ? "" : field.trim().toLowerCase();
        }
    }

    public record DedupeEntry(Object value, long lastWriteMs) {
    }

    private final ConcurrentHashMap<DedupeKey, DedupeEntry> lastWrites = new ConcurrentHashMap<>();
    private final AtomicLong suppressed = new AtomicLong();
    private final AtomicLong passed = new AtomicLong();
    private volatile long dedupeWindowMs = 100L;

    public void configure(long dedupeWindowMs) {
        this.dedupeWindowMs = Math.max(10L, dedupeWindowMs);
    }

    /**
     * @return true 表示应写入（非重复或窗口已过）；false 表示可跳过
     */
    public boolean shouldWrite(long playerId, String field, Object newValue, long nowMs) {
        DedupeKey key = new DedupeKey(playerId, field);
        DedupeEntry prev = lastWrites.get(key);
        if (prev != null
                && nowMs - prev.lastWriteMs() < dedupeWindowMs
                && valuesEqual(prev.value(), newValue)) {
            suppressed.incrementAndGet();
            return false;
        }
        lastWrites.put(key, new DedupeEntry(newValue, nowMs));
        passed.incrementAndGet();
        return true;
    }

    public Map<String, Object> stats() {
        return Map.of(
                "tracked", lastWrites.size(),
                "suppressed", suppressed.get(),
                "passed", passed.get(),
                "dedupeWindowMs", dedupeWindowMs);
    }

    private static boolean valuesEqual(Object a, Object b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.equals(b);
    }
}
