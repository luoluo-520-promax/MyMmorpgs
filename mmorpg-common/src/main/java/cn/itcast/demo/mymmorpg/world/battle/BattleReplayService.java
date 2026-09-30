package cn.itcast.demo.mymmorpg.world.battle;

import cn.itcast.demo.mymmorpg.persistence.HistoricalDataRetention;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 战斗指令流重播：按 tick 写入 List，限制 5000 条；支持 2x/4x 加速回放校验。
 */
@Service
public class BattleReplayService {

    public static final int MAX_ENTRIES = 5000;
    public static final long ARCHIVE_AFTER_MS = 24L * 60 * 60 * 1000;
    public static final long HOT_RETENTION_MS = HistoricalDataRetention.HOT_REPLAY_MS;

    public record ReplayEntry(int tick, long tsMs, String actionType, Map<String, Object> payload) {
        public ReplayEntry {
            actionType = actionType == null ? "UNKNOWN" : actionType.trim().toUpperCase();
            payload = payload == null ? Map.of() : Map.copyOf(payload);
        }
    }

    private final ConcurrentHashMap<String, List<ReplayEntry>> buffers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> startedAt = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> archivedUri = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> randomSeeds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PatchBaselineContext> baselines = new ConcurrentHashMap<>();
    private final HistoricalConfigSnapshotStore historicalConfigs;
    private final HistoricalDataRetention retention = new HistoricalDataRetention();

    public BattleReplayService(HistoricalConfigSnapshotStore historicalConfigs) {
        this.historicalConfigs = historicalConfigs == null
                ? new HistoricalConfigSnapshotStore() : historicalConfigs;
    }

    public BattleReplayService() {
        this(new HistoricalConfigSnapshotStore());
    }

    public Map<String, Object> start(String battleId, long seed, long nowMs) {
        return start(battleId, seed, nowMs, null, 0L);
    }

    /** 存储回放时强制记录 config_version 与 server_epoch。 */
    public Map<String, Object> start(
            String battleId, long seed, long nowMs, PatchBaselineContext baseline) {
        return start(battleId, seed, nowMs, baseline, baseline == null ? 0L : baseline.serverEpoch());
    }

    private Map<String, Object> start(
            String battleId, long seed, long nowMs, PatchBaselineContext baseline, long serverEpoch) {
        String id = battleId == null || battleId.isBlank()
                ? "replay-" + UUID.randomUUID() : battleId.trim();
        buffers.put(id, new ArrayList<>());
        startedAt.put(id, nowMs);
        randomSeeds.put(id, seed);
        if (baseline != null) {
            baselines.put(id, baseline);
            historicalConfigs.save(
                    baseline.configVersion(),
                    baseline.gitCommitSha(),
                    baseline.serverEpoch(),
                    Map.of("replayId", id, "seed", seed));
        }
        retention.markHot("replay:" + id, nowMs);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("replayId", id);
        body.put("seed", seed);
        body.put("maxEntries", MAX_ENTRIES);
        if (baseline != null) {
            body.put("configVersion", baseline.configVersion());
            body.put("gitCommitSha", baseline.gitCommitSha());
            body.put("serverEpoch", baseline.serverEpoch());
        }
        return body;
    }

    public Map<String, Object> append(String replayId, int tick, long tsMs,
                                      String actionType, Map<String, Object> payload) {
        List<ReplayEntry> list = buffers.get(replayId);
        if (list == null) {
            return Map.of("ok", false, "error", "replay_not_found");
        }
        synchronized (list) {
            if (list.size() >= MAX_ENTRIES) {
                return Map.of("ok", false, "error", "truncated", "size", list.size());
            }
            list.add(new ReplayEntry(tick, tsMs, actionType, payload));
        }
        return Map.of("ok", true, "replayId", replayId, "size", list.size(), "tick", tick);
    }

    /**
     * 按加速倍率回放；可附带期望伤害区间做反作弊抽检。
     */
    public Map<String, Object> playback(String replayId, double speedMul,
                                        Double expectedDamageMin, Double expectedDamageMax) {
        List<ReplayEntry> list = buffers.get(replayId);
        if (list == null) {
            String uri = archivedUri.get(replayId);
            if (uri != null) {
                return Map.of("ok", false, "error", "archived", "uri", uri);
            }
            return Map.of("ok", false, "error", "replay_not_found");
        }
        PatchBaselineContext baseline = baselines.get(replayId);
        if (baseline != null && !historicalConfigs.exists(baseline.configVersion())) {
            return Map.of("ok", false, "error", "baseline_expired",
                    "message", "该录像因版本更迭已失效",
                    "configVersion", baseline.configVersion());
        }
        double mul = speedMul <= 0 ? 1d : speedMul;
        List<Map<String, Object>> frames = new ArrayList<>();
        double damageSum = 0d;
        HistoricalConfigSnapshotStore.ConfigBaseline historical = baseline == null
                ? null : historicalConfigs.find(baseline.configVersion());
        synchronized (list) {
            for (ReplayEntry e : list) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("tick", e.tick());
                row.put("tsMs", e.tsMs());
                row.put("actionType", e.actionType());
                row.put("payload", e.payload());
                row.put("virtualDtMs", Math.round(16.0 / mul));
                if (historical != null) {
                    row.put("historicalConfigVersion", historical.configVersion());
                    row.put("historicalServerEpoch", historical.serverEpoch());
                }
                frames.add(row);
                Object dmg = e.payload().get("damage");
                if (dmg instanceof Number n) {
                    damageSum += n.doubleValue();
                }
            }
        }
        boolean withinSeed = true;
        if (expectedDamageMin != null && expectedDamageMax != null) {
            withinSeed = damageSum >= expectedDamageMin && damageSum <= expectedDamageMax;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("replayId", replayId);
        body.put("speedMul", mul);
        body.put("frameCount", frames.size());
        body.put("frames", frames);
        body.put("damageSum", damageSum);
        body.put("withinSeedExpectation", withinSeed);
        body.put("seed", randomSeeds.getOrDefault(replayId, 0L));
        if (baseline != null) {
            body.put("configVersion", baseline.configVersion());
            body.put("gitCommitSha", baseline.gitCommitSha());
            body.put("serverEpoch", baseline.serverEpoch());
            body.put("usingHistoricalBaseline", historical != null);
        }
        return body;
    }

    /** 24h 后模拟归档到 OSS/MinIO；超过 7 天强制执行冷存储。 */
    public Map<String, Object> archiveIfExpired(String replayId, long nowMs) {
        Long start = startedAt.get(replayId);
        if (start == null) {
            return Map.of("ok", false, "error", "replay_not_found");
        }
        List<ReplayEntry> list = buffers.get(replayId);
        int size = list == null ? 0 : list.size();
        Map<String, Object> cold = retention.archiveIfCold("replay:" + replayId, nowMs, size);
        if (Boolean.TRUE.equals(cold.get("ok"))) {
            buffers.remove(replayId);
            startedAt.remove(replayId);
            String uri = String.valueOf(cold.get("uri"));
            archivedUri.put(replayId, uri);
            return cold;
        }
        if (nowMs - start < ARCHIVE_AFTER_MS) {
            return Map.of("ok", false, "error", "not_expired", "ageMs", nowMs - start);
        }
        list = buffers.remove(replayId);
        size = list == null ? 0 : list.size();
        String uri = "oss://replays/" + retention.shardTable(replayId) + "/" + replayId + ".pb";
        archivedUri.put(replayId, uri);
        startedAt.remove(replayId);
        retention.markHot("replay:" + replayId, start);
        return Map.of("ok", true, "replayId", replayId, "uri", uri, "entries", size, "format", "protobuf");
    }

    public HistoricalDataRetention retention() {
        return retention;
    }

    public int size(String replayId) {
        List<ReplayEntry> list = buffers.get(replayId);
        return list == null ? 0 : list.size();
    }
}
