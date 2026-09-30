package cn.itcast.demo.mymmorpg.world.explore;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 大地图标记：宝箱/挑战/谜题等统一已收集/未收集/已完成状态。
 */
@Service
public class MapMarkerService {

    public enum MarkerKind {
        CHEST, CHALLENGE, PUZZLE, WAYPOINT, LANDMARK
    }

    public enum MarkerStatus {
        UNCOLLECTED, COLLECTED, IN_PROGRESS, COMPLETED, LOCKED
    }

    public record MarkerDef(
            String markerId,
            MarkerKind kind,
            String regionId,
            String label,
            float x, float y, float z) {
    }

    private final CollectibleService collectibles;
    private final RegionProgressService regionProgress;
    private final ConcurrentHashMap<String, MarkerDef> markers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Set<String>> inProgressChallenges = new ConcurrentHashMap<>();

    public MapMarkerService(CollectibleService collectibles, RegionProgressService regionProgress) {
        this.collectibles = collectibles;
        this.regionProgress = regionProgress;
    }

    public MapMarkerService() {
        this(new CollectibleService(), new RegionProgressService());
    }

    public void register(MarkerDef def) {
        if (def != null && def.markerId() != null) {
            markers.put(def.markerId(), def);
        }
    }

    public void syncFromCollectibles() {
        for (CollectibleService.CollectibleDef def : collectibles.listAll()) {
            MarkerKind kind = def.tier() == CollectibleService.Tier.OCULUS
                    ? MarkerKind.CHEST : MarkerKind.CHEST;
            register(new MarkerDef(
                    def.collectibleId(), kind, String.valueOf(def.regionId()),
                    def.title(), def.x(), def.y(), def.z()));
        }
    }

    public Map<String, Object> markersForRegion(long playerId, String regionId) {
        Set<String> collected = collectibles.collectedIds(playerId);
        Set<String> inProgress = inProgressChallenges.getOrDefault(playerId, Set.of());
        Map<String, Object> rp = regionProgress.status(playerId, regionId);
        int percent = 0;
        if (rp.get("region_progress") instanceof Map<?, ?> m && m.get("percent") instanceof Number n) {
            percent = n.intValue();
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (MarkerDef m : markers.values()) {
            if (!m.regionId().equals(regionId) && !m.regionId().equals(String.valueOf(parseRegionNum(regionId)))) {
                continue;
            }
            MarkerStatus status = resolveStatus(m, collected, inProgress, percent);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("markerId", m.markerId());
            row.put("kind", m.kind().name());
            row.put("label", m.label());
            row.put("x", m.x());
            row.put("y", m.y());
            row.put("z", m.z());
            row.put("status", status.name());
            row.put("icon", iconFor(status, m.kind()));
            row.put("trackable", status == MarkerStatus.UNCOLLECTED || status == MarkerStatus.IN_PROGRESS);
            rows.add(row);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("regionId", regionId);
        body.put("explorationPercent", percent);
        body.put("markers", rows);
        body.put("summary", summarize(rows));
        return body;
    }

    public Map<String, Object> startChallenge(long playerId, String markerId) {
        MarkerDef m = markers.get(markerId);
        if (m == null || m.kind() != MarkerKind.CHALLENGE) {
            return Map.of("ok", false, "error", "not_a_challenge");
        }
        inProgressChallenges.computeIfAbsent(playerId, id -> ConcurrentHashMap.newKeySet()).add(markerId);
        return Map.of("ok", true, "markerId", markerId, "status", MarkerStatus.IN_PROGRESS.name());
    }

    public Map<String, Object> completeChallenge(long playerId, String markerId) {
        inProgressChallenges.computeIfAbsent(playerId, id -> ConcurrentHashMap.newKeySet()).remove(markerId);
        return Map.of("ok", true, "markerId", markerId, "status", MarkerStatus.COMPLETED.name(),
                "repeatRewardBlocked", true);
    }

    private MarkerStatus resolveStatus(
            MarkerDef m, Set<String> collected, Set<String> inProgress, int percent) {
        if (m.kind() == MarkerKind.CHEST) {
            return collected.contains(m.markerId()) ? MarkerStatus.COLLECTED : MarkerStatus.UNCOLLECTED;
        }
        if (m.kind() == MarkerKind.CHALLENGE) {
            if (collected.contains(m.markerId() + ":done")) {
                return MarkerStatus.COMPLETED;
            }
            if (inProgress.contains(m.markerId())) {
                return MarkerStatus.IN_PROGRESS;
            }
            return MarkerStatus.UNCOLLECTED;
        }
        if (m.kind() == MarkerKind.WAYPOINT && percent < 20) {
            return MarkerStatus.LOCKED;
        }
        return MarkerStatus.UNCOLLECTED;
    }

    private static String iconFor(MarkerStatus status, MarkerKind kind) {
        return switch (status) {
            case COLLECTED, COMPLETED -> "icon_" + kind.name().toLowerCase() + "_done";
            case IN_PROGRESS -> "icon_" + kind.name().toLowerCase() + "_active";
            case LOCKED -> "icon_" + kind.name().toLowerCase() + "_locked";
            case UNCOLLECTED -> "icon_" + kind.name().toLowerCase() + "_open";
        };
    }

    private static Map<String, Integer> summarize(List<Map<String, Object>> rows) {
        Map<String, Integer> sum = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            String st = String.valueOf(r.get("status"));
            sum.merge(st, 1, Integer::sum);
        }
        return sum;
    }

    private static int parseRegionNum(String regionId) {
        if ("wolf-camp-valley".equals(regionId)) {
            return 1;
        }
        try {
            return Integer.parseInt(regionId);
        } catch (Exception e) {
            return 1;
        }
    }
}
