package cn.itcast.demo.mymmorpg.world.traverse;

import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 滑动窗口轨迹校验：不校验单点坐标，而是校验最近 N 帧移动向量夹角。
 * 允许 ±15° 轨迹偏差；偏差 &gt; 30° 且速度骤降时视为撞墙并强制拉回。
 */
@Service
public class MoveTrajectoryValidator {

    public static final int WINDOW_SIZE = 5;
    public static final float TOLERANCE_DEG = 15f;
    public static final float HARD_REJECT_DEG = 30f;
    public static final float SPEED_DROP_RATIO = 0.45f;
    public static final long ELASTIC_BUFFER_MS = 1_000L;

    public record MoveSample(float dx, float dy, float dz, float speed, long atMs) {
    }

    public record ValidationResult(
            boolean accepted,
            boolean softTolerance,
            boolean hardReject,
            float maxAngleDeg,
            String reason) {
    }

    private final ConcurrentHashMap<Long, Deque<MoveSample>> trajectories = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> elasticUntilMs = new ConcurrentHashMap<>();

    /** SOFT_ROLLBACK 后允许 1 秒弹性缓冲，避免强制瞬移。 */
    public void armElasticBuffer(long playerId, long nowMs) {
        elasticUntilMs.put(playerId, nowMs + ELASTIC_BUFFER_MS);
    }

    public boolean inElasticBuffer(long playerId, long nowMs) {
        Long until = elasticUntilMs.get(playerId);
        return until != null && until > nowMs;
    }

    public ValidationResult validate(
            long playerId, float fromX, float fromY, float fromZ,
            float toX, float toY, float toZ, float speed, long nowMs) {
        return validate(playerId, fromX, fromY, fromZ, toX, toY, toZ, speed, nowMs, 0f);
    }

    public ValidationResult validate(
            long playerId, float fromX, float fromY, float fromZ,
            float toX, float toY, float toZ, float speed, long nowMs, float cameraYawDeg) {
        float dx = toX - fromX;
        float dy = toY - fromY;
        float dz = toZ - fromZ;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 0.001) {
            return new ValidationResult(true, false, false, 0f, "stationary");
        }
        // 摄像机相对移动时放宽单帧夹角校验（后撤步等）
        float tolerance = Math.abs(cameraYawDeg) > 0.01f ? TOLERANCE_DEG + 5f : TOLERANCE_DEG;
        Deque<MoveSample> window = trajectories.computeIfAbsent(playerId, id -> new ArrayDeque<>());
        synchronized (window) {
            if (!window.isEmpty()) {
                MoveSample last = window.peekLast();
                float lastSpeed = last.speed();
                float maxAngle = maxAngleWithWindow(window, dx, dy, dz);
                boolean speedDrop = speed < lastSpeed * SPEED_DROP_RATIO && lastSpeed > 1f;
                if (maxAngle > HARD_REJECT_DEG && speedDrop) {
                    return new ValidationResult(false, false, true, maxAngle, "wall_collision");
                }
                if (maxAngle > tolerance) {
                    window.addLast(new MoveSample(dx, dy, dz, speed, nowMs));
                    trim(window);
                    return new ValidationResult(true, true, false, maxAngle, "within_soft_tolerance");
                }
            }
            window.addLast(new MoveSample(dx, dy, dz, speed, nowMs));
            trim(window);
            return new ValidationResult(true, false, false, 0f, "ok");
        }
    }

    public Map<String, Object> validateAsMap(
            long playerId, float fromX, float fromY, float fromZ,
            float toX, float toY, float toZ, float speed, long nowMs) {
        ValidationResult r = validate(playerId, fromX, fromY, fromZ, toX, toY, toZ, speed, nowMs);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("trajectoryOk", r.accepted());
        body.put("softTolerance", r.softTolerance());
        body.put("hardReject", r.hardReject());
        body.put("maxAngleDeg", Math.round(r.maxAngleDeg() * 10f) / 10f);
        body.put("reason", r.reason());
        body.put("windowSize", WINDOW_SIZE);
        body.put("toleranceDeg", TOLERANCE_DEG);
        return body;
    }

    public void reset(long playerId) {
        trajectories.remove(playerId);
    }

    private static float maxAngleWithWindow(Deque<MoveSample> window, float dx, float dy, float dz) {
        float max = 0f;
        for (MoveSample s : window) {
            float angle = angleDeg(s.dx(), s.dy(), s.dz(), dx, dy, dz);
            if (angle > max) {
                max = angle;
            }
        }
        return max;
    }

    private static float angleDeg(float ax, float ay, float az, float bx, float by, float bz) {
        double la = Math.sqrt(ax * ax + ay * ay + az * az);
        double lb = Math.sqrt(bx * bx + by * by + bz * bz);
        if (la < 0.001 || lb < 0.001) {
            return 0f;
        }
        double dot = (ax * bx + ay * by + az * bz) / (la * lb);
        dot = Math.max(-1, Math.min(1, dot));
        return (float) Math.toDegrees(Math.acos(dot));
    }

    private static void trim(Deque<MoveSample> window) {
        while (window.size() > WINDOW_SIZE) {
            window.pollFirst();
        }
    }
}
