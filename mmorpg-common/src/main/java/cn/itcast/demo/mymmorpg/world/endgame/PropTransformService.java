package cn.itcast.demo.mymmorpg.world.endgame;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 道具变形（Prop Hunt）：移动限速 + 周期 PROP_AUTH 签名防瞬移。
 */
@Service
public class PropTransformService {

    public static final long AUTH_INTERVAL_MS = 15_000L;
    public static final double MOVE_SPEED_MUL = 0.5;

    private final ConcurrentHashMap<Long, String> propOf = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> lastAuthMs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> lastAuthSig = new ConcurrentHashMap<>();

    public Map<String, Object> transform(long playerId, String propId) {
        propOf.put(playerId, propId == null ? "crate" : propId.trim());
        lastAuthMs.put(playerId, 0L);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("propId", propOf.get(playerId));
        body.put("moveSpeedMul", MOVE_SPEED_MUL);
        body.put("authIntervalMs", AUTH_INTERVAL_MS);
        return body;
    }

    public Map<String, Object> submitPropAuth(long playerId, String signature, long nowMs) {
        if (!propOf.containsKey(playerId)) {
            return Map.of("ok", false, "error", "not_transformed");
        }
        if (signature == null || signature.isBlank()) {
            return Map.of("ok", false, "error", "missing_PROP_AUTH");
        }
        long last = lastAuthMs.getOrDefault(playerId, 0L);
        if (last > 0 && nowMs - last > AUTH_INTERVAL_MS + 2_000L) {
            return Map.of("ok", false, "error", "auth_timeout", "cheatSuspect", true);
        }
        lastAuthMs.put(playerId, nowMs);
        lastAuthSig.put(playerId, signature);
        return Map.of("ok", true, "event", "PROP_AUTH", "playerId", playerId, "atMs", nowMs);
    }

    public Map<String, Object> validateMove(long playerId, double reportedSpeedMul) {
        if (!propOf.containsKey(playerId)) {
            return Map.of("ok", false, "error", "not_transformed");
        }
        boolean ok = reportedSpeedMul <= MOVE_SPEED_MUL + 0.05;
        return Map.of("ok", ok, "expectedMul", MOVE_SPEED_MUL,
                "reportedMul", reportedSpeedMul,
                "error", ok ? "" : "speed_exceeded");
    }

    public boolean isProp(long playerId) {
        return propOf.containsKey(playerId);
    }

    public String propId(long playerId) {
        return propOf.get(playerId);
    }
}
