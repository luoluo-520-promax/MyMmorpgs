package cn.itcast.demo.mymmorpg.world.explore;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 探索度世界影响：阈值解锁新路径、改变 NPC 对话、触发区域事件。
 */
@Service
public class ExplorationWorldImpactService {

    public enum ImpactKind {
        UNLOCK_PATH, NPC_DIALOGUE, TRIGGER_EVENT, SPAWN_NPC, OPEN_GATE
    }

    public record ImpactDef(
            String impactId,
            String regionId,
            int thresholdPercent,
            ImpactKind kind,
            String targetId,
            String description,
            Map<String, Object> payload) {

        public ImpactDef {
            payload = payload == null ? Map.of() : Map.copyOf(payload);
        }
    }

    private final RegionProgressService regionProgress;
    private final ConcurrentHashMap<String, ImpactDef> impacts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Set<String>> applied = new ConcurrentHashMap<>();

    public ExplorationWorldImpactService(RegionProgressService regionProgress) {
        this.regionProgress = regionProgress;
    }

    public ExplorationWorldImpactService() {
        this(new RegionProgressService());
    }

    public void register(ImpactDef def) {
        if (def != null && def.impactId() != null) {
            impacts.put(def.impactId(), def);
        }
    }

    /**
     * 根据当前探索度评估并应用尚未触发的影响。
     */
    public Map<String, Object> evaluate(long playerId, String regionId) {
        int percent = explorationPercent(playerId, regionId);
        Set<String> done = applied.computeIfAbsent(playerId, id -> ConcurrentHashMap.newKeySet());
        List<Map<String, Object>> newlyApplied = new ArrayList<>();
        List<Map<String, Object>> pending = new ArrayList<>();

        for (ImpactDef def : impacts.values()) {
            if (!def.regionId().equals(regionId)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("impactId", def.impactId());
            row.put("kind", def.kind().name());
            row.put("targetId", def.targetId());
            row.put("description", def.description());
            row.put("thresholdPercent", def.thresholdPercent());
            row.put("payload", def.payload());
            if (percent >= def.thresholdPercent()) {
                if (!done.contains(def.impactId())) {
                    done.add(def.impactId());
                    row.put("justApplied", true);
                    row.put("worldChange", buildWorldChange(def));
                    newlyApplied.add(row);
                } else {
                    row.put("justApplied", false);
                    row.put("worldChange", buildWorldChange(def));
                    newlyApplied.add(row);
                }
            } else {
                row.put("remainingPercent", def.thresholdPercent() - percent);
                pending.add(row);
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("regionId", regionId);
        body.put("explorationPercent", percent);
        body.put("newlyApplied", newlyApplied.stream().filter(r -> Boolean.TRUE.equals(r.get("justApplied"))).toList());
        body.put("activeImpacts", newlyApplied);
        body.put("pendingImpacts", pending);
        return body;
    }

    public boolean hasImpact(long playerId, String impactId) {
        return applied.getOrDefault(playerId, Set.of()).contains(impactId);
    }

    public String npcDialogueOverride(long playerId, String npcId, String defaultLine) {
        for (ImpactDef def : impacts.values()) {
            if (def.kind() != ImpactKind.NPC_DIALOGUE || !def.targetId().equals(npcId)) {
                continue;
            }
            if (hasImpact(playerId, def.impactId())) {
                Object line = def.payload().get("dialogueLine");
                return line == null ? defaultLine : String.valueOf(line);
            }
        }
        return defaultLine;
    }

    private Map<String, Object> buildWorldChange(ImpactDef def) {
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("kind", def.kind().name());
        change.put("targetId", def.targetId());
        switch (def.kind()) {
            case UNLOCK_PATH -> change.put("pathId", def.payload().getOrDefault("pathId", def.targetId()));
            case NPC_DIALOGUE -> change.put("dialogueLine", def.payload().get("dialogueLine"));
            case TRIGGER_EVENT -> change.put("eventId", def.payload().getOrDefault("eventId", def.targetId()));
            case SPAWN_NPC -> change.put("npcId", def.payload().getOrDefault("npcId", def.targetId()));
            case OPEN_GATE -> change.put("gateId", def.payload().getOrDefault("gateId", def.targetId()));
        }
        return change;
    }

    private int explorationPercent(long playerId, String regionId) {
        Map<String, Object> st = regionProgress.status(playerId, regionId);
        return rpPercent(st);
    }

    private static int rpPercent(Map<String, Object> st) {
        if (!Boolean.TRUE.equals(st.get("ok"))) {
            return 0;
        }
        Object rp = st.get("region_progress");
        if (rp instanceof Map<?, ?> m && m.get("percent") instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }
}
