package cn.itcast.demo.mymmorpg.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 策划表 Redis 缓存热更：清空 {@link ConfigQueryService} 相关 cache 区域。
 */
@Service
public class ConfigCacheReloadService {

    private static final Logger log = LoggerFactory.getLogger(ConfigCacheReloadService.class);

    public static final List<String> CONFIG_CACHE_NAMES = List.of(
            "mapConfigById",
            "allMonsterConfigs",
            "monsterConfigById",
            "skillConfigById",
            "buffConfigById",
            "itemConfigById"
    );

    private final ObjectProvider<CacheManager> cacheManager;

    public ConfigCacheReloadService(ObjectProvider<CacheManager> cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * @return 已清空的 cache 名称列表；无 CacheManager 时返回空列表
     */
    public List<String> evictConfigCaches() {
        CacheManager manager = cacheManager.getIfAvailable();
        if (manager == null) {
            log.warn("Config cache reload skipped: CacheManager unavailable");
            return List.of();
        }
        List<String> cleared = new ArrayList<>();
        for (String name : CONFIG_CACHE_NAMES) {
            Cache cache = manager.getCache(name);
            if (cache != null) {
                cache.clear();
                cleared.add(name);
            }
        }
        log.info("Config caches evicted: {}", cleared);
        return cleared;
    }
}
