package cn.itcast.demo.mymmorpg.world.puzzle;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 地形拓扑图：DESTROY_CLIFF 因果链校验——射线相交 + 重型攻击/炸弹弹药扣除，杜绝隔空碎岩。
 */
@Service
public class TerrainTopologyGraph {

    public enum DestroyTool {
        HEAVY_ATTACK, BOMB
    }

    public record CliffNode(
            String cliffId,
            int worldId,
            float x, float y, float z,
            float radiusM) {
        public CliffNode {
            cliffId = cliffId == null ? "" : cliffId.trim();
            radiusM = radiusM <= 0 ? 3f : radiusM;
        }
    }

    private final ConcurrentHashMap<String, CliffNode> cliffs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, AtomicInteger> bombAmmo = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, AtomicInteger> heavyStamina = new ConcurrentHashMap<>();

    public void registerCliff(CliffNode node) {
        if (node != null && !node.cliffId().isBlank()) {
            cliffs.put(node.cliffId(), node);
        }
    }

    public void grantBomb(long playerId, int count) {
        bombAmmo.computeIfAbsent(playerId, id -> new AtomicInteger(0)).addAndGet(Math.max(0, count));
    }

    public void grantHeavyStamina(long playerId, int points) {
        heavyStamina.computeIfAbsent(playerId, id -> new AtomicInteger(0)).addAndGet(Math.max(0, points));
    }

    public int bombCount(long playerId) {
        return bombAmmo.getOrDefault(playerId, new AtomicInteger(0)).get();
    }

    /**
     * 验证玩家当前位置到悬崖是否射线相交，并扣除对应体力/弹药。
     */
    public Map<String, Object> validateDestroyCliff(
            long playerId, String cliffId,
            float playerX, float playerY, float playerZ,
            float aimDirX, float aimDirY, float aimDirZ,
            DestroyTool tool, int staminaCost, long nowMs) {
        CliffNode cliff = cliffs.get(cliffId == null ? "" : cliffId.trim());
        if (cliff == null) {
            return Map.of("ok", false, "error", "cliff_not_found");
        }
        DestroyTool t = tool == null ? DestroyTool.HEAVY_ATTACK : tool;
        float dx = cliff.x() - playerX;
        float dy = cliff.y() - playerY;
        float dz = cliff.z() - playerZ;
        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float maxRange = t == DestroyTool.BOMB ? 18f : 8f;
        if (dist > maxRange) {
            return Map.of("ok", false, "error", "out_of_range", "dist", dist, "maxRange", maxRange);
        }
        float len = (float) Math.sqrt(aimDirX * aimDirX + aimDirY * aimDirY + aimDirZ * aimDirZ);
        if (len < 1e-4f) {
            return Map.of("ok", false, "error", "aim_required");
        }
        float ux = aimDirX / len, uy = aimDirY / len, uz = aimDirZ / len;
        // 射线最近点距离（点到射线）
        float proj = dx * ux + dy * uy + dz * uz;
        if (proj < 0 || proj > maxRange) {
            return Map.of("ok", false, "error", "ray_miss", "reason", "projection_out");
        }
        float cx = playerX + ux * proj;
        float cy = playerY + uy * proj;
        float cz = playerZ + uz * proj;
        float miss = (float) Math.sqrt(
                (cx - cliff.x()) * (cx - cliff.x())
                        + (cy - cliff.y()) * (cy - cliff.y())
                        + (cz - cliff.z()) * (cz - cliff.z()));
        if (miss > cliff.radiusM()) {
            return Map.of("ok", false, "error", "ray_miss", "missDist", miss, "radius", cliff.radiusM());
        }

        if (t == DestroyTool.BOMB) {
            AtomicInteger ammo = bombAmmo.computeIfAbsent(playerId, id -> new AtomicInteger(0));
            if (ammo.get() <= 0) {
                return Map.of("ok", false, "error", "bomb_exhausted");
            }
            ammo.decrementAndGet();
        } else {
            int cost = Math.max(1, staminaCost);
            AtomicInteger stam = heavyStamina.computeIfAbsent(playerId, id -> new AtomicInteger(0));
            if (stam.get() < cost) {
                return Map.of("ok", false, "error", "heavy_stamina_exhausted",
                        "required", cost, "remain", stam.get());
            }
            stam.addAndGet(-cost);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("cliffId", cliff.cliffId());
        body.put("mutationType", "DESTROY_CLIFF");
        body.put("causalChain", true);
        body.put("tool", t.name());
        body.put("rayHit", true);
        body.put("dist", Math.round(dist * 100f) / 100f);
        body.put("atMs", nowMs);
        body.put("bombRemain", bombCount(playerId));
        body.put("heavyStaminaRemain",
                heavyStamina.getOrDefault(playerId, new AtomicInteger(0)).get());
        return body;
    }
}
