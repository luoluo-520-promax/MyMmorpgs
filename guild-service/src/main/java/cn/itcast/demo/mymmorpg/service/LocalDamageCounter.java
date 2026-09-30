package cn.itcast.demo.mymmorpg.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 本地伤害聚合：Scene/公会节点内存累加，每 1 秒合并 ZINCRBY 提交 Redis，避免热 Key 击穿。
 */
@Component
public class LocalDamageCounter {

    public static final long FLUSH_INTERVAL_MS = 1000L;

    private final ObjectProvider<StringRedisTemplate> redisTemplate;
    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, AtomicLong>> pending = new ConcurrentHashMap<>();
    private final AtomicLong localIncrements = new AtomicLong();
    private final AtomicLong flushBatches = new AtomicLong();
    private final AtomicLong redisIncrements = new AtomicLong();

    public LocalDamageCounter(ObjectProvider<StringRedisTemplate> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 本地累加，不立即写 Redis。
     */
    public long accumulate(String zsetKey, long playerId, long delta) {
        if (zsetKey == null || zsetKey.isBlank() || playerId <= 0 || delta <= 0) {
            return 0L;
        }
        localIncrements.incrementAndGet();
        return pending.computeIfAbsent(zsetKey, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(playerId, id -> new AtomicLong())
                .addAndGet(delta);
    }

    /**
     * 读取本地累计值（含未刷盘部分）。
     */
    public long localTotal(String zsetKey, long playerId) {
        ConcurrentHashMap<Long, AtomicLong> map = pending.get(zsetKey);
        if (map == null) {
            return 0L;
        }
        AtomicLong v = map.get(playerId);
        return v == null ? 0L : v.get();
    }

    @Scheduled(fixedDelayString = "${game.guild.damage-flush-ms:1000}")
    public void scheduledFlush() {
        flushAll();
    }

    public Map<String, Object> flushAll() {
        StringRedisTemplate redis = redisTemplate.getIfAvailable();
        int flushed = 0;
        List<String> keys = new ArrayList<>(pending.keySet());
        for (String zsetKey : keys) {
            ConcurrentHashMap<Long, AtomicLong> map = pending.remove(zsetKey);
            if (map == null || map.isEmpty()) {
                continue;
            }
            for (Map.Entry<Long, AtomicLong> e : map.entrySet()) {
                long delta = e.getValue().getAndSet(0L);
                if (delta <= 0) {
                    continue;
                }
                if (redis != null) {
                    redis.opsForZSet().incrementScore(zsetKey, String.valueOf(e.getKey()), delta);
                    redisIncrements.incrementAndGet();
                }
                flushed++;
            }
        }
        if (flushed > 0) {
            flushBatches.incrementAndGet();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("flushedEntries", flushed);
        body.put("redisAvailable", redis != null);
        return body;
    }

    public void clear(String zsetKey) {
        pending.remove(zsetKey);
    }

    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pendingKeys", pending.size());
        m.put("localIncrements", localIncrements.get());
        m.put("flushBatches", flushBatches.get());
        m.put("redisIncrements", redisIncrements.get());
        return m;
    }
}
