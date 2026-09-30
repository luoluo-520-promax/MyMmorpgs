package cn.itcast.demo.mymmorpg.world.loot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 采集物权热数据：仅写 Redis Bitmap（或本地 BitSet），定时/关服批量 Append 落库，减轻 MySQL 压力。
 * <p>
 * 客户端「已采集」状态以 Redis 为准，不查库。
 */
@Component
public class LootOwnershipBitmapStore {

    private static final Logger log = LoggerFactory.getLogger(LootOwnershipBitmapStore.class);
    private static final String KEY_PREFIX = "loot:owned:bitmap:";

    public record FlushEvent(String scopeKey, int bitIndex, boolean claimed, long playerId, long atMs) {
    }

    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final ConcurrentHashMap<String, BitSet> local = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<FlushEvent> dirtyQueue = new ConcurrentLinkedQueue<>();
    private final AtomicLong claimHits = new AtomicLong();
    private final AtomicLong flushed = new AtomicLong();
    private volatile long lastFlushMs = 0L;

    public LootOwnershipBitmapStore(ObjectProvider<StringRedisTemplate> redisProvider) {
        this.redisProvider = redisProvider;
    }

    public LootOwnershipBitmapStore() {
        this.redisProvider = null;
    }

    public static String scopeKey(int worldId, int sceneId, String resourceGroup) {
        String g = resourceGroup == null || resourceGroup.isBlank() ? "default" : resourceGroup.trim();
        return worldId + ":" + sceneId + ":" + g;
    }

    public boolean isClaimed(String scopeKey, int bitIndex) {
        if (bitIndex < 0) {
            return false;
        }
        StringRedisTemplate redis = redis();
        if (redis != null) {
            try {
                Boolean bit = redis.opsForValue().getBit(KEY_PREFIX + scopeKey, bitIndex);
                return Boolean.TRUE.equals(bit);
            } catch (Exception e) {
                log.warn("loot bitmap get failed: {}", e.getMessage());
            }
        }
        BitSet set = local.get(scopeKey);
        return set != null && set.get(bitIndex);
    }

    /**
     * 原子声明采集：已占用则失败；成功则入脏队列等待 Append 落库。
     */
    public Map<String, Object> tryClaim(String scopeKey, int bitIndex, long playerId, long nowMs) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (bitIndex < 0) {
            m.put("ok", false);
            m.put("error", "invalid_bit");
            return m;
        }
        if (isClaimed(scopeKey, bitIndex)) {
            m.put("ok", false);
            m.put("error", "already_claimed");
            m.put("scopeKey", scopeKey);
            m.put("bitIndex", bitIndex);
            return m;
        }
        boolean written = setBit(scopeKey, bitIndex, true);
        if (!written) {
            m.put("ok", false);
            m.put("error", "write_failed");
            return m;
        }
        dirtyQueue.offer(new FlushEvent(scopeKey, bitIndex, true, playerId, nowMs));
        claimHits.incrementAndGet();
        m.put("ok", true);
        m.put("scopeKey", scopeKey);
        m.put("bitIndex", bitIndex);
        m.put("playerId", playerId);
        m.put("source", "redis_bitmap");
        return m;
    }

    /**
     * 批量取出脏事件供落库（Append-only）；调用方负责写 MySQL。
     */
    public List<FlushEvent> drainDirty(int maxBatch) {
        int n = Math.max(1, maxBatch);
        List<FlushEvent> out = new ArrayList<>(Math.min(n, 64));
        FlushEvent e;
        while (out.size() < n && (e = dirtyQueue.poll()) != null) {
            out.add(e);
        }
        flushed.addAndGet(out.size());
        lastFlushMs = System.currentTimeMillis();
        return out;
    }

    public int dirtySize() {
        return dirtyQueue.size();
    }

    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("claimHits", claimHits.get());
        m.put("flushed", flushed.get());
        m.put("dirtySize", dirtyQueue.size());
        m.put("lastFlushMs", lastFlushMs);
        m.put("localScopes", local.size());
        return m;
    }

    private boolean setBit(String scopeKey, int bitIndex, boolean value) {
        StringRedisTemplate redis = redis();
        if (redis != null) {
            try {
                redis.opsForValue().setBit(KEY_PREFIX + scopeKey, bitIndex, value);
                // 热数据 TTL：防止永久膨胀；落库后可由运维续期
                redis.expire(KEY_PREFIX + scopeKey, Duration.ofDays(7));
                return true;
            } catch (Exception e) {
                log.warn("loot bitmap set failed: {}", e.getMessage());
            }
        }
        local.compute(scopeKey, (k, set) -> {
            BitSet bits = set == null ? new BitSet() : set;
            bits.set(bitIndex, value);
            return bits;
        });
        return true;
    }

    private StringRedisTemplate redis() {
        return redisProvider == null ? null : redisProvider.getIfAvailable();
    }
}
