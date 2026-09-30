package cn.itcast.demo.mymmorpg.world.sideplay;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 搜打撤复合任务：侦察 → 进攻 → 安全撤离，嵌入大世界。
 */
@Service
public class ExtractionMissionService {

    public enum Phase {
        SCOUT, ASSAULT, EXTRACT, COMPLETED, FAILED
    }

    public record MissionDef(
            String missionId,
            String name,
            int sceneId,
            float scoutX, float scoutZ,
            float assaultX, float assaultZ,
            float extractX, float extractZ,
            float phaseRadius,
            List<Map<String, Object>> extractRewards) {

        public MissionDef {
            phaseRadius = phaseRadius <= 0f ? 12f : phaseRadius;
            extractRewards = extractRewards == null ? List.of() : List.copyOf(extractRewards);
        }
    }

    public record RunState(
            String missionId,
            Phase phase,
            boolean scoutDone,
            boolean assaultDone,
            int lootValue,
            long startedAtMs) {
    }

    private final ConcurrentHashMap<String, MissionDef> defs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, RunState> runs = new ConcurrentHashMap<>();

    public void register(MissionDef def) {
        defs.put(def.missionId(), def);
    }

    public Map<String, Object> start(long playerId, String missionId, long nowMs) {
        MissionDef def = defs.get(missionId);
        if (def == null) {
            return Map.of("ok", false, "error", "mission_not_found");
        }
        if (runs.containsKey(playerId)) {
            RunState cur = runs.get(playerId);
            if (cur.phase() != Phase.COMPLETED && cur.phase() != Phase.FAILED) {
                return Map.of("ok", false, "error", "mission_in_progress", "phase", cur.phase().name());
            }
        }
        runs.put(playerId, new RunState(missionId, Phase.SCOUT, false, false, 0, nowMs));
        return status(playerId);
    }

    public Map<String, Object> scout(long playerId, float x, float z) {
        return advance(playerId, Phase.SCOUT, x, z, 0);
    }

    public Map<String, Object> assault(long playerId, float x, float z, int lootGained) {
        return advance(playerId, Phase.ASSAULT, x, z, lootGained);
    }

    public Map<String, Object> extract(long playerId, float x, float z) {
        return advance(playerId, Phase.EXTRACT, x, z, 0);
    }

    public Map<String, Object> fail(long playerId, String reason) {
        RunState run = runs.get(playerId);
        if (run == null) {
            return Map.of("ok", false, "error", "no_run");
        }
        runs.put(playerId, new RunState(run.missionId(), Phase.FAILED,
                run.scoutDone(), run.assaultDone(), 0, run.startedAtMs()));
        return Map.of("ok", true, "phase", Phase.FAILED.name(), "reason", reason == null ? "downed" : reason,
                "lootLost", run.lootValue());
    }

    public Map<String, Object> status(long playerId) {
        RunState run = runs.get(playerId);
        if (run == null) {
            return Map.of("ok", true, "active", false);
        }
        MissionDef def = defs.get(run.missionId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("active", true);
        body.put("missionId", run.missionId());
        body.put("name", def == null ? run.missionId() : def.name());
        body.put("phase", run.phase().name());
        body.put("scoutDone", run.scoutDone());
        body.put("assaultDone", run.assaultDone());
        body.put("lootValue", run.lootValue());
        return body;
    }

    private Map<String, Object> advance(long playerId, Phase expected, float x, float z, int lootGained) {
        RunState run = runs.get(playerId);
        if (run == null) {
            return Map.of("ok", false, "error", "no_run");
        }
        MissionDef def = defs.get(run.missionId());
        if (def == null) {
            return Map.of("ok", false, "error", "mission_missing");
        }
        if (run.phase() != expected) {
            return Map.of("ok", false, "error", "wrong_phase", "expected", expected.name(),
                    "actual", run.phase().name());
        }
        float tx;
        float tz;
        switch (expected) {
            case SCOUT -> {
                tx = def.scoutX();
                tz = def.scoutZ();
            }
            case ASSAULT -> {
                tx = def.assaultX();
                tz = def.assaultZ();
            }
            case EXTRACT -> {
                tx = def.extractX();
                tz = def.extractZ();
            }
            default -> {
                return Map.of("ok", false, "error", "invalid_phase");
            }
        }
        float dx = x - tx;
        float dz = z - tz;
        if (Math.sqrt(dx * dx + dz * dz) > def.phaseRadius()) {
            return Map.of("ok", false, "error", "out_of_phase_zone");
        }
        RunState next;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        switch (expected) {
            case SCOUT -> {
                next = new RunState(run.missionId(), Phase.ASSAULT, true, false, 0, run.startedAtMs());
                body.put("phase", Phase.ASSAULT.name());
            }
            case ASSAULT -> {
                int loot = Math.max(0, lootGained);
                next = new RunState(run.missionId(), Phase.EXTRACT, true, true, loot, run.startedAtMs());
                body.put("phase", Phase.EXTRACT.name());
                body.put("lootValue", loot);
            }
            case EXTRACT -> {
                next = new RunState(run.missionId(), Phase.COMPLETED, true, true,
                        run.lootValue(), run.startedAtMs());
                body.put("phase", Phase.COMPLETED.name());
                body.put("lootValue", run.lootValue());
                body.put("grantPlans", def.extractRewards());
                body.put("idempotencyKey", "extract:" + playerId + ":" + run.missionId()
                        + ":" + run.startedAtMs());
            }
            default -> {
                return Map.of("ok", false, "error", "invalid_phase");
            }
        }
        runs.put(playerId, next);
        return body;
    }
}
