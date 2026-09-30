package cn.itcast.demo.mymmorpg.world.level;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 世界等级突破本：独立 SceneInstance + 时限；胜利后 upgradeWorldLevel（带互斥锁）。
 */
@Service
public class AscensionDungeonService {

    public static final long DEFAULT_TIME_LIMIT_MS = 180_000L;

    public record AscensionQuest(
            String questId,
            int fromLevel,
            int toLevel,
            String dungeonSceneKey,
            long timeLimitMs) {
        public AscensionQuest {
            questId = questId == null ? "" : questId.trim();
            timeLimitMs = timeLimitMs <= 0 ? DEFAULT_TIME_LIMIT_MS : timeLimitMs;
        }
    }

    public record RunState(
            String runId,
            long playerId,
            String questId,
            String sceneInstanceId,
            int fromLevel,
            int toLevel,
            long startMs,
            long deadlineMs,
            boolean finished) {
    }

    private final ConcurrentHashMap<String, AscensionQuest> quests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RunState> runs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> activeRunByPlayer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> upgradeLocks = new ConcurrentHashMap<>();
    private final WorldLevelManager worldLevels;
    private final ConcurrentHashMap<String, Boolean> firstClearBroadcast = new ConcurrentHashMap<>();

    public AscensionDungeonService() {
        this(new WorldLevelManager());
    }

    public AscensionDungeonService(WorldLevelManager worldLevels) {
        this.worldLevels = worldLevels == null ? new WorldLevelManager() : worldLevels;
    }

    public void register(AscensionQuest quest) {
        if (quest != null && !quest.questId().isBlank()) {
            quests.put(quest.questId(), quest);
        }
    }

    /**
     * 经验溢出且完成前置后进入突破本：分配独立 SceneInstance。
     */
    public Map<String, Object> enter(
            long playerId, String questId, int worldId, boolean expOverflow,
            boolean prerequisiteDone, long nowMs) {
        AscensionQuest q = quests.get(questId == null ? "" : questId.trim());
        if (q == null) {
            return Map.of("ok", false, "error", "ascension_quest_not_found");
        }
        if (!expOverflow) {
            return Map.of("ok", false, "error", "exp_not_overflow");
        }
        if (!prerequisiteDone) {
            return Map.of("ok", false, "error", "prerequisite_incomplete");
        }
        if (activeRunByPlayer.containsKey(playerId)) {
            return Map.of("ok", false, "error", "already_in_ascension");
        }
        int current = worldLevels.getWorldLevel(worldId, playerId);
        if (current < q.fromLevel()) {
            return Map.of("ok", false, "error", "world_level_too_low", "current", current);
        }
        if (current >= q.toLevel()) {
            return Map.of("ok", false, "error", "already_ascended", "current", current);
        }
        String runId = "asc-" + UUID.randomUUID();
        String instanceId = "asc-inst-" + playerId + "-" + nowMs;
        RunState run = new RunState(
                runId, playerId, q.questId(), instanceId,
                q.fromLevel(), q.toLevel(), nowMs, nowMs + q.timeLimitMs(), false);
        runs.put(runId, run);
        activeRunByPlayer.put(playerId, runId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("event", "ENTER_ASCENSION_DUNGEON");
        body.put("runId", runId);
        body.put("sceneInstanceId", instanceId);
        body.put("sharedZone", false);
        body.put("timeLimitMs", q.timeLimitMs());
        body.put("deadlineMs", run.deadlineMs());
        body.put("fromLevel", q.fromLevel());
        body.put("toLevel", q.toLevel());
        body.put("ascension", true);
        return body;
    }

    /**
     * 战斗结算：ascension=true 且胜利 → upgradeWorldLevel（分布式锁语义）。
     */
    public Map<String, Object> settle(
            String runId, boolean victory, int worldId, long nowMs) {
        RunState run = runs.get(runId);
        if (run == null) {
            return Map.of("ok", false, "error", "run_not_found");
        }
        if (run.finished()) {
            return Map.of("ok", false, "error", "already_settled");
        }
        if (nowMs > run.deadlineMs()) {
            finish(run, false);
            return Map.of("ok", false, "error", "time_up", "ascension", true);
        }
        if (!victory) {
            finish(run, false);
            return Map.of("ok", true, "victory", false, "worldLevelUnchanged", true);
        }
        String lockKey = "wl-upgrade:" + worldId + ":" + run.playerId();
        if (upgradeLocks.putIfAbsent(lockKey, Boolean.TRUE) != null) {
            return Map.of("ok", false, "error", "upgrade_locked");
        }
        try {
            worldLevels.setWorldLevel(worldId, run.playerId(), run.toLevel());
            finish(run, true);
            boolean first = firstClearBroadcast.putIfAbsent(run.questId() + ":" + run.playerId(), true) == null;
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ok", true);
            body.put("victory", true);
            body.put("ascension", true);
            body.put("worldLevel", run.toLevel());
            body.put("upgraded", true);
            body.put("firstClear", first);
            if (first) {
                body.put("broadcast", Map.of(
                        "channel", "RegionWorldChannel",
                        "event", "ASCENSION_FIRST_CLEAR",
                        "playerId", run.playerId(),
                        "toLevel", run.toLevel()));
            }
            return body;
        } finally {
            upgradeLocks.remove(lockKey);
        }
    }

    public WorldLevelManager worldLevels() {
        return worldLevels;
    }

    private void finish(RunState run, boolean success) {
        runs.put(run.runId(), new RunState(
                run.runId(), run.playerId(), run.questId(), run.sceneInstanceId(),
                run.fromLevel(), run.toLevel(), run.startMs(), run.deadlineMs(), true));
        activeRunByPlayer.remove(run.playerId(), run.runId());
    }
}
