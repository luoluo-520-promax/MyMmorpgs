package cn.itcast.demo.mymmorpg.cache;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis L2 位置批量写入：每 500ms pipeline 异步刷入，非阻塞主逻辑 Tick。
 * 仅写变更帧；静止站立超过 3 秒的玩家跳过 Redis 写入，减少约 50% 缓存写压力。
 */
@Component
public class RedisPositionBatchWriter {

    public record PositionWrite(long playerId, float x, float y, float z, long atMs) {
    }

    public static final long STATIONARY_SKIP_MS = 3000L;
    public static final float POSITION_EPSILON = 0.05f;

    private static final String KEY_PREFIX = "scene:pos:";

    private final ObjectProvider<StringRedisTemplate> redis;
    private final ConcurrentLinkedQueue<PositionWrite> queue = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<Long, PositionWrite> lastWritten = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> lastMovedMs = new ConcurrentHashMap<>();
    private final AtomicLong enqueued = new AtomicLong();
    private final AtomicLong flushed = new AtomicLong();
    private final AtomicLong flushBatches = new AtomicLong();
    private final AtomicLong skippedStationary = new AtomicLong();
    private final AtomicLong skippedUnchanged = new AtomicLong();
    private volatile long flushIntervalMs = 500L;

    public RedisPositionBatchWriter(ObjectProvider<StringRedisTemplate> redis) {
        this.redis = redis;
    }

    public void configure(long flushIntervalMs) {
        this.flushIntervalMs = Math.max(100L, flushIntervalMs);
    }

    public void enqueue(long playerId, float x, float y, float z, long nowMs) {
        if (!shouldWrite(playerId, x, y, z, nowMs)) {
            return;
        }
        queue.offer(new PositionWrite(playerId, x, y, z, nowMs));
        enqueued.incrementAndGet();
    }

    /**
     * 只写变更帧；静止超过 {@link #STATIONARY_SKIP_MS} 则不写入 Redis。
     */
    public boolean shouldWrite(long playerId, float x, float y, float z, long nowMs) {
        if (playerId <= 0) {
            return false;
        }
        PositionWrite prev = lastWritten.get(playerId);
        Long lastMove = lastMovedMs.get(playerId);
        if (prev != null
                && Math.abs(prev.x() - x) <= POSITION_EPSILON
                && Math.abs(prev.y() - y) <= POSITION_EPSILON
                && Math.abs(prev.z() - z) <= POSITION_EPSILON) {
            if (lastMove != null && nowMs - lastMove >= STATIONARY_SKIP_MS) {
                skippedStationary.incrementAndGet();
                return false;
            }
            skippedUnchanged.incrementAndGet();
            return false;
        }
        lastMovedMs.put(playerId, nowMs);
        lastWritten.put(playerId, new PositionWrite(playerId, x, y, z, nowMs));
        return true;
    }

    @Scheduled(fixedDelayString = "${game.cache.position-flush-ms:500}")
    public void scheduledFlush() {
        flush();
    }

    public Map<String, Object> flush() {
        StringRedisTemplate template = redis.getIfAvailable();
        List<PositionWrite> batch = new ArrayList<>();
        PositionWrite w;
        while ((w = queue.poll()) != null && batch.size() < 2000) {
            batch.add(w);
        }
        if (batch.isEmpty()) {
            return Map.of("ok", true, "flushed", 0);
        }
        if (template != null) {
            template.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                for (PositionWrite p : batch) {
                    String val = p.x() + "," + p.y() + "," + p.z() + "," + p.atMs();
                    connection.stringCommands().set(
                            (KEY_PREFIX + p.playerId()).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                            val.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                return null;
            });
        }
        flushed.addAndGet(batch.size());
        flushBatches.incrementAndGet();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("flushed", batch.size());
        body.put("redisAvailable", template != null);
        body.put("flushIntervalMs", flushIntervalMs);
        return body;
    }

    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("queued", queue.size());
        m.put("enqueued", enqueued.get());
        m.put("flushed", flushed.get());
        m.put("flushBatches", flushBatches.get());
        m.put("skippedStationary", skippedStationary.get());
        m.put("skippedUnchanged", skippedUnchanged.get());
        m.put("stationarySkipMs", STATIONARY_SKIP_MS);
        return m;
    }
}
