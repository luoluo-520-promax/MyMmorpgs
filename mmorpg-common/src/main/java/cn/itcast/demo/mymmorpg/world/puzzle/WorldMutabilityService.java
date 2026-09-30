package cn.itcast.demo.mymmorpg.world.puzzle;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态可破坏环境：destroyable 状态存 Hash 语义；重生用 ZSET 分帧扫描。
 */
@Service
public class WorldMutabilityService {

    public enum DestroyableState {
        INTACT, DESTROYED
    }

    public enum DamageType {
        NORMAL, HEAVY_ATTACK, ELEMENTAL
    }

    public enum TerrainKind {
        BOUNCE_MUSHROOM, WIND_FIELD, CLIMB_VINE
    }

    public record DestroyableDef(
            String id,
            String kind,
            int worldId,
            float x, float y, float z,
            int hpThreshold,
            long respawnCooldownMs,
            boolean altersCollision) {
        public DestroyableDef {
            id = id == null ? "" : id.trim();
            kind = kind == null ? "TREE" : kind.trim().toUpperCase();
            hpThreshold = Math.max(1, hpThreshold);
            respawnCooldownMs = respawnCooldownMs <= 0 ? 300_000L : respawnCooldownMs;
        }
    }

    /**
     * 特殊地形交互冷却：弹射蘑菇 / 风场 / 藤蔓。
     * Redis 语义 {@code terrain:cooldown:{regionId}:{cellX}:{cellY}}。
     */
    public static final class TerrainInteractionTracker {
        public static final long DEFAULT_TTL_MS = 60_000L;
        public static final long MIN_TTL_MS = 30_000L;
        public static final long MAX_TTL_MS = 120_000L;

        private final ConcurrentHashMap<String, Long> cooldownUntil = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, TerrainKind> kinds = new ConcurrentHashMap<>();

        public static String redisKey(String regionId, int cellX, int cellY) {
            return "terrain:cooldown:" + regionId + ":" + cellX + ":" + cellY;
        }

        public static String clientCacheKey(String regionId, int cellX, int cellY) {
            return "terrain:cooldown:cache:" + regionId + ":" + cellX + ":" + cellY;
        }

        /** 客户端本地缓存：冷却中直接拒绝，无需每次发请求 */
        public Map<String, Object> clientCacheCheck(
                String regionId, int cellX, int cellY, long clientCachedUntilMs, long nowMs) {
            if (clientCachedUntilMs > nowMs) {
                return Map.of("ok", false, "error", "TERRAIN_EXHAUSTED",
                        "clientCacheHit", true,
                        "cacheKey", clientCacheKey(regionId, cellX, cellY),
                        "remainMs", clientCachedUntilMs - nowMs);
            }
            if (isExhausted(regionId, cellX, cellY, nowMs)) {
                Long until = cooldownUntilOf(regionId, cellX, cellY);
                return Map.of("ok", false, "error", "TERRAIN_EXHAUSTED",
                        "clientCacheHit", false,
                        "cacheKey", clientCacheKey(regionId, cellX, cellY),
                        "remainMs", until == null ? 0L : until - nowMs,
                        "syncClientCacheUntilMs", until == null ? nowMs : until);
            }
            return Map.of("ok", true, "clientCacheHit", false, "needServerConfirm", true);
        }

        public boolean isExhausted(String regionId, int cellX, int cellY, long nowMs) {
            String key = redisKey(regionId, cellX, cellY);
            Long until = cooldownUntil.get(key);
            if (until == null) {
                return false;
            }
            if (until <= nowMs) {
                cooldownUntil.remove(key);
                kinds.remove(key);
                return false;
            }
            return true;
        }

        public Map<String, Object> tryConsume(
                String regionId, int cellX, int cellY, TerrainKind kind, long ttlMs, long nowMs) {
            String key = redisKey(regionId, cellX, cellY);
            if (isExhausted(regionId, cellX, cellY, nowMs)) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("ok", false);
                body.put("error", "TERRAIN_EXHAUSTED");
                body.put("retcode", cn.itcast.demo.mymmorpg.protocol.RetCode.TERRAIN_EXHAUSTED);
                body.put("redisKey", key);
                body.put("witherAnim", true);
                body.put("consumeStamina", false);
                body.put("remainMs", cooldownUntil.get(key) - nowMs);
                return body;
            }
            long ttl = Math.max(MIN_TTL_MS, Math.min(MAX_TTL_MS, ttlMs <= 0 ? DEFAULT_TTL_MS : ttlMs));
            long until = nowMs + ttl;
            cooldownUntil.put(key, until);
            kinds.put(key, kind == null ? TerrainKind.BOUNCE_MUSHROOM : kind);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ok", true);
            body.put("redisKey", key);
            body.put("kind", kinds.get(key).name());
            body.put("cooldownUntilMs", until);
            body.put("ttlMs", ttl);
            body.put("consumeStamina", true);
            return body;
        }

        public Long cooldownUntilOf(String regionId, int cellX, int cellY) {
            return cooldownUntil.get(redisKey(regionId, cellX, cellY));
        }
    }

    private final ConcurrentHashMap<String, DestroyableDef> defs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DestroyableState> states = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> damageAccum = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> respawnTs = new ConcurrentHashMap<>();
    /** respawnTs → id，模拟 Redis ZSET */
    private final ConcurrentHashMap<String, Long> respawnIndex = new ConcurrentHashMap<>();
    private final TerrainInteractionTracker terrainTracker = new TerrainInteractionTracker();

    public TerrainInteractionTracker terrainTracker() {
        return terrainTracker;
    }

    public void register(DestroyableDef def) {
        if (def != null && !def.id().isBlank()) {
            defs.put(def.id(), def);
            states.putIfAbsent(def.id(), DestroyableState.INTACT);
        }
    }

    /**
     * HEAVY_ATTACK 累计伤害达阈值 → EnvironmentMutationEvent + AOI MutationBroadcast。
     */
    public Map<String, Object> applyDamage(
            String destroyableId, DamageType damageType, int damage, long nowMs, float viewerRadiusM) {
        DestroyableDef def = defs.get(destroyableId == null ? "" : destroyableId.trim());
        if (def == null) {
            return Map.of("ok", false, "error", "destroyable_not_found");
        }
        if (states.get(def.id()) == DestroyableState.DESTROYED) {
            return Map.of("ok", false, "error", "already_destroyed", "id", def.id());
        }
        if (damageType != DamageType.HEAVY_ATTACK) {
            return Map.of("ok", false, "error", "requires_heavy_attack", "damageType",
                    damageType == null ? "null" : damageType.name());
        }
        int accum = damageAccum.merge(def.id(), Math.max(0, damage), Integer::sum);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("id", def.id());
        body.put("accumDamage", accum);
        body.put("threshold", def.hpThreshold());
        if (accum < def.hpThreshold()) {
            body.put("mutated", false);
            return body;
        }
        states.put(def.id(), DestroyableState.DESTROYED);
        damageAccum.put(def.id(), 0);
        long readyAt = nowMs + def.respawnCooldownMs();
        respawnTs.put(def.id(), readyAt);
        respawnIndex.put(def.id(), readyAt);
        body.put("mutated", true);
        body.put("event", "EnvironmentMutationEvent");
        body.put("state", DestroyableState.DESTROYED.name());
        body.put("altersCollision", def.altersCollision());
        body.put("respawnTs", readyAt);
        String mutationType = "CLIFF".equals(def.kind()) || "WALL".equals(def.kind())
                ? "DESTROY_CLIFF" : "DESTROY";
        body.put("mutationType", mutationType);
        body.put("newClimbPath", "DESTROY_CLIFF".equals(mutationType));
        body.put("mutationBroadcast", Map.of(
                "cmd", "MutationBroadcast",
                "mutationType", mutationType,
                "id", def.id(),
                "kind", def.kind(),
                "x", def.x(), "y", def.y(), "z", def.z(),
                "radiusM", viewerRadiusM <= 0 ? 300f : viewerRadiusM,
                "state", DestroyableState.DESTROYED.name(),
                "newClimbPath", "DESTROY_CLIFF".equals(mutationType)));
        return body;
    }

    /** 分帧刷新：扫描重生时间戳最早的前 limit 条。 */
    public Map<String, Object> scanRespawn(long nowMs, int limit) {
        int lim = Math.max(1, Math.min(100, limit <= 0 ? 100 : limit));
        List<Map.Entry<String, Long>> due = new ArrayList<>();
        for (Map.Entry<String, Long> e : respawnIndex.entrySet()) {
            if (e.getValue() <= nowMs) {
                due.add(e);
            }
        }
        due.sort(Map.Entry.comparingByValue());
        List<Map<String, Object>> refreshed = new ArrayList<>();
        for (int i = 0; i < Math.min(lim, due.size()); i++) {
            String id = due.get(i).getKey();
            states.put(id, DestroyableState.INTACT);
            respawnTs.remove(id);
            respawnIndex.remove(id);
            DestroyableDef def = defs.get(id);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id);
            row.put("state", DestroyableState.INTACT.name());
            if (def != null) {
                row.put("kind", def.kind());
                row.put("x", def.x());
                row.put("z", def.z());
            }
            refreshed.add(row);
        }
        return Map.of("ok", true, "refreshed", refreshed, "count", refreshed.size(), "scannedCap", lim);
    }

    public Map<String, Object> snapshot(String id) {
        DestroyableDef def = defs.get(id);
        if (def == null) {
            return Map.of("ok", false, "error", "destroyable_not_found");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("id", id);
        body.put("kind", def.kind());
        body.put("state", states.getOrDefault(id, DestroyableState.INTACT).name());
        body.put("accumDamage", damageAccum.getOrDefault(id, 0));
        body.put("respawnTs", respawnTs.getOrDefault(id, 0L));
        body.put("redisKey", "destroyable:" + id);
        return body;
    }
}
