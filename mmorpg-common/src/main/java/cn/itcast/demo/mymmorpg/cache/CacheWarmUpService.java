package cn.itcast.demo.mymmorpg.cache;

import cn.itcast.demo.mymmorpg.entity.MonsterConfig;
import cn.itcast.demo.mymmorpg.service.ConfigQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 版本切换前缓存预热：分布式锁 + 双重检测，避免开服瞬间击穿 MySQL。
 */
@Service
public class CacheWarmUpService {

    private static final Logger log = LoggerFactory.getLogger(CacheWarmUpService.class);
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    private static final Duration CACHE_TTL = Duration.ofHours(6);

    private final ConfigQueryService configQueryService;
    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Object> localCache = new ConcurrentHashMap<>();

    public CacheWarmUpService(
            ConfigQueryService configQueryService,
            ObjectProvider<StringRedisTemplate> redisProvider,
            ObjectMapper objectMapper) {
        this.configQueryService = configQueryService;
        this.redisProvider = redisProvider;
        this.objectMapper = objectMapper;
    }

    /**
     * 异步预热指定版本涉及的活动/商店/深渊配置到 Redis。
     */
    public Map<String, Object> warmUpForVersion(String versionCode, List<String> hotKeys) {
        long started = System.currentTimeMillis();
        List<String> warmed = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        if (hotKeys == null || hotKeys.isEmpty()) {
            hotKeys = defaultHotKeys();
        }
        for (String key : hotKeys) {
            if (fillHotKey(key)) {
                warmed.add(key);
            } else {
                skipped.add(key);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("versionCode", versionCode);
        out.put("warmed", warmed);
        out.put("skipped", skipped);
        out.put("elapsedMs", System.currentTimeMillis() - started);
        return out;
    }

    /** 带分布式锁的热点 Key 回填。 */
    public boolean fillHotKey(String redisKey) {
        if (redisKey == null || redisKey.isBlank()) {
            return false;
        }
        String key = redisKey.trim();
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis != null) {
            try {
                if (Boolean.TRUE.equals(redis.hasKey(key))) {
                    return true;
                }
                String lockKey = "lock:warmup:" + key;
                Boolean locked = redis.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
                if (!Boolean.TRUE.equals(locked)) {
                    Thread.sleep(50L);
                    return Boolean.TRUE.equals(redis.hasKey(key));
                }
                try {
                    if (Boolean.TRUE.equals(redis.hasKey(key))) {
                        return true;
                    }
                    String payload = loadPayloadFromDb(key);
                    if (payload != null) {
                        redis.opsForValue().set(key, payload, CACHE_TTL);
                        return true;
                    }
                } finally {
                    redis.delete(lockKey);
                }
            } catch (Exception e) {
                log.warn("fillHotKey redis failed key={}: {}", key, e.getMessage());
            }
        }
        return fillLocal(key);
    }

    private boolean fillLocal(String key) {
        Object cached = localCache.get(key);
        if (cached != null) {
            return true;
        }
        String payload = loadPayloadFromDb(key);
        if (payload == null) {
            return false;
        }
        localCache.put(key, payload);
        return true;
    }

    private String loadPayloadFromDb(String redisKey) {
        try {
            if (redisKey.startsWith("abyss:floor:config:")) {
                List<MonsterConfig> monsters = configQueryService.listAllMonsters();
                return objectMapper.writeValueAsString(monsters);
            }
            if (redisKey.startsWith("activity:config:")) {
                return objectMapper.writeValueAsString(Map.of("loaded", true, "key", redisKey));
            }
            if (redisKey.startsWith("shop:product:config:")) {
                return objectMapper.writeValueAsString(Map.of("loaded", true, "key", redisKey));
            }
        } catch (Exception e) {
            log.warn("loadPayloadFromDb failed key={}: {}", redisKey, e.getMessage());
        }
        return null;
    }

    public static List<String> defaultHotKeys() {
        List<String> keys = new ArrayList<>();
        keys.add("abyss:floor:config:12");
        keys.add("activity:config:current");
        keys.add("shop:product:config:current");
        return keys;
    }
}
