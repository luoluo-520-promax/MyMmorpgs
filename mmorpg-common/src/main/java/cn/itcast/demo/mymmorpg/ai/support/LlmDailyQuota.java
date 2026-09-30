package cn.itcast.demo.mymmorpg.ai.support;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 每玩家 LLM 日调用配额（默认 50）。
 */
public final class LlmDailyQuota {

    private final int dailyLimit;
    private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();
    private final ZoneId zone;

    public LlmDailyQuota() {
        this(50, ZoneId.of("Asia/Shanghai"));
    }

    public LlmDailyQuota(int dailyLimit, ZoneId zone) {
        this.dailyLimit = Math.max(1, dailyLimit);
        this.zone = zone == null ? ZoneId.systemDefault() : zone;
    }

    public boolean tryAcquire(long playerId) {
        String key = key(playerId);
        AtomicInteger c = counters.computeIfAbsent(key, k -> new AtomicInteger());
        int next = c.incrementAndGet();
        if (next > dailyLimit) {
            c.decrementAndGet();
            return false;
        }
        return true;
    }

    public int remaining(long playerId) {
        AtomicInteger c = counters.get(key(playerId));
        int used = c == null ? 0 : c.get();
        return Math.max(0, dailyLimit - used);
    }

    public Map<String, Object> snapshot(long playerId) {
        return Map.of(
                "playerId", playerId,
                "dailyLimit", dailyLimit,
                "remaining", remaining(playerId),
                "day", LocalDate.now(zone).toString());
    }

    private String key(long playerId) {
        return playerId + ":" + LocalDate.now(zone);
    }
}
