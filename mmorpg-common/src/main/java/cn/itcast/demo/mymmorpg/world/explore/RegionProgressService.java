package cn.itcast.demo.mymmorpg.world.explore;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 区域探索度面板：传送点 / 收集物 / 谜题 / 世界任务 → 阈值解锁区域声望奖励。
 */
@Service
public class RegionProgressService {

    public record RegionMeta(
            String regionId,
            String name,
            int waypointTotal,
            int collectibleTotal,
            int puzzleTotal,
            int worldQuestTotal,
            List<ReputationTier> reputationTiers) {

        public RegionMeta {
            waypointTotal = Math.max(1, waypointTotal);
            collectibleTotal = Math.max(1, collectibleTotal);
            puzzleTotal = Math.max(1, puzzleTotal);
            worldQuestTotal = Math.max(1, worldQuestTotal);
            reputationTiers = reputationTiers == null ? List.of() : List.copyOf(reputationTiers);
        }
    }

    public record ReputationTier(
            int thresholdPercent,
            String rewardId,
            String rewardLabel,
            List<Map<String, Object>> grantPlans) {
    }

    private final ConcurrentHashMap<String, RegionMeta> regions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, Set<String>>> waypoints =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, Set<String>>> collectibles =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, Set<String>>> puzzles =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, Set<String>>> quests =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, Set<Integer>>> claimedTiers =
            new ConcurrentHashMap<>();

    public void register(RegionMeta meta) {
        regions.put(meta.regionId(), meta);
    }

    public Map<String, Object> markWaypoint(long playerId, String regionId, String waypointId) {
        return mark(waypoints, playerId, regionId, waypointId, "waypoint");
    }

    public Map<String, Object> markCollectible(long playerId, String regionId, String collectibleId) {
        return mark(collectibles, playerId, regionId, collectibleId, "collectible");
    }

    public Map<String, Object> markPuzzle(long playerId, String regionId, String puzzleId) {
        return mark(puzzles, playerId, regionId, puzzleId, "puzzle");
    }

    public Map<String, Object> markWorldQuest(long playerId, String regionId, String questId) {
        return mark(quests, playerId, regionId, questId, "worldQuest");
    }

    public Map<String, Object> status(long playerId, String regionId) {
        RegionMeta meta = regions.get(regionId);
        if (meta == null) {
            return Map.of("ok", false, "error", "region_not_found");
        }
        int wp = sizeOf(waypoints, playerId, regionId);
        int col = sizeOf(collectibles, playerId, regionId);
        int pz = sizeOf(puzzles, playerId, regionId);
        int q = sizeOf(quests, playerId, regionId);
        double wpRate = rate(wp, meta.waypointTotal());
        double colRate = rate(col, meta.collectibleTotal());
        double pzRate = rate(pz, meta.puzzleTotal());
        double qRate = rate(q, meta.worldQuestTotal());
        double overall = (wpRate + colRate + pzRate + qRate) / 4.0 * 100.0;
        int percent = (int) Math.round(overall);

        List<Map<String, Object>> unlocked = new ArrayList<>();
        List<Map<String, Object>> pending = new ArrayList<>();
        Set<Integer> claimed = claimedTiers
                .computeIfAbsent(playerId, id -> new ConcurrentHashMap<>())
                .computeIfAbsent(regionId, id -> ConcurrentHashMap.newKeySet());
        for (ReputationTier tier : meta.reputationTiers()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("thresholdPercent", tier.thresholdPercent());
            row.put("rewardId", tier.rewardId());
            row.put("rewardLabel", tier.rewardLabel());
            row.put("grantPlans", tier.grantPlans());
            if (percent >= tier.thresholdPercent()) {
                if (!claimed.contains(tier.thresholdPercent())) {
                    claimed.add(tier.thresholdPercent());
                    row.put("justUnlocked", true);
                    unlocked.add(row);
                } else {
                    row.put("justUnlocked", false);
                    unlocked.add(row);
                }
            } else {
                pending.add(row);
            }
        }

        Map<String, Object> regionProgress = new LinkedHashMap<>();
        regionProgress.put("regionId", regionId);
        regionProgress.put("name", meta.name());
        regionProgress.put("percent", percent);
        regionProgress.put("waypointUnlockRate", round(wpRate));
        regionProgress.put("collectibleCompletion", round(colRate));
        regionProgress.put("puzzleSolveRate", round(pzRate));
        regionProgress.put("worldQuestCompletion", round(qRate));
        regionProgress.put("counts", Map.of(
                "waypoints", wp + "/" + meta.waypointTotal(),
                "collectibles", col + "/" + meta.collectibleTotal(),
                "puzzles", pz + "/" + meta.puzzleTotal(),
                "worldQuests", q + "/" + meta.worldQuestTotal()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("region_progress", regionProgress);
        body.put("reputationUnlocked", unlocked);
        body.put("reputationPending", pending);
        return body;
    }

    public List<Map<String, Object>> listPlayerRegions(long playerId) {
        return regions.keySet().stream().map(id -> status(playerId, id)).toList();
    }

    private Map<String, Object> mark(
            ConcurrentHashMap<Long, ConcurrentHashMap<String, Set<String>>> store,
            long playerId, String regionId, String itemId, String kind) {
        if (!regions.containsKey(regionId)) {
            return Map.of("ok", false, "error", "region_not_found");
        }
        store.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>())
                .computeIfAbsent(regionId, id -> ConcurrentHashMap.newKeySet())
                .add(itemId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("kind", kind);
        body.put("regionId", regionId);
        body.put("itemId", itemId);
        body.put("status", status(playerId, regionId));
        return body;
    }

    private static int sizeOf(
            ConcurrentHashMap<Long, ConcurrentHashMap<String, Set<String>>> store,
            long playerId, String regionId) {
        return store.getOrDefault(playerId, new ConcurrentHashMap<>())
                .getOrDefault(regionId, Set.of()).size();
    }

    private static double rate(int got, int total) {
        return Math.min(1.0, (double) got / (double) total);
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
