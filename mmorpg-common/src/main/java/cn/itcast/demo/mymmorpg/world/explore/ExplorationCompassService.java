package cn.itcast.demo.mymmorpg.world.explore;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 探索罗盘/雷达：区域探索度达到阈值后探测剩余收集物、限时挑战等，避免「跟着视频跑」。
 */
@Service
public class ExplorationCompassService {

    public static final int UNLOCK_THRESHOLD_PERCENT = 70;
    public static final float DEFAULT_PROBE_RADIUS = 120f;

    public enum TargetKind {
        CHEST, OCULUS, CHALLENGE, PUZZLE, WAYPOINT
    }

    public enum DistanceBand {
        HIGH, MID, LOW
    }

    public record ProbeTarget(
            String targetId,
            TargetKind kind,
            String title,
            float x, float y, float z,
            float distanceM) {
    }

    public record TimedChallengeDef(
            String challengeId,
            String regionId,
            String title,
            float x, float y, float z,
            float radius) {
    }

    private final RegionProgressService regionProgress;
    private final CollectibleService collectibles;
    private final ConcurrentHashMap<String, TimedChallengeDef> timedChallenges = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Set<String>> completedChallenges = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Set<String>> solvedPuzzles = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> lastProbeMs = new ConcurrentHashMap<>();
    private static final long PROBE_COOLDOWN_MS = 3_000L;

    public ExplorationCompassService(RegionProgressService regionProgress, CollectibleService collectibles) {
        this.regionProgress = regionProgress;
        this.collectibles = collectibles;
    }

    public ExplorationCompassService() {
        this(new RegionProgressService(), new CollectibleService());
    }

    public void registerTimedChallenge(TimedChallengeDef def) {
        if (def != null && def.challengeId() != null) {
            timedChallenges.put(def.challengeId(), def);
        }
    }

    public Map<String, Object> markChallengeComplete(long playerId, String regionId, String challengeId) {
        completedChallenges.computeIfAbsent(playerId, id -> ConcurrentHashMap.newKeySet()).add(challengeId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("challengeId", challengeId);
        body.put("repeatBlocked", true);
        return body;
    }

    public void markPuzzleSolved(long playerId, String puzzleId) {
        if (playerId > 0 && puzzleId != null) {
            solvedPuzzles.computeIfAbsent(playerId, id -> ConcurrentHashMap.newKeySet()).add(puzzleId);
        }
    }

    public Map<String, Object> unlockStatus(long playerId, String regionId) {
        int percent = explorationPercent(playerId, regionId);
        boolean unlocked = percent >= UNLOCK_THRESHOLD_PERCENT;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("regionId", regionId);
        body.put("explorationPercent", percent);
        body.put("unlockThreshold", UNLOCK_THRESHOLD_PERCENT);
        body.put("compassUnlocked", unlocked);
        body.put("hint", unlocked
                ? "罗盘已解锁，可探测附近剩余目标"
                : "探索度达 " + UNLOCK_THRESHOLD_PERCENT + "% 后解锁罗盘");
        return body;
    }

    /**
     * 探测附近未收集目标；已完成的限时挑战不再返回。
     */
    public Map<String, Object> probe(
            long playerId, String regionId, float px, float py, float pz,
            float radius, long nowMs) {
        Map<String, Object> status = unlockStatus(playerId, regionId);
        if (!Boolean.TRUE.equals(status.get("compassUnlocked"))) {
            status.put("ok", false);
            status.put("error", "compass_locked");
            return status;
        }
        Long last = lastProbeMs.get(playerId);
        if (last != null && nowMs - last < PROBE_COOLDOWN_MS) {
            return Map.of("ok", false, "error", "probe_cooldown",
                    "retryAfterMs", PROBE_COOLDOWN_MS - (nowMs - last));
        }
        lastProbeMs.put(playerId, nowMs);

        float r = radius <= 0f ? DEFAULT_PROBE_RADIUS : radius;
        Set<String> collected = collectibles.collectedIds(playerId);
        Set<String> doneChallenges = completedChallenges.getOrDefault(playerId, Set.of());
        Set<String> donePuzzles = solvedPuzzles.getOrDefault(playerId, Set.of());

        List<ProbeTarget> targets = new ArrayList<>();
        int regionNum = parseRegionId(regionId);
        for (CollectibleService.CollectibleDef def : collectibles.listByRegion(regionNum)) {
            if (collected.contains(def.collectibleId())) {
                continue;
            }
            float dist = distance(px, py, pz, def.x(), def.y(), def.z());
            if (dist <= r) {
                TargetKind kind = def.tier() == CollectibleService.Tier.OCULUS
                        ? TargetKind.OCULUS : TargetKind.CHEST;
                targets.add(new ProbeTarget(
                        def.collectibleId(), kind, def.title(),
                        def.x(), def.y(), def.z(), dist));
            }
        }
        for (TimedChallengeDef ch : timedChallenges.values()) {
            if (!ch.regionId().equals(regionId) || doneChallenges.contains(ch.challengeId())) {
                continue;
            }
            float dist = distance(px, py, pz, ch.x(), ch.y(), ch.z());
            if (dist <= r) {
                targets.add(new ProbeTarget(
                        ch.challengeId(), TargetKind.CHALLENGE, ch.title(),
                        ch.x(), ch.y(), ch.z(), dist));
            }
        }
        for (String puzzleId : listPendingPuzzles(regionId, donePuzzles)) {
            // 演示谜题坐标由配置表提供；此处用占位
            targets.add(new ProbeTarget(puzzleId, TargetKind.PUZZLE, puzzleId,
                    px + 10f, py, pz + 10f, 14f));
        }
        targets.sort((a, b) -> Float.compare(a.distanceM(), b.distanceM()));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (ProbeTarget t : targets) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("targetId", t.targetId());
            row.put("kind", t.kind().name());
            row.put("title", t.title());
            row.put("x", t.x());
            row.put("y", t.y());
            row.put("z", t.z());
            row.put("distanceM", Math.round(t.distanceM() * 10f) / 10f);
            row.put("mapIcon", mapIcon(t.kind()));
            rows.add(row);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("regionId", regionId);
        body.put("probeRadius", r);
        body.put("remainingCount", rows.size());
        body.put("targets", rows);
        body.put("clientHint", rows.isEmpty() ? "附近暂无未收集目标" : "罗盘已标记 " + rows.size() + " 个剩余目标");
        return body;
    }

    /**
     * 异步回声定位：返回模糊距离区间而非精确坐标，保留「凭感觉找」的探索惊喜。
     */
    public Map<String, Object> echoProbe(
            long playerId, String regionId, float px, float py, float pz,
            float radius, long nowMs) {
        Map<String, Object> status = unlockStatus(playerId, regionId);
        if (!Boolean.TRUE.equals(status.get("compassUnlocked"))) {
            status.put("ok", false);
            status.put("error", "compass_locked");
            return status;
        }
        Long last = lastProbeMs.get(playerId);
        if (last != null && nowMs - last < PROBE_COOLDOWN_MS) {
            return Map.of("ok", false, "error", "probe_cooldown",
                    "retryAfterMs", PROBE_COOLDOWN_MS - (nowMs - last));
        }
        lastProbeMs.put(playerId, nowMs);

        float r = radius <= 0f ? DEFAULT_PROBE_RADIUS : radius;
        Set<String> collected = collectibles.collectedIds(playerId);
        int regionNum = parseRegionId(regionId);

        ProbeTarget nearest = null;
        float nearestDist = Float.MAX_VALUE;
        for (CollectibleService.CollectibleDef def : collectibles.listByRegion(regionNum)) {
            if (collected.contains(def.collectibleId())) {
                continue;
            }
            float dist = distance(px, py, pz, def.x(), def.y(), def.z());
            if (dist <= r && dist < nearestDist) {
                nearestDist = dist;
                TargetKind kind = def.tier() == CollectibleService.Tier.OCULUS
                        ? TargetKind.OCULUS : TargetKind.CHEST;
                nearest = new ProbeTarget(
                        def.collectibleId(), kind, def.title(),
                        def.x(), def.y(), def.z(), dist);
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("regionId", regionId);
        body.put("echoMode", true);
        body.put("revealCoordinates", false);
        if (nearest == null) {
            body.put("distanceBand", DistanceBand.LOW.name());
            body.put("distanceToNearest", "NONE");
            body.put("hint", "附近回声微弱，继续探索");
            return body;
        }
        DistanceBand band = toDistanceBand(nearestDist);
        body.put("targetKind", nearest.kind().name());
        body.put("targetTitle", nearest.title());
        body.put("distanceBand", band.name());
        body.put("distanceToNearest", band.name());
        body.put("approxDistanceM", bandRangeHint(band));
        body.put("directionHint", directionHint(px, pz, nearest.x(), nearest.z()));
        body.put("clientHint", "罗盘回声：" + bandRangeHint(band) + "处有未收集目标");
        return body;
    }

    private static DistanceBand toDistanceBand(float dist) {
        if (dist <= 40f) {
            return DistanceBand.HIGH;
        }
        if (dist <= 80f) {
            return DistanceBand.MID;
        }
        return DistanceBand.LOW;
    }

    private static String bandRangeHint(DistanceBand band) {
        return switch (band) {
            case HIGH -> "30-50m";
            case MID -> "50-100m";
            case LOW -> "100m+";
        };
    }

    private static String directionHint(float px, float pz, float tx, float tz) {
        float dx = tx - px;
        float dz = tz - pz;
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? "偏东" : "偏西";
        }
        return dz > 0 ? "偏北" : "偏南";
    }

    private static String mapIcon(TargetKind kind) {
        return switch (kind) {
            case CHEST -> "icon_chest_uncollected";
            case OCULUS -> "icon_oculus_uncollected";
            case CHALLENGE -> "icon_challenge_available";
            case PUZZLE -> "icon_puzzle_unsolved";
            case WAYPOINT -> "icon_waypoint_locked";
        };
    }

    private List<String> listPendingPuzzles(String regionId, Set<String> donePuzzles) {
        List<String> pending = new ArrayList<>();
        if ("wolf-camp-valley".equals(regionId) && !donePuzzles.contains("puzzle-demo-obelisk")) {
            pending.add("puzzle-demo-obelisk");
        }
        return pending;
    }

    private int explorationPercent(long playerId, String regionId) {
        Map<String, Object> st = regionProgress.status(playerId, regionId);
        if (!Boolean.TRUE.equals(st.get("ok"))) {
            return 0;
        }
        Object rp = st.get("region_progress");
        if (rp instanceof Map<?, ?> m && m.get("percent") instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    private static int parseRegionId(String regionId) {
        if ("wolf-camp-valley".equals(regionId)) {
            return 1;
        }
        try {
            return Integer.parseInt(regionId);
        } catch (Exception e) {
            return 1;
        }
    }

    private static float distance(float x1, float y1, float z1, float x2, float y2, float z2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        float dz = z1 - z2;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
