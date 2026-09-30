package cn.itcast.demo.mymmorpg.world.traverse;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 水下领域独立物理层：BIOME_UNDERWATER 下突进缩距、火/雷爆炸改导电 DoT、Boss BT 水下分支。
 */
@Service
public class UnderwaterPhysicsService {

    public static final String BIOME_UNDERWATER = "BIOME_UNDERWATER";
    public static final double DASH_DISTANCE_FACTOR = 0.4; // 缩减 60% → 保留 40%
    public static final String BOSS_BT_BRANCH = "UNDERWATER";

    private final ConcurrentHashMap<Long, Boolean> underwater = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> regionBiome = new ConcurrentHashMap<>();

    public void markRegionBiome(int regionId, String biome) {
        regionBiome.put(regionId, biome == null ? "" : biome.trim());
    }

    public void enterUnderwater(long playerId, int regionId) {
        String biome = regionBiome.getOrDefault(regionId, BIOME_UNDERWATER);
        if (BIOME_UNDERWATER.equals(biome) || biome.isBlank()) {
            underwater.put(playerId, true);
            regionBiome.putIfAbsent(regionId, BIOME_UNDERWATER);
        }
    }

    public void leaveUnderwater(long playerId) {
        underwater.remove(playerId);
    }

    public boolean isUnderwater(long playerId) {
        return Boolean.TRUE.equals(underwater.get(playerId));
    }

    public String biomeOf(int regionId) {
        return regionBiome.getOrDefault(regionId, "");
    }

    /**
     * MovementAdmission 钩子：突进类位移距离缩减 60%。
     */
    public Map<String, Object> scaleDashDistance(long playerId, float requestedDistance) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestedDistance", requestedDistance);
        if (!isUnderwater(playerId)) {
            body.put("ok", true);
            body.put("underwater", false);
            body.put("effectiveDistance", requestedDistance);
            body.put("factor", 1.0);
            return body;
        }
        float effective = (float) (requestedDistance * DASH_DISTANCE_FACTOR);
        body.put("ok", true);
        body.put("underwater", true);
        body.put("biome", BIOME_UNDERWATER);
        body.put("effectiveDistance", effective);
        body.put("factor", DASH_DISTANCE_FACTOR);
        body.put("reductionPct", 60);
        return body;
    }

    /**
     * 元素反应约束：禁止火/雷爆炸（OVERLOAD），改为导电 DoT。
     */
    public Map<String, Object> filterElementReaction(
            long playerId, String reactionType, String appliedElement) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("underwater", isUnderwater(playerId));
        if (!isUnderwater(playerId)) {
            body.put("ok", true);
            body.put("reaction", reactionType);
            body.put("rewritten", false);
            return body;
        }
        String r = reactionType == null ? "" : reactionType.toUpperCase();
        String el = appliedElement == null ? "" : appliedElement.toUpperCase();
        boolean explosive = "OVERLOAD".equals(r)
                || Set.of("PYRO", "FIRE", "ELECTRO", "THUNDER").contains(el) && "OVERLOAD".equals(r);
        if ("OVERLOAD".equals(r) || explosive) {
            body.put("ok", true);
            body.put("reaction", "ELECTRO_CONDUCT_DOT");
            body.put("rewritten", true);
            body.put("forbidden", "OVERLOAD_EXPLOSION");
            body.put("dotTicks", 4);
            body.put("dotIntervalMs", 500);
            body.put("note", "underwater_no_explosion");
            return body;
        }
        if ("PYRO".equals(el) || "FIRE".equals(el)) {
            body.put("ok", true);
            body.put("reaction", "STEAM_SUPPRESS");
            body.put("rewritten", true);
            body.put("note", "fire_quenched_underwater");
            return body;
        }
        body.put("ok", true);
        body.put("reaction", reactionType);
        body.put("rewritten", false);
        return body;
    }

    public Map<String, Object> bossBehaviorTreeBranch(String bossId) {
        return Map.of(
                "ok", true,
                "bossId", bossId == null ? "" : bossId,
                "btBranch", BOSS_BT_BRANCH,
                "biome", BIOME_UNDERWATER,
                "required", true);
    }
}
