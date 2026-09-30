package cn.itcast.demo.mymmorpg.world.traverse;

import cn.itcast.demo.mymmorpg.sync.MovementType;
import cn.itcast.demo.mymmorpg.sync.SceneMoveCmd;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 立体移动动量继承：滑翔→下落、钩锁摆荡→冲刺等状态切换时保留速度向量。
 */
@Service
public class TraverseMomentumService {

    public static final float GLIDE_TO_FALL_HORIZONTAL_RETAIN = 0.70f;
    public static final float SWING_ATTACK_SPEED_BONUS_BASE = 1.0f;
    public static final float DEFAULT_SURFACE_FRICTION = 0.1f;
    public static final float DEFAULT_BRAKE_DECEL = 0.5f;

    public record VelocityVector(float vx, float vy, float vz, float speed) {
    }

    /**
     * 冰面/水面滑行摩擦衰减：客户端本地模拟，服务端周期性校验速度曲线。
     */
    public Map<String, Object> applyFriction(
            VelocityVector current, float surfaceFriction, float brakeDecel, long deltaMs) {
        if (current == null) {
            return Map.of("ok", false, "error", "velocity_required");
        }
        float friction = surfaceFriction <= 0 ? DEFAULT_SURFACE_FRICTION : surfaceFriction;
        float brake = brakeDecel <= 0 ? DEFAULT_BRAKE_DECEL : brakeDecel;
        float dt = Math.max(0.016f, deltaMs / 1000f);
        float horizSpeed = (float) Math.sqrt(current.vx() * current.vx() + current.vz() * current.vz());
        float decay = Math.max(0f, 1f - friction * dt);
        float newHoriz = Math.max(0f, horizSpeed * decay - brake * dt);
        float scale = horizSpeed > 0.01f ? newHoriz / horizSpeed : 0f;
        VelocityVector after = new VelocityVector(
                current.vx() * scale, current.vy(), current.vz() * scale, newHoriz);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("surfaceFriction", friction);
        body.put("brakeDeceleration", brake);
        body.put("beforeSpeed", horizSpeed);
        body.put("afterSpeed", newHoriz);
        body.put("velocity", Map.of("vx", after.vx(), "vy", after.vy(), "vz", after.vz(), "speed", after.speed()));
        return body;
    }

    /**
     * 计算状态切换后的继承速度。
     */
    public VelocityVector inheritOnTransition(
            MovementType from, MovementType to, VelocityVector current) {
        if (current == null) {
            return new VelocityVector(0f, 0f, 0f, 0f);
        }
        if (from == MovementType.GLIDE && (to == MovementType.WALK || to == MovementType.SWIM)) {
            return new VelocityVector(
                    current.vx() * GLIDE_TO_FALL_HORIZONTAL_RETAIN,
                    current.vy(),
                    current.vz() * GLIDE_TO_FALL_HORIZONTAL_RETAIN,
                    current.speed() * GLIDE_TO_FALL_HORIZONTAL_RETAIN);
        }
        if (from == MovementType.GRAPPLE && to == MovementType.WALK) {
            return new VelocityVector(current.vx(), current.vy() * 0.5f, current.vz(), current.speed());
        }
        return current;
    }

    /**
     * 钩锁摆荡至最高点派生攻击的伤害系数。
     */
    public double swingAttackDamageMultiplier(float currentSpeed, float baseSpeed) {
        if (baseSpeed <= 0f) {
            baseSpeed = 8f;
        }
        double ratio = currentSpeed / baseSpeed;
        return SWING_ATTACK_SPEED_BONUS_BASE + Math.min(1.5, ratio * 0.5);
    }

    public SceneMoveCmd applyInherited(SceneMoveCmd cmd, VelocityVector inherited) {
        if (cmd == null || inherited == null) {
            return cmd;
        }
        return new SceneMoveCmd(
                cmd.targetX(), cmd.targetY(), cmd.targetZ(), cmd.speed(), cmd.timestamp(),
                cmd.movementType(), cmd.climbableMeshId(), cmd.moveFlags(),
                cmd.mountCreatureUid(), cmd.grappleNodeId(),
                cmd.wallNormalX(), cmd.wallNormalY(), cmd.wallNormalZ(),
                inherited.vx(), inherited.vy(), inherited.vz(),
                cmd.physicsStateHash(),
                cmd.actionState(), cmd.moveIntent(), cmd.cameraYaw(), cmd.clientDeltaMs(),
                cmd.surfaceType());
    }

    public Map<String, Object> transitionReport(
            long playerId, MovementType from, MovementType to, VelocityVector before, VelocityVector after) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("from", from == null ? "WALK" : from.name());
        body.put("to", to == null ? "WALK" : to.name());
        body.put("inheritedVelocity", Map.of(
                "vx", after == null ? 0f : after.vx(),
                "vy", after == null ? 0f : after.vy(),
                "vz", after == null ? 0f : after.vz(),
                "speed", after == null ? 0f : after.speed()));
        if (before != null && after != null) {
            body.put("retainRatio", before.speed() > 0 ? after.speed() / before.speed() : 0f);
        }
        return body;
    }
}
