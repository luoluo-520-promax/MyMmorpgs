package cn.itcast.demo.mymmorpg.world.battle;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 辅助战斗：经典/简化模式、自动连招、伤害预警，兼顾硬核与休闲玩家。
 */
@Service
public class CombatAssistService {

    public enum AssistMode {
        CLASSIC, SIMPLIFIED
    }

    public enum DeviceType {
        PC, MOBILE, CONTROLLER
    }

    public record AssistSettings(
            AssistMode mode,
            DeviceType deviceType,
            boolean autoCombo,
            boolean damageWarning,
            boolean dodgeHint,
            boolean timeSlowOnDanger,
            boolean softLock) {

        public static AssistSettings defaults(AssistMode mode, DeviceType device) {
            DeviceType dev = device == null ? DeviceType.PC : device;
            if (mode == AssistMode.SIMPLIFIED) {
                return new AssistSettings(mode, dev, true, true, true, true, dev == DeviceType.MOBILE);
            }
            return new AssistSettings(mode, dev, false, false, false, false, dev == DeviceType.MOBILE);
        }
    }

    public static final int MOBILE_DODGE_WINDOW_BONUS_MS = 30;
    public static final float MOBILE_GRAPPLE_SNAP_BONUS = 0.15f;
    public static final float PC_LOOT_WEIGHT_BONUS = 0.05f;
    public static final float SOFT_LOCK_MAX_DEG = 15f;
    /** 移动端 BOSS 弱点判定放大 */
    public static final float MOBILE_HITBOX_SCALE = 1.15f;
    public static final float AIM_ASSIST_DAMPING = 0.35f;

    public record AimAssistVector(
            float suggestedTargetAngleDeg,
            float currentYawDeg,
            float damping,
            boolean active) {
    }

    private final HitFeedbackService hitFeedback;
    private final ConcurrentHashMap<Long, AssistSettings> settings = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, DeviceType> deviceTypes = new ConcurrentHashMap<>();

    public CombatAssistService(HitFeedbackService hitFeedback) {
        this.hitFeedback = hitFeedback == null ? new HitFeedbackService() : hitFeedback;
    }

    public CombatAssistService() {
        this(new HitFeedbackService());
    }

    public Map<String, Object> setMode(long playerId, AssistMode mode) {
        return setMode(playerId, mode, deviceTypes.getOrDefault(playerId, DeviceType.PC));
    }

    public Map<String, Object> registerDevice(long playerId, DeviceType deviceType) {
        DeviceType dev = deviceType == null ? DeviceType.PC : deviceType;
        deviceTypes.put(playerId, dev);
        AssistSettings cur = settings.get(playerId);
        AssistMode mode = cur == null ? AssistMode.CLASSIC : cur.mode();
        return setMode(playerId, mode, dev);
    }

    public Map<String, Object> setMode(long playerId, AssistMode mode, DeviceType deviceType) {
        AssistSettings s = AssistSettings.defaults(mode, deviceType);
        settings.put(playerId, s);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("mode", mode.name());
        body.put("deviceType", s.deviceType().name());
        body.put("autoCombo", s.autoCombo());
        body.put("damageWarning", s.damageWarning());
        body.put("dodgeHint", s.dodgeHint());
        body.put("timeSlowOnDanger", s.timeSlowOnDanger());
        body.put("softLock", s.softLock());
        if (s.deviceType() == DeviceType.MOBILE) {
            body.put("dodgeWindowBonusMs", MOBILE_DODGE_WINDOW_BONUS_MS);
            body.put("grappleSnapBonus", MOBILE_GRAPPLE_SNAP_BONUS);
        }
        if (s.deviceType() == DeviceType.PC) {
            body.put("lootWeightBonus", PC_LOOT_WEIGHT_BONUS);
        }
        body.put("clientHint", mode == AssistMode.SIMPLIFIED
                ? "简化模式：自动普攻 + 危险时时间缓速提示"
                : "经典模式：完整操作深度");
        return body;
    }

    public Map<String, Object> configure(long playerId, Map<String, Object> overrides) {
        AssistSettings base = settings.getOrDefault(playerId,
                AssistSettings.defaults(AssistMode.CLASSIC, deviceTypes.getOrDefault(playerId, DeviceType.PC)));
        AssistSettings next = new AssistSettings(
                overrides.containsKey("mode")
                        ? AssistMode.valueOf(String.valueOf(overrides.get("mode"))) : base.mode(),
                overrides.containsKey("deviceType")
                        ? DeviceType.valueOf(String.valueOf(overrides.get("deviceType"))) : base.deviceType(),
                Boolean.TRUE.equals(overrides.getOrDefault("autoCombo", base.autoCombo())),
                Boolean.TRUE.equals(overrides.getOrDefault("damageWarning", base.damageWarning())),
                Boolean.TRUE.equals(overrides.getOrDefault("dodgeHint", base.dodgeHint())),
                Boolean.TRUE.equals(overrides.getOrDefault("timeSlowOnDanger", base.timeSlowOnDanger())),
                Boolean.TRUE.equals(overrides.getOrDefault("softLock", base.softLock())));
        settings.put(playerId, next);
        return snapshot(playerId);
    }

    public static final float CAMERA_SOFT_LOCK_CONE_DEG = 60f;

    /**
     * 软锁敌：基于 cameraYaw 视野锥（±60°）筛选目标，修正攻击朝向不超过 15°。
     */
    public Map<String, Object> resolveSoftLock(
            long playerId, float aimYawDeg, float nearestEnemyYawDeg, float distanceM) {
        return resolveSoftLock(playerId, aimYawDeg, aimYawDeg, nearestEnemyYawDeg, distanceM);
    }

    public Map<String, Object> resolveSoftLock(
            long playerId, float cameraYawDeg, float aimYawDeg,
            float nearestEnemyYawDeg, float distanceM) {
        AssistSettings s = settings.getOrDefault(playerId,
                AssistSettings.defaults(AssistMode.CLASSIC, DeviceType.PC));
        float enemyDeltaFromCamera = normalizeAngle(nearestEnemyYawDeg - cameraYawDeg);
        if (Math.abs(enemyDeltaFromCamera) > CAMERA_SOFT_LOCK_CONE_DEG) {
            Map<String, Object> outOfCone = new LinkedHashMap<>();
            outOfCone.put("ok", true);
            outOfCone.put("playerId", playerId);
            outOfCone.put("softLockEnabled", s.softLock());
            outOfCone.put("correctedYaw", aimYawDeg);
            outOfCone.put("corrected", false);
            outOfCone.put("outOfCameraCone", true);
            return outOfCone;
        }
        float delta = normalizeAngle(nearestEnemyYawDeg - aimYawDeg);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("softLockEnabled", s.softLock() && s.deviceType() == DeviceType.MOBILE);
        body.put("cameraYawDeg", cameraYawDeg);
        body.put("cameraConeDeg", CAMERA_SOFT_LOCK_CONE_DEG);
        if (!s.softLock() || distanceM > 25f) {
            body.put("correctedYaw", aimYawDeg);
            body.put("corrected", false);
            return body;
        }
        float clamped = Math.max(-SOFT_LOCK_MAX_DEG, Math.min(SOFT_LOCK_MAX_DEG, delta));
        body.put("correctedYaw", aimYawDeg + clamped);
        body.put("corrected", Math.abs(clamped) > 0.1f);
        body.put("correctionDeg", clamped);
        return body;
    }

    /**
     * 智能软锁敌：服务端不下发强制瞄准，仅给出 SuggestedTargetAngle 供客户端阻尼吸附。
     */
    public Map<String, Object> predictiveAimAssist(
            long playerId, float aimYawDeg, float predictedEnemyYawDeg,
            float enemyVx, float enemyVz, float distanceM) {
        AssistSettings s = settings.getOrDefault(playerId,
                AssistSettings.defaults(AssistMode.CLASSIC, deviceTypes.getOrDefault(playerId, DeviceType.PC)));
        // 简单预判：按水平速度外推朝向
        float leadYaw = predictedEnemyYawDeg;
        if (distanceM > 0.1f && (Math.abs(enemyVx) + Math.abs(enemyVz)) > 0.1f) {
            leadYaw = (float) Math.toDegrees(Math.atan2(enemyVx, enemyVz));
        }
        float delta = normalizeAngle(leadYaw - aimYawDeg);
        boolean active = s.deviceType() != DeviceType.PC && distanceM <= 28f;
        float suggested = active
                ? aimYawDeg + Math.max(-SOFT_LOCK_MAX_DEG, Math.min(SOFT_LOCK_MAX_DEG, delta))
                : aimYawDeg;
        AimAssistVector vec = new AimAssistVector(suggested, aimYawDeg, AIM_ASSIST_DAMPING, active);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("deviceType", s.deviceType().name());
        body.put("suggestedTargetAngle", vec.suggestedTargetAngleDeg());
        body.put("aimAssistVector", Map.of(
                "suggestedTargetAngle", vec.suggestedTargetAngleDeg(),
                "currentYaw", vec.currentYawDeg(),
                "damping", vec.damping(),
                "active", vec.active()));
        body.put("serverDoesNotForceAim", true);
        body.put("clientDampingAdsorb", active);
        return body;
    }

    /** 按设备差异化 HitBox 半径缩放。 */
    public float hitboxScale(long playerId) {
        DeviceType dev = deviceTypes.getOrDefault(playerId,
                settings.getOrDefault(playerId,
                        AssistSettings.defaults(AssistMode.CLASSIC, DeviceType.PC)).deviceType());
        return dev == DeviceType.MOBILE ? MOBILE_HITBOX_SCALE : 1.0f;
    }

    private static float normalizeAngle(float deg) {
        float a = deg % 360f;
        if (a > 180f) {
            a -= 360f;
        }
        if (a < -180f) {
            a += 360f;
        }
        return a;
    }

    public Map<String, Object> resolveAttack(
            long playerId, long attackerId, long targetId,
            double incomingDamage, double poiseDamage, boolean heavyOrFall,
            float targetPoiseRemainRatio, float playerHpRatio) {
        AssistSettings s = settings.getOrDefault(playerId,
                AssistSettings.defaults(AssistMode.CLASSIC, deviceTypes.getOrDefault(playerId, DeviceType.PC)));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("mode", s.mode().name());

        if (s.autoCombo() && s.mode() == AssistMode.SIMPLIFIED) {
            body.put("autoComboChain", List.of("NORMAL_1", "NORMAL_2", "NORMAL_3", "FINISHER"));
            body.put("autoComboEnabled", true);
        }

        if (s.damageWarning() && incomingDamage > 0) {
            body.put("damageWarning", Map.of(
                    "incomingDamage", incomingDamage,
                    "severity", incomingDamage > 500 ? "LETHAL" : incomingDamage > 200 ? "HIGH" : "MEDIUM",
                    "dodgeRecommended", true));
        }

        if (s.timeSlowOnDanger() && playerHpRatio < 0.3f && incomingDamage > 100) {
            body.put("timeSlow", Map.of("scale", 0.3, "durationMs", 800, "reason", "low_hp_danger"));
        }

        if (s.dodgeHint() && incomingDamage > 150) {
            int dodgeWindow = 200;
            if (s.deviceType() == DeviceType.MOBILE) {
                dodgeWindow += MOBILE_DODGE_WINDOW_BONUS_MS;
            }
            body.put("dodgeWindowHint", Map.of("openMs", dodgeWindow, "perfectWindowMs", 80));
        }

        Map<String, Object> feedback = hitFeedback.buildFeedback(
                attackerId, targetId, poiseDamage, heavyOrFall, targetPoiseRemainRatio,
                HitFeedbackService.PredictionVerdict.CONFIRMED_HIT, null, hitboxScale(playerId));
        if (s.mode() == AssistMode.SIMPLIFIED) {
            int hitStop = ((Number) feedback.get("hitStopDurationMs")).intValue();
            feedback.put("hitStopDurationMs", Math.min(120, hitStop + 20));
            feedback.put("enhancedVfx", true);
        }
        body.put("hitFeedback", feedback);
        body.put("hitBoxScale", hitboxScale(playerId));
        return body;
    }

    public Map<String, Object> snapshot(long playerId) {
        AssistSettings s = settings.getOrDefault(playerId,
                AssistSettings.defaults(AssistMode.CLASSIC, deviceTypes.getOrDefault(playerId, DeviceType.PC)));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("mode", s.mode().name());
        body.put("deviceType", s.deviceType().name());
        body.put("autoCombo", s.autoCombo());
        body.put("damageWarning", s.damageWarning());
        body.put("dodgeHint", s.dodgeHint());
        body.put("timeSlowOnDanger", s.timeSlowOnDanger());
        body.put("softLock", s.softLock());
        body.put("hitBoxScale", hitboxScale(playerId));
        return body;
    }
}
