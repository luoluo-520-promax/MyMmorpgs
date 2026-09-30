package cn.itcast.demo.mymmorpg.ai.vision;

import cn.itcast.demo.mymmorpg.world.explore.MapMarkerService;
import cn.itcast.demo.mymmorpg.world.explore.RegionProgressService;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * P15 · 截图到路点翻译器：玩家主动「截图求助」上传脱敏低分图像元数据，
 * 视觉特征与 {@link RegionProgressService} 坐标对齐，生成方向箭头指引。
 * <p>
 * 图片会话最多保留 5 分钟；不落盘持久化原图。
 */
public final class ScreenWaypointTranslator {

    public static final long IMAGE_TTL_MS = 5L * 60 * 1000;

    public record LandmarkHit(String landmarkId, String label, float confidence, float x, float y, float z) {
    }

    public record WaypointGuide(
            String direction,
            float distanceM,
            String description,
            float targetX,
            float targetY,
            float targetZ,
            String markerId) {
    }

    private final ConcurrentHashMap<String, ImageSession> sessions = new ConcurrentHashMap<>();
    private final RegionProgressService regionProgress;
    private final MapMarkerService mapMarkers;
    private final Map<String, LandmarkHit> landmarkCatalog = new ConcurrentHashMap<>();

    public ScreenWaypointTranslator() {
        this(new RegionProgressService(), new MapMarkerService());
        seedDefaultLandmarks();
    }

    public ScreenWaypointTranslator(RegionProgressService regionProgress, MapMarkerService mapMarkers) {
        this.regionProgress = regionProgress == null ? new RegionProgressService() : regionProgress;
        this.mapMarkers = mapMarkers == null ? new MapMarkerService() : mapMarkers;
        seedDefaultLandmarks();
    }

    public void registerLandmark(LandmarkHit hit) {
        if (hit != null && hit.landmarkId() != null) {
            landmarkCatalog.put(hit.landmarkId().toLowerCase(Locale.ROOT), hit);
            mapMarkers.register(new MapMarkerService.MarkerDef(
                    "wp-" + hit.landmarkId(), MapMarkerService.MarkerKind.WAYPOINT,
                    "1", hit.label(), hit.x(), hit.y(), hit.z()));
        }
    }

    /**
     * 上传脱敏截图元数据（不接收人脸/UI 边框像素流，仅特征标签）。
     */
    public Map<String, Object> uploadScreenshot(long playerId, String regionId,
                                                List<String> visualTags, long nowMs) {
        purgeExpired(nowMs);
        String sessionId = "scr-" + UUID.randomUUID();
        List<String> tags = visualTags == null ? List.of() : List.copyOf(visualTags);
        ImageSession session = new ImageSession(sessionId, playerId,
                regionId == null ? "1" : regionId, tags, nowMs + IMAGE_TTL_MS);
        sessions.put(sessionId, session);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("sessionId", sessionId);
        out.put("playerId", playerId);
        out.put("regionId", session.regionId);
        out.put("expiresAtMs", session.expiresAtMs);
        out.put("ttlMs", IMAGE_TTL_MS);
        out.put("privacy", Map.of("facesRedacted", true, "uiChromeStripped", true, "persist", false));
        out.put("trigger", "player_opt_in_screenshot_help");
        return out;
    }

    /**
     * 轻量视觉层：用标签匹配地标目录（可替换为 ViT / 云端多模态 API）。
     */
    public Map<String, Object> translate(String sessionId, float playerX, float playerY, float playerZ,
                                         long nowMs) {
        purgeExpired(nowMs);
        ImageSession session = sessions.get(sessionId == null ? "" : sessionId);
        if (session == null) {
            return Map.of("ok", false, "error", "session_not_found_or_expired");
        }
        if (nowMs > session.expiresAtMs) {
            sessions.remove(sessionId);
            return Map.of("ok", false, "error", "session_expired");
        }

        LandmarkHit best = matchLandmark(session.tags);
        if (best == null) {
            return Map.of("ok", false, "error", "no_landmark_recognized",
                    "hint", "请对准巨石、雕像或任务旗帜后再试");
        }

        float dx = best.x() - playerX;
        float dz = best.z() - playerZ;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        String direction = compass(dx, dz);
        String desc = String.format(Locale.ROOT, "前方 %.0fm %s下", dist, best.label());
        String markerId = "wp-" + best.landmarkId();

        Map<String, Object> regionStatus = regionProgress.status(session.playerId, session.regionId);

        WaypointGuide guide = new WaypointGuide(direction, dist, desc, best.x(), best.y(), best.z(), markerId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("sessionId", sessionId);
        out.put("recognized", Map.of(
                "landmarkId", best.landmarkId(),
                "label", best.label(),
                "confidence", best.confidence()));
        out.put("guide", Map.of(
                "direction", guide.direction(),
                "arrow", guide.direction(),
                "distanceM", Math.round(guide.distanceM() * 10) / 10.0,
                "description", guide.description(),
                "target", Map.of("x", guide.targetX(), "y", guide.targetY(), "z", guide.targetZ()),
                "markerId", guide.markerId()));
        out.put("regionProgress", regionStatus);
        out.put("expiresAtMs", session.expiresAtMs);
        out.put("pipeline", "B");
        return out;
    }

    public Map<String, Object> help(long playerId, String regionId, List<String> visualTags,
                                    float playerX, float playerY, float playerZ, long nowMs) {
        Map<String, Object> up = uploadScreenshot(playerId, regionId, visualTags, nowMs);
        if (!Boolean.TRUE.equals(up.get("ok"))) {
            return up;
        }
        String sessionId = String.valueOf(up.get("sessionId"));
        Map<String, Object> guide = translate(sessionId, playerX, playerY, playerZ, nowMs);
        Map<String, Object> out = new LinkedHashMap<>(guide);
        out.put("upload", up);
        return out;
    }

    public int activeSessions() {
        return sessions.size();
    }

    private LandmarkHit matchLandmark(List<String> tags) {
        LandmarkHit best = null;
        float bestScore = 0f;
        for (String tag : tags) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            String key = tag.trim().toLowerCase(Locale.ROOT);
            for (Map.Entry<String, LandmarkHit> e : landmarkCatalog.entrySet()) {
                if (key.contains(e.getKey()) || e.getKey().contains(key)
                        || e.getValue().label().toLowerCase(Locale.ROOT).contains(key)) {
                    float score = e.getValue().confidence();
                    if (score > bestScore) {
                        bestScore = score;
                        best = e.getValue();
                    }
                }
            }
        }
        return best;
    }

    private void purgeExpired(long nowMs) {
        Iterator<Map.Entry<String, ImageSession>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ImageSession> e = it.next();
            if (nowMs > e.getValue().expiresAtMs) {
                it.remove();
            }
        }
    }

    private void seedDefaultLandmarks() {
        registerLandmark(new LandmarkHit("boulder", "巨石", 0.86f, 220f, 3f, 180f));
        registerLandmark(new LandmarkHit("statue", "七天神像", 0.92f, 100f, 5f, 100f));
        registerLandmark(new LandmarkHit("flag", "任务旗帜", 0.8f, 300f, 1f, 250f));
        registerLandmark(new LandmarkHit("ruin_gate", "遗迹石门", 0.84f, 400f, 8f, 120f));
    }

    private static String compass(float dx, float dz) {
        double angle = Math.toDegrees(Math.atan2(dx, dz));
        if (angle < 0) {
            angle += 360;
        }
        if (angle >= 337.5 || angle < 22.5) {
            return "N";
        }
        if (angle < 67.5) {
            return "NE";
        }
        if (angle < 112.5) {
            return "E";
        }
        if (angle < 157.5) {
            return "SE";
        }
        if (angle < 202.5) {
            return "S";
        }
        if (angle < 247.5) {
            return "SW";
        }
        if (angle < 292.5) {
            return "W";
        }
        return "NW";
    }

    private static final class ImageSession {
        final String sessionId;
        final long playerId;
        final String regionId;
        final List<String> tags;
        final long expiresAtMs;

        ImageSession(String sessionId, long playerId, String regionId, List<String> tags, long expiresAtMs) {
            this.sessionId = sessionId;
            this.playerId = playerId;
            this.regionId = regionId;
            this.tags = tags;
            this.expiresAtMs = expiresAtMs;
        }
    }
}
