package cn.itcast.demo.mymmorpg.sync;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端预测 + 服务端校验：按动作类型使用差异化阈值（位置偏差、速度倍率、时间偏移）。
 */
@Component
public class MovementPredictionValidator {

    public enum ActionType {
        MOVE, DASH, SKILL_DISPLACE, TELEPORT_SKILL,
        /** 滑翔伞 */
        GLIDE,
        /** 攀爬 */
        CLIMB,
        /** 游泳 */
        SWIM,
        /** 钩锁牵引 */
        HOOK,
        /** 载具 */
        VEHICLE
    }

    public record ActionThreshold(double positionDelta, double speedMultiplier, long timeSkewMs) {
    }

    public record ValidationResult(
            boolean accepted,
            boolean corrected,
            String reason,
            boolean rollbackRequired) {

        public static ValidationResult ok() {
            return new ValidationResult(true, false, "", false);
        }

        public static ValidationResult corrected(String reason) {
            return new ValidationResult(true, true, reason, false);
        }

        public static ValidationResult reject(String reason, boolean rollback) {
            return new ValidationResult(false, false, reason, rollback);
        }
    }

    private final EnumMap<ActionType, ActionThreshold> thresholds = new EnumMap<>(ActionType.class);
    private final ConcurrentHashMap<Long, float[]> lastAccepted = new ConcurrentHashMap<>();

    public MovementPredictionValidator() {
        // MOVE 紧、DASH 松、SKILL_DISPLACE 中、TELEPORT_SKILL 最宽；探索移动介于其间
        thresholds.put(ActionType.MOVE, new ActionThreshold(1.5, 1.25, 120L));
        thresholds.put(ActionType.DASH, new ActionThreshold(8.0, 3.5, 220L));
        thresholds.put(ActionType.SKILL_DISPLACE, new ActionThreshold(5.0, 2.2, 180L));
        thresholds.put(ActionType.TELEPORT_SKILL, new ActionThreshold(48.0, 12.0, 500L));
        thresholds.put(ActionType.GLIDE, new ActionThreshold(12.0, 2.8, 280L));
        thresholds.put(ActionType.CLIMB, new ActionThreshold(4.0, 1.6, 200L));
        thresholds.put(ActionType.SWIM, new ActionThreshold(3.5, 1.5, 180L));
        thresholds.put(ActionType.HOOK, new ActionThreshold(28.0, 6.0, 350L));
        thresholds.put(ActionType.VEHICLE, new ActionThreshold(16.0, 4.0, 300L));
    }

    public void configureThreshold(ActionType type, ActionThreshold threshold) {
        if (type == null || threshold == null) {
            return;
        }
        thresholds.put(type, new ActionThreshold(
                Math.max(0.1, threshold.positionDelta()),
                Math.max(1.0, threshold.speedMultiplier()),
                Math.max(0L, threshold.timeSkewMs())));
    }

    public ActionThreshold thresholdOf(ActionType type) {
        return thresholds.get(type == null ? ActionType.MOVE : type);
    }

    public Map<ActionType, ActionThreshold> thresholds() {
        return Map.copyOf(thresholds);
    }

    /**
     * 校验客户端预测坐标相对权威坐标是否在动作阈值内。
     */
    public ValidationResult validate(
            long playerId,
            ActionType action,
            float predictedX, float predictedY, float predictedZ,
            float authoritativeX, float authoritativeY, float authoritativeZ,
            long clientTs, long serverTs) {
        ActionType type = action == null ? ActionType.MOVE : action;
        ActionThreshold th = thresholds.getOrDefault(type, thresholds.get(ActionType.MOVE));

        long skew = Math.abs(serverTs - clientTs);
        if (skew > th.timeSkewMs()) {
            return ValidationResult.reject("time_skew", true);
        }

        float dx = predictedX - authoritativeX;
        float dy = predictedY - authoritativeY;
        float dz = predictedZ - authoritativeZ;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        float[] prev = lastAccepted.get(playerId);
        if (prev != null) {
            float pdx = predictedX - prev[0];
            float pdy = predictedY - prev[1];
            float pdz = predictedZ - prev[2];
            double step = Math.sqrt(pdx * pdx + pdy * pdy + pdz * pdz);
            double dtSec = Math.max(0.016, Math.abs(serverTs - (long) prev[3]) / 1000.0);
            double impliedSpeed = step / dtSec;
            double maxSpeed = baseSpeed(type) * th.speedMultiplier();
            if (impliedSpeed > maxSpeed) {
                return ValidationResult.reject("speed_multiplier", true);
            }
        }

        if (dist <= th.positionDelta()) {
            accept(playerId, predictedX, predictedY, predictedZ, serverTs);
            return ValidationResult.ok();
        }

        // 中等偏差：接受权威位并标记 corrected；过大则回滚
        double hardLimit = th.positionDelta()
                * (type == ActionType.TELEPORT_SKILL || type == ActionType.HOOK ? 2.5 : 2.0);
        if (dist <= hardLimit) {
            accept(playerId, authoritativeX, authoritativeY, authoritativeZ, serverTs);
            return ValidationResult.corrected("position_corrected");
        }
        return ValidationResult.reject("position_delta", true);
    }

    private static double baseSpeed(ActionType type) {
        return switch (type) {
            case MOVE -> 8.0;
            case DASH -> 18.0;
            case SKILL_DISPLACE -> 14.0;
            case TELEPORT_SKILL -> 40.0;
            case GLIDE -> 14.0;
            case CLIMB -> 5.0;
            case SWIM -> 4.5;
            case HOOK -> 32.0;
            case VEHICLE -> 22.0;
        };
    }

    private void accept(long playerId, float x, float y, float z, long serverTs) {
        lastAccepted.put(playerId, new float[]{x, y, z, (float) serverTs});
    }

    public void clearPlayer(long playerId) {
        lastAccepted.remove(playerId);
    }
}
