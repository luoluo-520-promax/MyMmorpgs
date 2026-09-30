package cn.itcast.demo.mymmorpg.world.traverse;

import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 手滑抖动滤波器：MOBILE 端 200ms 内连续 3 次方向截然相反（夹角 &gt; 150°）时丢弃后 2 次指令。
 */
@Service
public class InputConfidenceAnalyzer {

    public static final long DEBOUNCE_WINDOW_MS = 200L;
    public static final int BASE_INPUT_WINDOW_MS = 100;
    public static final int REVERSE_STREAK_THRESHOLD = 3;
    public static final float REVERSE_ANGLE_DEG = 150f;

    public record DirSample(float dx, float dz, long atMs) {
    }

    private final ConcurrentHashMap<Long, Deque<DirSample>> recentDirs = new ConcurrentHashMap<>();

    public Map<String, Object> analyze(
            long playerId, String deviceType,
            float fromX, float fromZ, float toX, float toZ, long nowMs) {
        return analyze(playerId, deviceType, fromX, fromZ, toX, toZ, nowMs, 16);
    }

    public static int effectiveWindowMs(int clientDeltaMs) {
        return BASE_INPUT_WINDOW_MS + Math.max(1, clientDeltaMs) * 2;
    }

    /**
     * @param deviceType PC / MOBILE / CONTROLLER
     * @param clientDeltaMs 本次输入距上一帧耗时，用于动态窗口缩放
     */
    public Map<String, Object> analyze(
            long playerId, String deviceType,
            float fromX, float fromZ, float toX, float toZ, long nowMs, int clientDeltaMs) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("inputDebounced", false);
        body.put("effectiveWindowMs", effectiveWindowMs(clientDeltaMs));
        body.put("clientDeltaMs", Math.max(1, clientDeltaMs));
        if (!"MOBILE".equalsIgnoreCase(deviceType)) {
            body.put("deviceType", deviceType == null ? "PC" : deviceType);
            return body;
        }
        float dx = toX - fromX;
        float dz = toZ - fromZ;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.01) {
            body.put("deviceType", "MOBILE");
            return body;
        }
        Deque<DirSample> window = recentDirs.computeIfAbsent(playerId, id -> new ArrayDeque<>());
        synchronized (window) {
            purgeOld(window, nowMs);
            int reverseStreak = countReverseStreak(window, dx, dz);
            window.addLast(new DirSample(dx, dz, nowMs));
            while (window.size() > 5) {
                window.pollFirst();
            }
            if (reverseStreak >= REVERSE_STREAK_THRESHOLD - 1) {
                body.put("inputDebounced", true);
                body.put("event", "INPUT_DEBOUNCED");
                body.put("reverseStreak", reverseStreak + 1);
                body.put("clientHint", "轻微震动摇杆并降低触屏灵敏度");
                body.put("dropCount", 2);
            }
            body.put("deviceType", "MOBILE");
            return body;
        }
    }

    private static void purgeOld(Deque<DirSample> window, long nowMs) {
        while (!window.isEmpty() && nowMs - window.peekFirst().atMs() > DEBOUNCE_WINDOW_MS) {
            window.pollFirst();
        }
    }

    private static int countReverseStreak(Deque<DirSample> window, float dx, float dz) {
        int streak = 0;
        DirSample prev = null;
        for (DirSample s : window) {
            if (prev != null && angleDeg(prev.dx(), prev.dz(), s.dx(), s.dz()) > REVERSE_ANGLE_DEG) {
                streak++;
            } else if (prev != null) {
                streak = 0;
            }
            prev = s;
        }
        if (prev != null && angleDeg(prev.dx(), prev.dz(), dx, dz) > REVERSE_ANGLE_DEG) {
            streak++;
        }
        return streak;
    }

    private static float angleDeg(float ax, float az, float bx, float bz) {
        double la = Math.sqrt(ax * ax + az * az);
        double lb = Math.sqrt(bx * bx + bz * bz);
        if (la < 0.001 || lb < 0.001) {
            return 0f;
        }
        double dot = (ax * bx + az * bz) / (la * lb);
        dot = Math.max(-1, Math.min(1, dot));
        return (float) Math.toDegrees(Math.acos(dot));
    }
}
