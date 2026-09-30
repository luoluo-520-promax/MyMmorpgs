package cn.itcast.demo.mymmorpg.world.narrative;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 世界偶遇：大世界探索时随机触发角色剧情（对话 / 委托 / 共赏风景），强化情感连接。
 */
@Service
public class WorldEncounterService {

    public enum EncounterKind {
        DIALOGUE, COMMISSION, SCENIC, MINIGAME
    }

    public record EncounterDef(
            String encounterId,
            String characterId,
            String characterName,
            EncounterKind kind,
            String prompt,
            /** 触发权重 */
            int weight,
            /** 冷却秒 */
            int cooldownSeconds,
            Map<String, Object> rewardHint) {

        public EncounterDef {
            kind = kind == null ? EncounterKind.DIALOGUE : kind;
            weight = Math.max(1, weight);
            cooldownSeconds = Math.max(30, cooldownSeconds);
            rewardHint = rewardHint == null ? Map.of() : Map.copyOf(rewardHint);
        }
    }

    private final ConcurrentHashMap<String, EncounterDef> defs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, Long>> lastTriggerMs =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, List<Map<String, Object>>> recent =
            new ConcurrentHashMap<>();

    public void register(EncounterDef def) {
        defs.put(def.encounterId(), def);
    }

    /**
     * 按权重抽样一次偶遇；冷却内返回 miss。
     */
    public Map<String, Object> tryTrigger(long playerId, int sceneId, long nowMs) {
        if (defs.isEmpty()) {
            return Map.of("ok", true, "triggered", false, "reason", "empty_pool");
        }
        List<EncounterDef> pool = new ArrayList<>();
        int total = 0;
        ConcurrentHashMap<String, Long> cd =
                lastTriggerMs.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>());
        for (EncounterDef d : defs.values()) {
            Long last = cd.get(d.encounterId());
            if (last != null && nowMs - last < d.cooldownSeconds() * 1000L) {
                continue;
            }
            pool.add(d);
            total += d.weight();
        }
        if (pool.isEmpty() || total <= 0) {
            return Map.of("ok", true, "triggered", false, "reason", "all_on_cooldown");
        }
        int roll = ThreadLocalRandom.current().nextInt(total);
        EncounterDef picked = pool.get(0);
        int acc = 0;
        for (EncounterDef d : pool) {
            acc += d.weight();
            if (roll < acc) {
                picked = d;
                break;
            }
        }
        cd.put(picked.encounterId(), nowMs);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("encounterId", picked.encounterId());
        event.put("characterId", picked.characterId());
        event.put("characterName", picked.characterName());
        event.put("kind", picked.kind().name());
        event.put("prompt", picked.prompt());
        event.put("sceneId", sceneId);
        event.put("rewardHint", picked.rewardHint());
        event.put("triggeredAtMs", nowMs);
        recent.computeIfAbsent(playerId, id -> new ArrayList<>()).add(0, event);
        List<Map<String, Object>> hist = recent.get(playerId);
        while (hist.size() > 20) {
            hist.remove(hist.size() - 1);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("triggered", true);
        body.put("encounter", event);
        return body;
    }

    /** 确定性触发（测试 / GM） */
    public Map<String, Object> forceTrigger(long playerId, String encounterId, int sceneId, long nowMs) {
        EncounterDef d = defs.get(encounterId);
        if (d == null) {
            return Map.of("ok", false, "error", "encounter_not_found");
        }
        lastTriggerMs.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>())
                .put(encounterId, nowMs);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("encounterId", d.encounterId());
        event.put("characterId", d.characterId());
        event.put("characterName", d.characterName());
        event.put("kind", d.kind().name());
        event.put("prompt", d.prompt());
        event.put("sceneId", sceneId);
        event.put("rewardHint", d.rewardHint());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("triggered", true);
        body.put("encounter", event);
        return body;
    }

    public List<Map<String, Object>> recentOf(long playerId) {
        return List.copyOf(recent.getOrDefault(playerId, List.of()));
    }

    // ── 幻影回放：其他玩家残影（离线化身，非实时） ──

    public record PhantomTrace(
            String phantomId,
            long ownerPlayerId,
            String ownerName,
            int sceneId,
            float x, float y, float z,
            String pose,
            List<String> emoteFrames,
            long recordedAtMs) {

        public PhantomTrace {
            emoteFrames = emoteFrames == null ? List.of() : List.copyOf(emoteFrames);
            pose = pose == null ? "IDLE" : pose;
        }
    }

    private final ConcurrentHashMap<String, PhantomTrace> phantoms = new ConcurrentHashMap<>();

    public Map<String, Object> leavePhantom(
            long ownerPlayerId, String ownerName, int sceneId,
            float x, float y, float z, String pose, List<String> emoteFrames, long nowMs) {
        String id = "phantom-" + ownerPlayerId + "-" + nowMs;
        PhantomTrace trace = new PhantomTrace(id, ownerPlayerId,
                ownerName == null ? ("p" + ownerPlayerId) : ownerName,
                sceneId, x, y, z, pose, emoteFrames, nowMs);
        phantoms.put(id, trace);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("phantom", toPhantomView(trace));
        body.put("hint", "我在你世界留下痕迹");
        return body;
    }

    public Map<String, Object> playPhantom(String phantomId) {
        PhantomTrace t = phantoms.get(phantomId);
        if (t == null) {
            return Map.of("ok", false, "error", "phantom_not_found");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("phantomId", phantomId);
        body.put("ownerName", t.ownerName());
        body.put("pose", t.pose());
        body.put("playback", t.emoteFrames());
        body.put("durationMs", Math.max(1000, t.emoteFrames().size() * 400));
        return body;
    }

    public List<Map<String, Object>> listPhantomsNear(int sceneId, float x, float z, float radius) {
        float r = radius <= 0f ? 30f : radius;
        List<Map<String, Object>> out = new ArrayList<>();
        for (PhantomTrace t : phantoms.values()) {
            if (t.sceneId() != sceneId) {
                continue;
            }
            float dx = x - t.x();
            float dz = z - t.z();
            if (Math.sqrt(dx * dx + dz * dz) <= r) {
                out.add(toPhantomView(t));
            }
        }
        return out;
    }

    private Map<String, Object> toPhantomView(PhantomTrace t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("phantomId", t.phantomId());
        m.put("ownerPlayerId", t.ownerPlayerId());
        m.put("ownerName", t.ownerName());
        m.put("sceneId", t.sceneId());
        m.put("x", t.x());
        m.put("y", t.y());
        m.put("z", t.z());
        m.put("pose", t.pose());
        m.put("emoteFrames", t.emoteFrames());
        m.put("recordedAtMs", t.recordedAtMs());
        return m;
    }

    // ── 幻影求助引导：高难解谜失败 ≥3 次后推荐最近通关残影，AI 规则模拟路径 ──

    public static final String EVENT_PHANTOM_GUIDE_MARK = "PHANTOM_GUIDE_MARK";
    public static final int PUZZLE_FAIL_THRESHOLD = 3;

    private final ConcurrentHashMap<String, Integer> puzzleFails = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> puzzleClearPhantom = new ConcurrentHashMap<>();

    public Map<String, Object> recordPuzzleClearPhantom(String puzzleId, String phantomId) {
        puzzleClearPhantom.put(puzzleId, phantomId);
        return Map.of("ok", true, "puzzleId", puzzleId, "phantomId", phantomId);
    }

    public Map<String, Object> recordPuzzleFail(long playerId, String puzzleId) {
        String key = playerId + ":" + puzzleId;
        int fails = puzzleFails.merge(key, 1, Integer::sum);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("fails", fails);
        body.put("threshold", PUZZLE_FAIL_THRESHOLD);
        if (fails < PUZZLE_FAIL_THRESHOLD) {
            body.put("recommend", false);
            return body;
        }
        String phantomId = puzzleClearPhantom.get(puzzleId);
        if (phantomId == null) {
            // 回退：取任意同场景幻影
            phantomId = phantoms.values().stream().findFirst().map(PhantomTrace::phantomId).orElse(null);
        }
        if (phantomId == null) {
            body.put("recommend", false);
            body.put("reason", "no_clear_phantom");
            return body;
        }
        PhantomTrace t = phantoms.get(phantomId);
        List<Map<String, Object>> path = simulateGuidePath(t);
        body.put("recommend", true);
        body.put("event", EVENT_PHANTOM_GUIDE_MARK);
        body.put("phantomId", phantomId);
        body.put("guideMark", Map.of(
                "event", EVENT_PHANTOM_GUIDE_MARK,
                "phantomId", phantomId,
                "ownerName", t == null ? "" : t.ownerName(),
                "path", path,
                "aiRule", "WAYPOINT_WALK",
                "skipPuzzle", false,
                "note", "human_warmth_guide_not_skip"));
        return body;
    }

    private List<Map<String, Object>> simulateGuidePath(PhantomTrace t) {
        List<Map<String, Object>> path = new ArrayList<>();
        if (t == null) {
            return path;
        }
        float x = t.x();
        float z = t.z();
        for (int i = 0; i < 5; i++) {
            path.add(Map.of(
                    "step", i,
                    "x", x + i * 2f,
                    "y", t.y(),
                    "z", z + i * 1.5f,
                    "action", i == 4 ? "INTERACT" : "WALK"));
        }
        return path;
    }
}
