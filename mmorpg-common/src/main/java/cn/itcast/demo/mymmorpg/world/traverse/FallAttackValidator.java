package cn.itcast.demo.mymmorpg.world.traverse;

import cn.itcast.demo.mymmorpg.world.puzzle.WorldMutabilityService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 下落攻击（登龙/下落重击）服务端校验：垂直维度无缝切入战斗。
 */
@Service
public class FallAttackValidator {

    public static final float MIN_FALL_ATTACK_HEIGHT_M = 5f;
    public static final float MIN_VELOCITY_Y = -3f;
    public static final String ACTION_FALL_HEAVY = "ACTION_FALL_HEAVY";

    private final WorldMutabilityService mutability;
    private final ConcurrentHashMap<Long, Long> landingLagUntil = new ConcurrentHashMap<>();

    public FallAttackValidator() {
        this(new WorldMutabilityService());
    }

    public FallAttackValidator(WorldMutabilityService mutability) {
        this.mutability = mutability == null ? new WorldMutabilityService() : mutability;
    }

    /**
     * 校验 fallDistance / velocityY；命中则按高度倍率结算，落地可产生冲击波破坏物与硬直。
     */
    public Map<String, Object> validateFallHeavy(
            long playerId,
            float fallDistanceM,
            float velocityY,
            boolean hitEnemy,
            int baseDamage,
            int worldId,
            float x, float y, float z,
            long nowMs) {
        Long lagUntil = landingLagUntil.get(playerId);
        if (lagUntil != null && nowMs < lagUntil) {
            return Map.of("ok", false, "error", "landing_lag",
                    "lagRemainMs", lagUntil - nowMs, "forceAnim", "LANDING_LAG");
        }
        if (fallDistanceM < MIN_FALL_ATTACK_HEIGHT_M || velocityY > MIN_VELOCITY_Y) {
            return Map.of("ok", false, "error", "fall_condition_not_met",
                    "minFallAttackHeight", MIN_FALL_ATTACK_HEIGHT_M,
                    "minVelocityY", MIN_VELOCITY_Y,
                    "fallDistance", fallDistanceM,
                    "velocityY", velocityY);
        }
        double mult = 1.0 + fallDistanceM / 10.0;
        int damage = Math.max(1, (int) Math.round(baseDamage * mult));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("action", ACTION_FALL_HEAVY);
        body.put("playerId", playerId);
        body.put("fallDistance", fallDistanceM);
        body.put("velocityY", velocityY);
        body.put("damageMultiplier", Math.round(mult * 100.0) / 100.0);
        body.put("damage", damage);
        body.put("hitEnemy", hitEnemy);

        if (hitEnemy) {
            body.put("landingLagMs", 0);
            body.put("staggerCancel", false);
        } else {
            int lagMs = (int) Math.min(1200, 300 + fallDistanceM * 40);
            landingLagUntil.put(playerId, nowMs + lagMs);
            body.put("landingLagMs", lagMs);
            body.put("forceAnim", "LANDING_LAG");
            body.put("staggerCancel", true);
            body.put("note", "server_authoritative_lag_prevents_cancel");
        }

        String shockId = "fall-shock-" + UUID.randomUUID();
        mutability.register(new WorldMutabilityService.DestroyableDef(
                shockId, "SHOCKWAVE", worldId, x, y, z, Math.max(1, damage / 10), 60_000L, true));
        Map<String, Object> shock = mutability.applyDamage(
                shockId, WorldMutabilityService.DamageType.HEAVY_ATTACK, Math.max(1, damage / 10), nowMs, 40f);
        body.put("shockwave", shock);
        body.put("shockRadiusM", Math.min(12f, 3f + fallDistanceM * 0.4f));
        return body;
    }

    public boolean inLandingLag(long playerId, long nowMs) {
        Long until = landingLagUntil.get(playerId);
        return until != null && nowMs < until;
    }
}
