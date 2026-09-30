package cn.itcast.demo.mymmorpg.world.lock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 怪物/世界实体归属锁：Redis SETNX（类 Redisson 语义），防止大世界抢怪重复结算。
 * 无 Redis 时回退进程内锁，便于单测。
 */
@Component
public class DistributedEntityLock {

    private static final Logger log = LoggerFactory.getLogger(DistributedEntityLock.class);
    private static final String KEY = "world:entity:lock:";
    private static final Duration DEFAULT_TTL = Duration.ofSeconds(120);

    public record LockHandle(String lockKey, String token, long ownerPlayerId, boolean acquired) {
        public static LockHandle failed(String lockKey) {
            return new LockHandle(lockKey, "", 0L, false);
        }
    }

    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final ConcurrentHashMap<String, String> localLocks = new ConcurrentHashMap<>();

    public DistributedEntityLock(ObjectProvider<StringRedisTemplate> redisProvider) {
        this.redisProvider = redisProvider;
    }

    public DistributedEntityLock() {
        this.redisProvider = null;
    }

    public LockHandle tryAcquire(int sceneId, long entityId, long ownerPlayerId, Duration ttl) {
        String lockKey = sceneId + ":" + entityId;
        String redisKey = KEY + lockKey;
        String token = ownerPlayerId + ":" + UUID.randomUUID();
        Duration effective = ttl == null || ttl.isZero() || ttl.isNegative() ? DEFAULT_TTL : ttl;
        StringRedisTemplate redis = redis();
        if (redis != null) {
            try {
                Boolean ok = redis.opsForValue().setIfAbsent(redisKey, token, effective);
                if (Boolean.TRUE.equals(ok)) {
                    return new LockHandle(lockKey, token, ownerPlayerId, true);
                }
                return LockHandle.failed(lockKey);
            } catch (Exception e) {
                log.warn("entity lock redis failed, fallback local: {}", e.getMessage());
            }
        }
        String prev = localLocks.putIfAbsent(lockKey, token);
        if (prev == null) {
            return new LockHandle(lockKey, token, ownerPlayerId, true);
        }
        return LockHandle.failed(lockKey);
    }

    public boolean release(String lockKey, String token) {
        if (lockKey == null || lockKey.isBlank()) {
            return false;
        }
        StringRedisTemplate redis = redis();
        if (redis != null) {
            try {
                String redisKey = KEY + lockKey;
                String cur = redis.opsForValue().get(redisKey);
                if (token != null && token.equals(cur)) {
                    redis.delete(redisKey);
                    return true;
                }
                return false;
            } catch (Exception e) {
                log.warn("entity unlock redis failed: {}", e.getMessage());
            }
        }
        return localLocks.remove(lockKey, token);
    }

    public boolean releaseByOwner(int sceneId, long entityId, long ownerPlayerId) {
        String lockKey = sceneId + ":" + entityId;
        StringRedisTemplate redis = redis();
        if (redis != null) {
            try {
                String redisKey = KEY + lockKey;
                String cur = redis.opsForValue().get(redisKey);
                if (cur != null && cur.startsWith(ownerPlayerId + ":")) {
                    redis.delete(redisKey);
                    return true;
                }
            } catch (Exception e) {
                log.warn("entity unlock-by-owner redis failed: {}", e.getMessage());
            }
        }
        String cur = localLocks.get(lockKey);
        if (cur != null && cur.startsWith(ownerPlayerId + ":")) {
            return localLocks.remove(lockKey, cur);
        }
        return false;
    }

    public Map<String, String> localSnapshot() {
        return Map.copyOf(localLocks);
    }

    private StringRedisTemplate redis() {
        return redisProvider == null ? null : redisProvider.getIfAvailable();
    }
}
