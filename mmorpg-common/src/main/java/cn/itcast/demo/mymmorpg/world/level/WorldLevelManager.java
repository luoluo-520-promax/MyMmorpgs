package cn.itcast.demo.mymmorpg.world.level;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import cn.itcast.demo.mymmorpg.redis.RedisAtomicScriptService;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 世界等级动态数值缩放：按玩家/房主世界等级调整怪物攻击与血量系数，在 Spawn 时下发。
 */
@Service
public class WorldLevelManager {

    private static final Logger log = LoggerFactory.getLogger(WorldLevelManager.class);
    private static final String KEY = "world:level:";
    private static final String COST_KEY = "world:level:cost:";
    private static final Duration TTL = Duration.ofDays(30);
    private static final int MAX_LEVEL = 12;

    /** 每级相对 recommendLevel 的攻防缩放步长 */
    private static final double STEP_ATK = 0.08;
    private static final double STEP_HP = 0.12;
    private static final double MIN_MUL = 0.35;
    private static final double MAX_MUL = 3.0;

    public record ScaledStats(
            int worldLevel,
            int recommendLevel,
            int baseAtk,
            int baseHp,
            int scaledAtk,
            int scaledHp,
            double atkMul,
            double hpMul) {
    }

    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final ObjectProvider<RedisAtomicScriptService> atomicScripts;
    private final ConcurrentHashMap<String, Integer> localLevels = new ConcurrentHashMap<>();

    public WorldLevelManager(ObjectProvider<StringRedisTemplate> redisProvider,
                             ObjectProvider<RedisAtomicScriptService> atomicScripts) {
        this.redisProvider = redisProvider;
        this.atomicScripts = atomicScripts;
    }

    public WorldLevelManager() {
        this.redisProvider = null;
        this.atomicScripts = null;
    }

    public void setWorldLevel(int worldId, long hostPlayerId, int worldLevel) {
        int lv = Math.max(0, Math.min(worldLevel, MAX_LEVEL));
        String key = scope(worldId, hostPlayerId);
        StringRedisTemplate redis = redis();
        if (redis != null) {
            try {
                redis.opsForValue().set(KEY + key, String.valueOf(lv), TTL);
                return;
            } catch (Exception e) {
                log.warn("world level write failed: {}", e.getMessage());
            }
        }
        localLevels.put(key, lv);
    }

    public int getWorldLevel(int worldId, long hostPlayerId) {
        String key = scope(worldId, hostPlayerId);
        StringRedisTemplate redis = redis();
        if (redis != null) {
            try {
                String v = redis.opsForValue().get(KEY + key);
                if (v != null && !v.isBlank()) {
                    return Integer.parseInt(v.trim());
                }
            } catch (Exception e) {
                log.warn("world level read failed: {}", e.getMessage());
            }
        }
        return localLevels.getOrDefault(key, 0);
    }

    /**
     * Lua 原子升级：扣费 + 写等级一次 RTT，避免分布式锁重试堆积。
     */
    public Map<String, Object> tryUpgradeWorldLevel(int worldId, long hostPlayerId, int cost, int targetLevel) {
        int cur = getWorldLevel(worldId, hostPlayerId);
        int next = Math.max(0, Math.min(targetLevel, MAX_LEVEL));
        if (next <= cur) {
            return Map.of("ok", false, "code", -2, "level", cur);
        }
        RedisAtomicScriptService scripts = atomicScripts == null ? null : atomicScripts.getIfAvailable();
        if (scripts != null) {
            return scripts.upgradeWorldLevel(
                    KEY + scope(worldId, hostPlayerId),
                    COST_KEY + hostPlayerId,
                    MAX_LEVEL, cost, next);
        }
        return scriptsFallbackUpgrade(worldId, hostPlayerId, cost, next);
    }

    private Map<String, Object> scriptsFallbackUpgrade(int worldId, long hostPlayerId, int cost, int next) {
        RedisAtomicScriptService local = new RedisAtomicScriptService();
        return local.upgradeWorldLevel(
                KEY + scope(worldId, hostPlayerId),
                COST_KEY + hostPlayerId,
                MAX_LEVEL, cost, next);
    }

    public ScaledStats scale(int worldLevel, int recommendLevel, int baseAtk, int baseHp) {
        int delta = worldLevel - Math.max(0, recommendLevel);
        double atkMul = clamp(1.0 + delta * STEP_ATK);
        double hpMul = clamp(1.0 + delta * STEP_HP);
        int atk = Math.max(1, (int) Math.round(Math.max(1, baseAtk) * atkMul));
        int hp = Math.max(1, (int) Math.round(Math.max(1, baseHp) * hpMul));
        return new ScaledStats(worldLevel, recommendLevel, baseAtk, baseHp, atk, hp, atkMul, hpMul);
    }

    public ScaledStats scaleForSpawn(int worldId, long hostPlayerId, int recommendLevel, int baseAtk, int baseHp) {
        return scale(getWorldLevel(worldId, hostPlayerId), recommendLevel, baseAtk, baseHp);
    }

    public Map<String, Object> toView(int worldId, long hostPlayerId) {
        int lv = getWorldLevel(worldId, hostPlayerId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("worldId", worldId);
        m.put("hostPlayerId", hostPlayerId);
        m.put("worldLevel", lv);
        m.put("atkStep", STEP_ATK);
        m.put("hpStep", STEP_HP);
        return m;
    }

    private static String scope(int worldId, long hostPlayerId) {
        return worldId + ":" + Math.max(0L, hostPlayerId);
    }

    private static double clamp(double v) {
        return Math.max(MIN_MUL, Math.min(MAX_MUL, v));
    }

    private StringRedisTemplate redis() {
        return redisProvider == null ? null : redisProvider.getIfAvailable();
    }
}
