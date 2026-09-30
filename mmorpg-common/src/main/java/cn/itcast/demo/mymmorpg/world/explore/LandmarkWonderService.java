package cn.itcast.demo.mymmorpg.world.explore;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 功能奇观运行时：校验移动手段 / 入口条件 → 推进迷宫层 → 解锁与发奖；
 * 并提供视距内 LOD 高亮轮廓标记。
 */
@Service
public class LandmarkWonderService {

    public record WonderEntryCondition(
            String landmarkId,
            /** 需先完成的谜题 Id 列表 */
            List<String> requiredPuzzleIds,
            int minExploreScore) {

        public WonderEntryCondition {
            requiredPuzzleIds = requiredPuzzleIds == null ? List.of() : List.copyOf(requiredPuzzleIds);
            minExploreScore = Math.max(0, minExploreScore);
        }
    }

    private final ConcurrentHashMap<String, LandmarkWonder> landmarks = new ConcurrentHashMap<>();
    /** playerId → landmarkId → 当前层（0=未开始） */
    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, Integer>> progress =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Set<String>> cleared = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, WonderEntryCondition> entryConditions =
            new ConcurrentHashMap<>();
    /** playerId → 已解谜题，供入口条件校验 */
    private final ConcurrentHashMap<Long, Set<String>> playerSolvedPuzzles = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Integer> playerExploreScore = new ConcurrentHashMap<>();

    public void register(LandmarkWonder wonder) {
        landmarks.put(wonder.landmarkId(), wonder);
    }

    public void setEntryCondition(WonderEntryCondition condition) {
        entryConditions.put(condition.landmarkId(), condition);
    }

    public void markPuzzleSolved(long playerId, String puzzleId) {
        playerSolvedPuzzles.computeIfAbsent(playerId, id -> ConcurrentHashMap.newKeySet()).add(puzzleId);
    }

    public void setExploreScore(long playerId, int score) {
        playerExploreScore.put(playerId, Math.max(0, score));
    }

    public Map<String, Object> enter(long playerId, String landmarkId, Set<String> unlockedModes) {
        Map<String, Object> body = new LinkedHashMap<>();
        LandmarkWonder wonder = landmarks.get(landmarkId);
        if (wonder == null) {
            return Map.of("ok", false, "error", "landmark_not_found");
        }
        WonderEntryCondition cond = entryConditions.get(landmarkId);
        if (cond != null) {
            Set<String> solved = playerSolvedPuzzles.getOrDefault(playerId, Set.of());
            List<String> missing = new ArrayList<>();
            for (String need : cond.requiredPuzzleIds()) {
                if (!solved.contains(need)) {
                    missing.add(need);
                }
            }
            if (!missing.isEmpty()) {
                body.put("ok", false);
                body.put("error", "entry_condition_puzzles");
                body.put("missingPuzzles", missing);
                body.put("hint", "看见→想办法→解锁：先完成前置谜题");
                return body;
            }
            int score = playerExploreScore.getOrDefault(playerId, 0);
            if (score < cond.minExploreScore()) {
                body.put("ok", false);
                body.put("error", "entry_condition_score");
                body.put("needScore", cond.minExploreScore());
                body.put("score", score);
                return body;
            }
        }
        Set<String> modes = unlockedModes == null ? Set.of() : unlockedModes;
        for (String need : wonder.requiredTraverseModes()) {
            if (!modes.contains(need)) {
                body.put("ok", false);
                body.put("error", "missing_traverse_mode");
                body.put("required", need);
                body.put("unlocked", List.copyOf(modes));
                return body;
            }
        }
        progress.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>())
                .putIfAbsent(landmarkId, 1);
        body.put("ok", true);
        body.put("landmarkId", landmarkId);
        body.put("name", wonder.name());
        body.put("layer", progress.get(playerId).get(landmarkId));
        body.put("mazeLayers", wonder.mazeLayers());
        body.put("requiredModes", wonder.requiredTraverseModes());
        return body;
    }

    /**
     * 视距内 LOD 标记：远处呈现高亮轮廓（类似神庙）。
     */
    public Map<String, Object> lodMarkers(float px, float py, float pz, float viewDistance) {
        float vd = viewDistance <= 0f ? 400f : viewDistance;
        List<Map<String, Object>> markers = new ArrayList<>();
        for (LandmarkWonder w : landmarks.values()) {
            float dx = px - w.x();
            float dy = py - w.y();
            float dz = pz - w.z();
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > vd) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("landmarkId", w.landmarkId());
            m.put("name", w.name());
            m.put("x", w.x());
            m.put("y", w.y());
            m.put("z", w.z());
            m.put("distance", Math.round(dist * 10.0) / 10.0);
            m.put("lod", dist > vd * 0.6f ? "SILHOUETTE" : dist > vd * 0.3f ? "OUTLINE" : "FULL");
            m.put("highlight", true);
            m.put("hasEntryCondition", entryConditions.containsKey(w.landmarkId()));
            markers.add(m);
        }
        return Map.of("ok", true, "markers", markers, "count", markers.size());
    }

    /**
     * 完成一层攀爬/解谜；全部层完成则发放奖励并返回 unlockIds。
     */
    public Map<String, Object> advanceLayer(long playerId, String landmarkId) {
        LandmarkWonder wonder = landmarks.get(landmarkId);
        if (wonder == null) {
            return Map.of("ok", false, "error", "landmark_not_found");
        }
        if (cleared.getOrDefault(playerId, Set.of()).contains(landmarkId)) {
            return Map.of("ok", false, "error", "already_cleared", "landmarkId", landmarkId);
        }
        ConcurrentHashMap<String, Integer> layers =
                progress.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>());
        int current = layers.getOrDefault(landmarkId, 0);
        if (current <= 0) {
            return Map.of("ok", false, "error", "not_entered");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        if (current >= wonder.mazeLayers()) {
            cleared.computeIfAbsent(playerId, id -> ConcurrentHashMap.newKeySet()).add(landmarkId);
            body.put("ok", true);
            body.put("cleared", true);
            body.put("landmarkId", landmarkId);
            body.put("grantPlans", wonder.clearRewards());
            body.put("unlockIds", wonder.unlockIds());
            body.put("puzzleBitIndex", wonder.puzzleBitIndex());
            body.put("idempotencyKey", "wonder:" + playerId + ":" + landmarkId);
            return body;
        }
        layers.put(landmarkId, current + 1);
        body.put("ok", true);
        body.put("cleared", false);
        body.put("landmarkId", landmarkId);
        body.put("layer", current + 1);
        body.put("mazeLayers", wonder.mazeLayers());
        return body;
    }

    public Map<String, Object> status(long playerId, String landmarkId) {
        LandmarkWonder wonder = landmarks.get(landmarkId);
        if (wonder == null) {
            return Map.of("ok", false, "error", "landmark_not_found");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("landmark", toView(wonder));
        body.put("layer", progress.getOrDefault(playerId, new ConcurrentHashMap<>())
                .getOrDefault(landmarkId, 0));
        body.put("cleared", cleared.getOrDefault(playerId, Set.of()).contains(landmarkId));
        return body;
    }

    public List<Map<String, Object>> list() {
        return landmarks.values().stream().map(this::toView).toList();
    }

    private Map<String, Object> toView(LandmarkWonder w) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("landmarkId", w.landmarkId());
        m.put("name", w.name());
        m.put("worldId", w.worldId());
        m.put("sceneId", w.sceneId());
        m.put("x", w.x());
        m.put("y", w.y());
        m.put("z", w.z());
        m.put("mazeLayers", w.mazeLayers());
        m.put("requiredTraverseModes", w.requiredTraverseModes());
        m.put("unlockIds", w.unlockIds());
        return m;
    }
}
