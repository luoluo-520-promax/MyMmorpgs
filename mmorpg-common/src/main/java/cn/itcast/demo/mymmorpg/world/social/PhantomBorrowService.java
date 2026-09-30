package cn.itcast.demo.mymmorpg.world.social;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 幻影借用：解谜失败 3 次后可借用高探索度好友的操作影子自动完成一小段。
 */
@Service
public class PhantomBorrowService {

    public static final int FAIL_THRESHOLD = 3;

    private final ConcurrentHashMap<String, Integer> failCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> recordedPhantoms = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> mirrorSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Float> friendViewHeading = new ConcurrentHashMap<>();

    public static final float MIRROR_RADIUS_M = 50f;
    public static final int MIRROR_TOKEN_COST = 1;

    private static String key(long playerId, String puzzleId) {
        return playerId + ":" + (puzzleId == null ? "" : puzzleId.trim());
    }

    public Map<String, Object> recordPhantom(String puzzleId, long helperPlayerId, List<String> actionSequence) {
        if (puzzleId == null || puzzleId.isBlank() || actionSequence == null || actionSequence.isEmpty()) {
            return Map.of("ok", false, "error", "invalid_phantom");
        }
        recordedPhantoms.put(puzzleId, helperPlayerId + ":" + String.join(",", actionSequence));
        return Map.of("ok", true, "puzzleId", puzzleId, "helperPlayerId", helperPlayerId,
                "steps", actionSequence.size());
    }

    public Map<String, Object> onPuzzleFail(long playerId, String puzzleId) {
        int fails = failCounts.merge(key(playerId, puzzleId), 1, Integer::sum);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("failCount", fails);
        body.put("borrowAvailable", fails >= FAIL_THRESHOLD);
        if (fails >= FAIL_THRESHOLD) {
            body.put("event", "PHANTOM_BORROW_AVAILABLE");
            body.put("hint", "可借用好友操作影子自动完成一小段");
        }
        return body;
    }

    public Map<String, Object> borrowPhantom(long playerId, String puzzleId, long friendPlayerId) {
        int fails = failCounts.getOrDefault(key(playerId, puzzleId), 0);
        if (fails < FAIL_THRESHOLD) {
            return Map.of("ok", false, "error", "fail_count_insufficient");
        }
        String phantom = recordedPhantoms.get(puzzleId);
        if (phantom == null) {
            return Map.of("ok", false, "error", "no_phantom_recorded");
        }
        failCounts.remove(key(playerId, puzzleId));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("event", "PHANTOM_BORROW_EXECUTE");
        body.put("playerId", playerId);
        body.put("friendPlayerId", friendPlayerId);
        body.put("puzzleId", puzzleId);
        body.put("phantomData", phantom);
        body.put("autoCompleteSegment", true);
        return body;
    }

    /**
     * 实时镜像窥屏：消耗社交代币，看到好友屏幕中心 50m 内敌人/宝箱轮廓（仅方向提示）。
     */
    public Map<String, Object> startRealtimeMirror(
            long viewerId, long friendPlayerId, int socialTokens,
            float friendX, float friendY, float friendZ, float headingDeg) {
        if (viewerId <= 0 || friendPlayerId <= 0) {
            return Map.of("ok", false, "error", "invalid_args");
        }
        if (socialTokens < MIRROR_TOKEN_COST) {
            return Map.of("ok", false, "error", "need_social_token", "cost", MIRROR_TOKEN_COST);
        }
        mirrorSessions.put(viewerId, friendPlayerId);
        friendViewHeading.put(friendPlayerId, headingDeg);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("event", "PHANTOM_REALTIME_MIRROR");
        body.put("viewerId", viewerId);
        body.put("friendPlayerId", friendPlayerId);
        body.put("radiusM", MIRROR_RADIUS_M);
        body.put("revealCoordinates", false);
        body.put("outlineOnly", true);
        body.put("socialTokenCost", MIRROR_TOKEN_COST);
        body.put("socialTokensLeft", socialTokens - MIRROR_TOKEN_COST);
        body.put("clientHint", "隐身视角窥屏：仅显示方向轮廓，不揭示坐标");
        return body;
    }

    public Map<String, Object> mirrorOutline(
            long viewerId, float friendX, float friendY, float friendZ,
            List<Map<String, Object>> nearbyEntities) {
        Long friendId = mirrorSessions.get(viewerId);
        if (friendId == null) {
            return Map.of("ok", false, "error", "mirror_not_active");
        }
        float heading = friendViewHeading.getOrDefault(friendId, 0f);
        List<Map<String, Object>> outlines = new ArrayList<>();
        if (nearbyEntities != null) {
            for (Map<String, Object> ent : nearbyEntities) {
                float ex = asFloat(ent.get("x"));
                float ez = asFloat(ent.get("z"));
                float dist = distance(friendX, friendZ, ex, ez);
                if (dist > MIRROR_RADIUS_M) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("kind", ent.getOrDefault("kind", "UNKNOWN"));
                row.put("directionDeg", directionDeg(friendX, friendZ, ex, ez, heading));
                row.put("distanceBand", dist < 20f ? "NEAR" : dist < 35f ? "MID" : "FAR");
                outlines.add(row);
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("viewerId", viewerId);
        body.put("friendPlayerId", friendId);
        body.put("outlines", outlines);
        body.put("outlineCount", outlines.size());
        return body;
    }

    public void endMirror(long viewerId) {
        mirrorSessions.remove(viewerId);
    }

    private static float directionDeg(float fx, float fz, float tx, float tz, float heading) {
        float dx = tx - fx;
        float dz = tz - fz;
        float angle = (float) Math.toDegrees(Math.atan2(dx, dz));
        return ((angle - heading) % 360f + 360f) % 360f;
    }

    private static float distance(float x1, float z1, float x2, float z2) {
        float dx = x1 - x2;
        float dz = z1 - z2;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    private static float asFloat(Object v) {
        if (v instanceof Number n) {
            return n.floatValue();
        }
        try {
            return Float.parseFloat(String.valueOf(v));
        } catch (Exception e) {
            return 0f;
        }
    }
}
