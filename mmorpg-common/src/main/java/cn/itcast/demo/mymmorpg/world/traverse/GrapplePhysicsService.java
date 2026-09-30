package cn.itcast.demo.mymmorpg.world.traverse;

import cn.itcast.demo.mymmorpg.physics.LitePhysicsEngine;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 钩锁物理拓展：拉拽敌人 / 摆荡踢派生。
 * 服务端仅校验落点是否在 GrappleNode 半径内，曲线由客户端 Seed 本地生成。
 */
@Service
public class GrapplePhysicsService {

    public static final double BASE_PULL_FORCE = 40.0;
    public static final double REF_SWING_SPEED = 12.0;
    /** 贝塞尔拉拽曲线控制点（t=0/0.5/1） */
    public static final float[] DEFAULT_PULL_CURVE_P0 = {0f, 0f};
    public static final float[] DEFAULT_PULL_CURVE_P1 = {0.35f, 0.85f};
    public static final float[] DEFAULT_PULL_CURVE_P2 = {1f, 1f};

    public enum BodyMass {
        LIGHT, HEAVY
    }

    private final LitePhysicsEngine litePhysics;
    private final ConcurrentHashMap<Long, Double> lastSwingSpeed = new ConcurrentHashMap<>();

    public GrapplePhysicsService(ObjectProvider<LitePhysicsEngine> litePhysics) {
        this.litePhysics = litePhysics.getIfAvailable(LitePhysicsEngine::new);
    }

    /** 无 Spring 上下文时的测试/门面构造 */
    public GrapplePhysicsService() {
        this.litePhysics = new LitePhysicsEngine();
    }

    /** 质量等级 1~10，替代简单 LIGHT/HEAVY 二分 */
    public static int massRating(BodyMass mass) {
        return mass == BodyMass.HEAVY ? 8 : 2;
    }

    public static int massRating(int rating) {
        return Math.max(1, Math.min(10, rating));
    }

    /**
     * 钩锁落点校验：LitePhysics 粗筛半径 + 高度，不计算真实拉拽曲线。
     */
    public Map<String, Object> validateGrappleLanding(
            long playerId, float landX, float landY, float landZ,
            float nodeX, float nodeY, float nodeZ, float nodeRadius) {
        boolean valid = litePhysics.grappleLandingValid(
                landX, landY, landZ, nodeX, nodeY, nodeZ, nodeRadius);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", valid);
        body.put("playerId", playerId);
        body.put("clientLocalSim", true);
        body.put("serverRadiusCheck", true);
        if (!valid) {
            body.put("note", "grapple_landing_out_of_radius");
        }
        return body;
    }

    /** 下发拉拽速度贝塞尔曲线参数，客户端本地模拟加速/减速。 */
    public Map<String, Object> pullSpeedCurve(String curveId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("curveId", curveId == null ? "default" : curveId);
        body.put("curveType", "BEZIER");
        body.put("p0", Map.of("t", DEFAULT_PULL_CURVE_P0[0], "speedMul", DEFAULT_PULL_CURVE_P0[1]));
        body.put("p1", Map.of("t", DEFAULT_PULL_CURVE_P1[0], "speedMul", DEFAULT_PULL_CURVE_P1[1]));
        body.put("p2", Map.of("t", DEFAULT_PULL_CURVE_P2[0], "speedMul", DEFAULT_PULL_CURVE_P2[1]));
        body.put("clientLocalSim", true);
        body.put("serverSyncIntervalMs", 300);
        return body;
    }

    /**
     * GRAPPLE_PULL_ENEMY：锚点为敌人时按力量与体型结算。
     */
    public Map<String, Object> pullEnemy(
            long playerId, long enemyId, BodyMass mass, double attackPower, long nowMs) {
        return pullEnemy(playerId, enemyId, massRating(mass), attackPower, 0.2, nowMs);
    }

    /**
     * GRAPPLE_PULL_ENEMY：基于 MassRating 的质量-动量公式。
     */
    public Map<String, Object> pullEnemy(
            long playerId, long enemyId, int massRating, double attackPower,
            double groundFriction, long nowMs) {
        int mass = massRating(massRating);
        double pullForce = BASE_PULL_FORCE + Math.max(0, attackPower) * 0.5;
        double knockBack = (pullForce / mass) * (1.0 - Math.max(0, Math.min(0.95, groundFriction)));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("msgId", MessageId.GRAPPLE_PULL_ENEMY_CS_REQ);
        body.put("event", "GRAPPLE_PULL_ENEMY");
        body.put("playerId", playerId);
        body.put("enemyId", enemyId);
        body.put("pullForce", pullForce);
        body.put("targetMassRating", mass);
        body.put("knockBackDistance", Math.round(knockBack * 1000d) / 1000d);
        body.put("groundFriction", groundFriction);
        body.put("atMs", nowMs);
        if (mass >= 7) {
            body.put("outcome", "PLAYER_PULLED_TO_ENEMY");
            body.put("collisionDamage", (int) Math.round(pullForce * 0.8));
            body.put("enemyHardStun", false);
        } else {
            body.put("outcome", "ENEMY_PULLED_TO_PLAYER");
            body.put("collisionDamage", 0);
            body.put("enemyHardStun", true);
            body.put("stunMs", 800);
        }
        body.put("predictedPhysicsDelta", Map.of(
                "event", "PREDICTED_PHYSICS_DELTA",
                "msgId", MessageId.PREDICTED_PHYSICS_DELTA_SC_NOTIFY,
                "knockBackDistance", body.get("knockBackDistance"),
                "timestampMs", nowMs));
        return body;
    }

    public void recordSwingSpeed(long playerId, double speed) {
        lastSwingSpeed.put(playerId, Math.max(0, speed));
    }

    /**
     * GRAPPLE_SWING_KICK：摆荡最高点释放，伤害倍率 × (速度/基准速度)。
     */
    public Map<String, Object> swingKick(long playerId, int baseDamage, boolean atApex) {
        if (!atApex) {
            return Map.of("ok", false, "error", "not_at_apex");
        }
        double speed = lastSwingSpeed.getOrDefault(playerId, REF_SWING_SPEED);
        double mult = Math.max(0.5, speed / REF_SWING_SPEED);
        int dmg = (int) Math.round(Math.max(1, baseDamage) * mult);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("event", "GRAPPLE_SWING_KICK");
        body.put("speed", speed);
        body.put("multiplier", Math.round(mult * 100.0) / 100.0);
        body.put("damage", dmg);
        return body;
    }
}
