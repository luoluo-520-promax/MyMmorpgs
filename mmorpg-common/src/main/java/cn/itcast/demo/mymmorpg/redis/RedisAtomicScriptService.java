package cn.itcast.demo.mymmorpg.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis Lua 原子脚本：扣费/升级/首杀判定一次 RTT 完成，避免 Redisson 锁重试堆积。
 */
@Component
public class RedisAtomicScriptService {

    private static final Logger log = LoggerFactory.getLogger(RedisAtomicScriptService.class);

    /** KEYS[1]=levelKey KEYS[2]=costKey ARGV[1]=maxLevel ARGV[2]=cost ARGV[3]=newLevel */
    private static final String UPGRADE_WORLD_LEVEL = """
            local lv = tonumber(redis.call('GET', KEYS[1]) or '0')
            local maxLv = tonumber(ARGV[1])
            local cost = tonumber(ARGV[2])
            local newLv = tonumber(ARGV[3])
            if lv >= maxLv then return {-1, lv} end
            if newLv <= lv then return {-2, lv} end
            local bal = tonumber(redis.call('GET', KEYS[2]) or '0')
            if bal < cost then return {-3, bal} end
            redis.call('DECRBY', KEYS[2], cost)
            redis.call('SET', KEYS[1], newLv)
            return {1, newLv, bal - cost}
            """;

    /** KEYS[1]=bossHpKey ARGV[1]=damage ARGV[2]=playerId */
    private static final String BOSS_FIRST_HIT = """
            local hp = tonumber(redis.call('GET', KEYS[1]) or '0')
            local dmg = tonumber(ARGV[1])
            local pid = ARGV[2]
            if hp <= 0 then return {0, ''} end
            local first = redis.call('GET', KEYS[1] .. ':first')
            if first then return {0, first} end
            local remain = hp - dmg
            if remain <= 0 then
                redis.call('SET', KEYS[1], 0)
                redis.call('SET', KEYS[1] .. ':first', pid)
                return {1, pid}
            end
            redis.call('SET', KEYS[1], remain)
            return {0, ''}
            """;

    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final ConcurrentHashMap<String, Integer> localBalances = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> localLevels = new ConcurrentHashMap<>();

    public RedisAtomicScriptService(ObjectProvider<StringRedisTemplate> redisProvider) {
        this.redisProvider = redisProvider;
    }

    public RedisAtomicScriptService() {
        this.redisProvider = null;
    }

    /**
     * 原子升级世界等级：扣费 + 写等级一次完成。
     *
     * @return code: 1=ok, -1=已达上限, -2=等级无效, -3=余额不足
     */
    public Map<String, Object> upgradeWorldLevel(String levelKey, String costKey,
                                                  int maxLevel, int cost, int newLevel) {
        StringRedisTemplate redis = redis();
        if (redis != null) {
            try {
                DefaultRedisScript<List> script = new DefaultRedisScript<>(UPGRADE_WORLD_LEVEL, List.class);
                @SuppressWarnings("unchecked")
                List<Long> result = redis.execute(script, List.of(levelKey, costKey),
                        String.valueOf(maxLevel), String.valueOf(cost), String.valueOf(newLevel));
                return parseUpgradeResult(result);
            } catch (Exception e) {
                log.warn("upgradeWorldLevel lua failed: {}", e.getMessage());
            }
        }
        return localUpgrade(levelKey, costKey, maxLevel, cost, newLevel);
    }

    public Map<String, Object> bossFirstHit(String bossHpKey, int damage, long playerId) {
        StringRedisTemplate redis = redis();
        if (redis != null) {
            try {
                DefaultRedisScript<List> script = new DefaultRedisScript<>(BOSS_FIRST_HIT, List.class);
                @SuppressWarnings("unchecked")
                List<Long> result = redis.execute(script, List.of(bossHpKey),
                        String.valueOf(damage), String.valueOf(playerId));
                if (result != null && !result.isEmpty()) {
                    boolean first = result.get(0) != null && result.get(0) == 1L;
                    String pid = result.size() > 1 && result.get(1) != null ? String.valueOf(result.get(1)) : "";
                    return Map.of("ok", true, "firstKill", first, "firstPlayerId", pid);
                }
            } catch (Exception e) {
                log.warn("bossFirstHit lua failed: {}", e.getMessage());
            }
        }
        return Map.of("ok", false, "error", "redis_unavailable");
    }

    private Map<String, Object> localUpgrade(String levelKey, String costKey,
                                              int maxLevel, int cost, int newLevel) {
        int lv = localLevels.getOrDefault(levelKey, 0);
        if (lv >= maxLevel) {
            return Map.of("ok", false, "code", -1, "level", lv);
        }
        if (newLevel <= lv) {
            return Map.of("ok", false, "code", -2, "level", lv);
        }
        int bal = localBalances.getOrDefault(costKey, 1000);
        if (bal < cost) {
            return Map.of("ok", false, "code", -3, "balance", bal);
        }
        localBalances.put(costKey, bal - cost);
        localLevels.put(levelKey, newLevel);
        return Map.of("ok", true, "code", 1, "level", newLevel, "balance", bal - cost);
    }

    private static Map<String, Object> parseUpgradeResult(List<Long> result) {
        if (result == null || result.isEmpty()) {
            return Map.of("ok", false, "error", "empty_result");
        }
        long code = result.get(0);
        if (code == 1L) {
            return Map.of("ok", true, "code", 1,
                    "level", result.size() > 1 ? result.get(1) : 0,
                    "balance", result.size() > 2 ? result.get(2) : 0);
        }
        return Map.of("ok", false, "code", (int) code,
                "detail", result.size() > 1 ? result.get(1) : 0);
    }

    private StringRedisTemplate redis() {
        return redisProvider == null ? null : redisProvider.getIfAvailable();
    }
}
