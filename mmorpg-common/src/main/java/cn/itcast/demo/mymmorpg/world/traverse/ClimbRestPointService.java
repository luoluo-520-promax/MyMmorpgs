package cn.itcast.demo.mymmorpg.world.traverse;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 攀爬歇脚点：攀爬过程中可恢复体力，减少「爬到一半掉下去」的负体验。
 */
@Service
public class ClimbRestPointService {

    public record RestPoint(
            String restId,
            String climbMeshId,
            float x, float y, float z,
            float radius,
            float staminaRecover,
            boolean grantsParkourSkill) {

        public RestPoint {
            restId = restId == null ? "" : restId.trim();
            climbMeshId = climbMeshId == null ? "" : climbMeshId.trim();
            radius = radius <= 0f ? 3f : radius;
            staminaRecover = staminaRecover <= 0f ? 35f : staminaRecover;
        }
    }

    public static final float CLIMB_JUMP_BONUS_RATIO = 0.30f;
    public static final long REST_POINT_BUFF_MS = 30_000L;
    public static final long NEGATIVE_STAMINA_WINDOW_MS = 800L;
    public static final float REST_JUMP_STAMINA_PRE_DEDUCT_RATIO = 0.50f;

    public record RestPointBuff(
            String restId,
            long grantedAtMs,
            long expireAtMs,
            boolean firstJumpConsumed) {
    }

    private final ConcurrentHashMap<String, RestPoint> restPoints = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Boolean> parkourUnlocked = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Boolean> climbJumpBonus = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, RestPointBuff> restPointBuffs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> negativeStaminaUntil = new ConcurrentHashMap<>();

    public void register(RestPoint point) {
        if (point != null && !point.restId().isBlank()) {
            restPoints.put(point.restId(), point);
        }
    }

    /**
     * @return 命中歇脚点时恢复体力并免除本段攀爬消耗
     */
    public Map<String, Object> tryRest(
            long playerId, String climbMeshId, float px, float py, float pz,
            StaminaConsumeService stamina) {
        RestPoint hit = resolveNear(climbMeshId, px, py, pz);
        if (hit == null) {
            return Map.of("ok", false, "onRestPoint", false);
        }
        float before = stamina.current(playerId);
        float after = stamina.recover(playerId, hit.staminaRecover());
        if (hit.grantsParkourSkill()) {
            parkourUnlocked.put(playerId, true);
        }
        climbJumpBonus.put(playerId, true);
        long nowMs = System.currentTimeMillis();
        RestPointBuff buff = new RestPointBuff(
                hit.restId(), nowMs, nowMs + REST_POINT_BUFF_MS, false);
        restPointBuffs.put(playerId, buff);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("onRestPoint", true);
        body.put("restId", hit.restId());
        body.put("climbExempt", true);
        body.put("staminaBefore", before);
        body.put("staminaAfter", after);
        body.put("recovered", after - before);
        body.put("parkourSkill", parkourUnlocked.getOrDefault(playerId, false));
        body.put("climbJumpBonus", CLIMB_JUMP_BONUS_RATIO);
        body.put("climbJumpBonusActive", true);
        body.put("restPointBuff", Map.of(
                "restId", buff.restId(),
                "expireAtMs", buff.expireAtMs(),
                "durationMs", REST_POINT_BUFF_MS));
        body.put("clientHint", "歇脚点：体力已恢复，30s 内首次攀爬跳跃可透支体力");
        return body;
    }

    /**
     * 离开歇脚点后的首次攀爬跳跃：预扣 50% 体力，800ms 内允许负体力移动。
     */
    public Map<String, Object> consumeRestJump(long playerId, StaminaConsumeService stamina, long nowMs) {
        RestPointBuff buff = restPointBuffs.get(playerId);
        if (buff == null || nowMs > buff.expireAtMs() || buff.firstJumpConsumed()) {
            return Map.of("ok", false, "restJump", false);
        }
        float before = stamina.current(playerId);
        float preDeduct = before * REST_JUMP_STAMINA_PRE_DEDUCT_RATIO;
        stamina.consumeAllowNegative(playerId, preDeduct);
        negativeStaminaUntil.put(playerId, nowMs + NEGATIVE_STAMINA_WINDOW_MS);
        restPointBuffs.put(playerId, new RestPointBuff(
                buff.restId(), buff.grantedAtMs(), buff.expireAtMs(), true));
        consumeClimbJumpBonus(playerId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("restJump", true);
        body.put("preDeduct", Math.round(preDeduct * 100f) / 100f);
        body.put("staminaAfter", stamina.current(playerId));
        body.put("negativeStaminaWindowMs", NEGATIVE_STAMINA_WINDOW_MS);
        body.put("climbJumpMultiplier", 1f + CLIMB_JUMP_BONUS_RATIO);
        body.put("clientHint", "歇口气跳得更高");
        return body;
    }

    public boolean inNegativeStaminaWindow(long playerId, long nowMs) {
        Long until = negativeStaminaUntil.get(playerId);
        return until != null && nowMs <= until;
    }

    public RestPointBuff activeBuff(long playerId, long nowMs) {
        RestPointBuff buff = restPointBuffs.get(playerId);
        if (buff == null || nowMs > buff.expireAtMs()) {
            restPointBuffs.remove(playerId);
            return null;
        }
        return buff;
    }

    public float climbJumpMultiplier(long playerId) {
        return Boolean.TRUE.equals(climbJumpBonus.get(playerId))
                ? 1f + CLIMB_JUMP_BONUS_RATIO : 1f;
    }

    public void consumeClimbJumpBonus(long playerId) {
        climbJumpBonus.remove(playerId);
    }

    public boolean hasParkourSkill(long playerId) {
        return Boolean.TRUE.equals(parkourUnlocked.get(playerId));
    }

    public RestPoint resolveNear(String climbMeshId, float px, float py, float pz) {
        RestPoint best = null;
        double bestDist = Double.MAX_VALUE;
        for (RestPoint p : restPoints.values()) {
            if (!p.climbMeshId().isBlank() && !p.climbMeshId().equals(climbMeshId)) {
                continue;
            }
            float dx = px - p.x();
            float dy = py - p.y();
            float dz = pz - p.z();
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d <= p.radius() && d < bestDist) {
                best = p;
                bestDist = d;
            }
        }
        return best;
    }
}
