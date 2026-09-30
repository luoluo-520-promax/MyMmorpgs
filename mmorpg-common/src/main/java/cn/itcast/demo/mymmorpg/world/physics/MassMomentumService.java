package cn.itcast.demo.mymmorpg.world.physics;

import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.world.puzzle.PhysicsAuthorityService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * P19 质量-动量继承引擎：MassRating 1~10，KnockBack = (Force/Mass) * (1 - Friction)。
 */
@Service
public class MassMomentumService {

    public static final double PREDICT_DEVIATION_METERS = 0.3;
    public static final int MIN_MASS = 1;
    public static final int MAX_MASS = 10;

    private final ConcurrentHashMap<Long, Integer> entityMass = new ConcurrentHashMap<>();
    private PhysicsAuthorityService physicsAuthority;

    public void bindPhysicsAuthority(PhysicsAuthorityService authority) {
        this.physicsAuthority = authority;
    }

    public void registerMass(long entityId, int massRating) {
        if (entityId > 0) {
            entityMass.put(entityId, clampMass(massRating));
        }
    }

    public int massOf(long entityId) {
        return entityMass.getOrDefault(entityId, 5);
    }

    /**
     * 计算击退距离并下发 PREDICTED_PHYSICS_DELTA。
     */
    public Map<String, Object> computeKnockBack(
            long attackerId, long targetId,
            double attackerForce, double groundFriction, long nowMs) {
        int mass = massOf(targetId);
        double friction = Math.max(0, Math.min(0.95, groundFriction));
        double distance = (attackerForce / mass) * (1.0 - friction);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("event", "PREDICTED_PHYSICS_DELTA");
        body.put("msgId", MessageId.PREDICTED_PHYSICS_DELTA_SC_NOTIFY);
        body.put("attackerId", attackerId);
        body.put("targetId", targetId);
        body.put("targetMassRating", mass);
        body.put("attackerForce", attackerForce);
        body.put("groundFriction", friction);
        body.put("knockBackDistance", Math.round(distance * 1000d) / 1000d);
        body.put("timestampMs", nowMs);
        body.put("clientSimulate", true);
        body.put("deviationThresholdM", PREDICT_DEVIATION_METERS);
        body.put("formula", "(AttackerForce/TargetMass)*(1-GroundFriction)");
        return body;
    }

    /**
     * 客户端模拟偏差超过 0.3m 时触发软拉回。
     */
    public Map<String, Object> validatePrediction(
            long entityId, float predictedX, float predictedY, float predictedZ,
            float actualX, float actualY, float actualZ, long nowMs) {
        double dx = actualX - predictedX;
        double dy = actualY - predictedY;
        double dz = actualZ - predictedZ;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        boolean over = dist > PREDICT_DEVIATION_METERS;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("entityId", entityId);
        body.put("deviationM", Math.round(dist * 1000d) / 1000d);
        body.put("thresholdM", PREDICT_DEVIATION_METERS);
        body.put("softPullback", over);
        if (over) {
            body.put("correctX", predictedX);
            body.put("correctY", predictedY);
            body.put("correctZ", predictedZ);
            body.put("note", "physics_authority_soft_pullback");
        }
        return body;
    }

    public static int clampMass(int mass) {
        return Math.max(MIN_MASS, Math.min(MAX_MASS, mass));
    }
}
