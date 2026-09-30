package cn.itcast.demo.mymmorpg.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 本地堆内 L1 位置缓存（Caffeine）：SceneMoveCmd 校验只更新本地，不阻塞主 Tick 写 Redis。
 */
@Component
public class LocalPositionCache {

    public record Position(float x, float y, float z, long updatedAtMs) {
    }

    private final Cache<Long, Position> cache = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong updates = new AtomicLong();

    public void put(long playerId, float x, float y, float z, long nowMs) {
        cache.put(playerId, new Position(x, y, z, nowMs));
        updates.incrementAndGet();
    }

    public Position get(long playerId) {
        Position p = cache.getIfPresent(playerId);
        if (p != null) {
            hits.incrementAndGet();
        } else {
            misses.incrementAndGet();
        }
        return p;
    }

    public void invalidate(long playerId) {
        cache.invalidate(playerId);
    }

    public long estimatedSize() {
        return cache.estimatedSize();
    }

    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("size", cache.estimatedSize());
        m.put("hits", hits.get());
        m.put("misses", misses.get());
        m.put("updates", updates.get());
        return m;
    }
}
