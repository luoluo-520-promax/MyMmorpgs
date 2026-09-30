package cn.itcast.demo.mymmorpg.world.progression;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 灵活日常/周常：多种完成路径（消耗体力、任意战斗、指定玩法），一键领取。
 */
@Service
public class FlexibleDailyQuestService {

    public enum ProgressKind {
        STAMINA_SPENT, ANY_BATTLE, EXPLORE_DISCOVER, GATHER, COMMISSION
    }

    public record QuestDef(
            String questId,
            String title,
            ProgressKind kind,
            int targetCount,
            List<Map<String, Object>> grantPlans) {

        public QuestDef {
            grantPlans = grantPlans == null ? List.of() : List.copyOf(grantPlans);
        }
    }

    private final ConcurrentHashMap<String, QuestDef> quests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, AtomicInteger>> progress =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Set<String>> claimed = new ConcurrentHashMap<>();

    public void register(QuestDef def) {
        if (def != null && def.questId() != null) {
            quests.put(def.questId(), def);
        }
    }

    public Map<String, Object> reportProgress(long playerId, ProgressKind kind, int amount) {
        List<Map<String, Object>> updated = new ArrayList<>();
        for (QuestDef q : quests.values()) {
            if (q.kind() != kind) {
                continue;
            }
            AtomicInteger counter = progress
                    .computeIfAbsent(playerId, id -> new ConcurrentHashMap<>())
                    .computeIfAbsent(q.questId(), k -> new AtomicInteger(0));
            int before = counter.get();
            int after = counter.addAndGet(Math.max(1, amount));
            updated.add(Map.of(
                    "questId", q.questId(),
                    "title", q.title(),
                    "progress", Math.min(after, q.targetCount()),
                    "target", q.targetCount(),
                    "completed", after >= q.targetCount()));
        }
        return Map.of("ok", true, "playerId", playerId, "kind", kind.name(), "updated", updated);
    }

    public Map<String, Object> claim(long playerId, String questId) {
        QuestDef q = quests.get(questId);
        if (q == null) {
            return Map.of("ok", false, "error", "quest_not_found");
        }
        int cur = progress.getOrDefault(playerId, new ConcurrentHashMap<>())
                .getOrDefault(questId, new AtomicInteger(0)).get();
        if (cur < q.targetCount()) {
            return Map.of("ok", false, "error", "not_complete", "progress", cur, "target", q.targetCount());
        }
        Set<String> done = claimed.computeIfAbsent(playerId, id -> ConcurrentHashMap.newKeySet());
        if (done.contains(questId)) {
            return Map.of("ok", false, "error", "already_claimed");
        }
        done.add(questId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("questId", questId);
        body.put("grantPlans", q.grantPlans());
        body.put("idempotencyKey", "daily:" + playerId + ":" + questId);
        return body;
    }

    public Map<String, Object> claimAll(long playerId) {
        List<Map<String, Object>> grants = new ArrayList<>();
        for (QuestDef q : quests.values()) {
            Map<String, Object> r = claim(playerId, q.questId());
            if (Boolean.TRUE.equals(r.get("ok"))) {
                grants.add(r);
            }
        }
        return Map.of("ok", true, "playerId", playerId, "claimed", grants);
    }

    public Map<String, Object> list(long playerId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Set<String> done = claimed.getOrDefault(playerId, Set.of());
        for (QuestDef q : quests.values()) {
            int cur = progress.getOrDefault(playerId, new ConcurrentHashMap<>())
                    .getOrDefault(q.questId(), new AtomicInteger(0)).get();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("questId", q.questId());
            row.put("title", q.title());
            row.put("kind", q.kind().name());
            row.put("progress", Math.min(cur, q.targetCount()));
            row.put("target", q.targetCount());
            row.put("completed", cur >= q.targetCount());
            row.put("claimed", done.contains(q.questId()));
            rows.add(row);
        }
        return Map.of("ok", true, "playerId", playerId, "quests", rows);
    }
}
