package cn.itcast.demo.mymmorpg.world.narrative;

import cn.itcast.demo.mymmorpg.protocol.MessageId;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 动态剧情节点：编年史 choice_based 组合达标时全服广播强制过场。
 */
@Service
public class DynamicCutsceneTrigger {

    private final ConcurrentHashMap<String, AtomicInteger> choiceCounts = new ConcurrentHashMap<>();
    private final List<Map<String, Object>> broadcastLog = new ArrayList<>();

    public Map<String, Object> recordChoice(String choiceKey) {
        int v = choiceCounts.computeIfAbsent(choiceKey == null ? "" : choiceKey.trim(),
                k -> new AtomicInteger(0)).incrementAndGet();
        return Map.of("ok", true, "choice", choiceKey, "count", v);
    }

    public int countOf(String choiceKey) {
        return choiceCounts.getOrDefault(choiceKey == null ? "" : choiceKey.trim(),
                new AtomicInteger(0)).get();
    }

    /**
     * 例：A&gt;=3 且 B&lt;=1 → FORCED_CUTSCENE_START（可跳过，约 2s）。
     */
    public Map<String, Object> evaluate(String choiceA, String choiceB, String cutsceneId) {
        int a = countOf(choiceA);
        int b = countOf(choiceB);
        if (a < 3 || b > 1) {
            return Map.of("ok", false, "triggered", false, "A", a, "B", b,
                    "need", "A>=3 && B<=1");
        }
        Map<String, Object> notify = new LinkedHashMap<>();
        notify.put("event", "FORCED_CUTSCENE_START");
        notify.put("msgId", MessageId.FORCED_CUTSCENE_START_SC_NOTIFY);
        notify.put("cutsceneId", cutsceneId);
        notify.put("durationMs", 2000);
        notify.put("skippable", true);
        notify.put("note", "全服共历史诗");
        synchronized (broadcastLog) {
            broadcastLog.add(notify);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("triggered", true);
        body.put("broadcast", notify);
        return body;
    }

    public List<Map<String, Object>> recentBroadcasts() {
        synchronized (broadcastLog) {
            return List.copyOf(broadcastLog);
        }
    }
}
