package cn.itcast.demo.mymmorpg.world.state;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.BitSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全图机关 / 解谜位图：Redis SETBIT 或本地 BitSet，支持房主世界隔离。
 * <p>
 * 机关属于易失性状态：服务重启后优先按策划默认值恢复，或以 Zone 粒度存 Redis；
 * <b>不要</b>强依赖 MySQL 回写。bitIndex 约定由策划表分配（压力板、元素方碑、仙灵路径等）。
 */
public final class WorldStateBitmap {

    private static final Logger log = LoggerFactory.getLogger(WorldStateBitmap.class);
    private static final String KEY_PREFIX = "world:state:bitmap:";

    private final StringRedisTemplate redis;
    private final ConcurrentHashMap<String, BitSet> local = new ConcurrentHashMap<>();

    public WorldStateBitmap(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public WorldStateBitmap() {
        this.redis = null;
    }

    public static String scopeKey(int worldId, long hostPlayerId) {
        // hostPlayerId=0 表示公共世界线；>0 为房主世界隔离态
        return worldId + ":" + Math.max(0L, hostPlayerId);
    }

    public boolean get(String scopeKey, int bitIndex) {
        if (bitIndex < 0) {
            return false;
        }
        if (redis != null) {
            try {
                Boolean bit = redis.opsForValue().getBit(KEY_PREFIX + scopeKey, bitIndex);
                return Boolean.TRUE.equals(bit);
            } catch (Exception e) {
                log.warn("world bitmap get failed: {}", e.getMessage());
            }
        }
        BitSet set = local.get(scopeKey);
        return set != null && set.get(bitIndex);
    }

    public boolean set(String scopeKey, int bitIndex, boolean value) {
        if (bitIndex < 0) {
            return false;
        }
        if (redis != null) {
            try {
                redis.opsForValue().setBit(KEY_PREFIX + scopeKey, bitIndex, value);
                return true;
            } catch (Exception e) {
                log.warn("world bitmap set failed: {}", e.getMessage());
            }
        }
        local.compute(scopeKey, (k, set) -> {
            BitSet bits = set == null ? new BitSet() : set;
            bits.set(bitIndex, value);
            return bits;
        });
        return true;
    }

    public int cardinality(String scopeKey) {
        // Redis BITCOUNT 在部分客户端版本差异较大，优先本地近似；生产可换 RedisConnection
        BitSet set = local.get(scopeKey);
        if (set != null) {
            return set.cardinality();
        }
        if (redis != null) {
            try {
                // 无本地缓存时无法精确计数，返回 -1 表示未知（仍可通过 get 查单 bit）
                Boolean any = redis.hasKey(KEY_PREFIX + scopeKey);
                return Boolean.TRUE.equals(any) ? -1 : 0;
            } catch (Exception e) {
                log.warn("world bitmap count failed: {}", e.getMessage());
            }
        }
        return 0;
    }

    public void clear(String scopeKey) {
        if (redis != null) {
            try {
                redis.delete(KEY_PREFIX + scopeKey);
            } catch (Exception e) {
                log.warn("world bitmap clear failed: {}", e.getMessage());
            }
        }
        local.remove(scopeKey);
    }
}
