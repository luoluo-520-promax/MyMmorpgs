package cn.itcast.demo.mymmorpg.world.ecosystem;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 生态叙事联动：生态行为与任务/角色故事产生联动，而非纯 AI 逻辑。
 */
@Service
public class EcoNarrativeBridgeService {

    public record EcoStoryLink(
            String linkId,
            String species,
            EcosystemBehaviorService.EcoState requiredState,
            String questId,
            String storyNodeId,
            String npcId,
            String narrativeHint) {
    }

    private final EcosystemBehaviorService ecosystem;
    private final ConcurrentHashMap<String, EcoStoryLink> links = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, String>> activeQuests =
            new ConcurrentHashMap<>();

    public EcoNarrativeBridgeService(EcosystemBehaviorService ecosystem) {
        this.ecosystem = ecosystem == null ? new EcosystemBehaviorService() : ecosystem;
    }

    public EcoNarrativeBridgeService() {
        this(new EcosystemBehaviorService());
    }

    public void register(EcoStoryLink link) {
        if (link != null && link.linkId() != null) {
            links.put(link.linkId(), link);
        }
    }

    public void setActiveQuest(long playerId, String questId) {
        if (playerId > 0 && questId != null) {
            activeQuests.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>())
                    .put(questId, "ACTIVE");
        }
    }

    public Map<String, Object> evaluate(long playerId, String creatureUid) {
        EcosystemBehaviorService.EcoState state = ecosystem.stateOf(creatureUid);
        EcosystemBehaviorService.CreatureEcoProfile profile = ecosystem.profileOf(creatureUid);
        if (profile == null) {
            return Map.of("ok", false, "error", "creature_not_found");
        }
        Map<String, String> quests = activeQuests.getOrDefault(playerId, new ConcurrentHashMap<>());
        List<Map<String, Object>> triggered = new ArrayList<>();

        for (EcoStoryLink link : links.values()) {
            if (!link.species().equals(profile.species()) || link.requiredState() != state) {
                continue;
            }
            if (!quests.containsKey(link.questId())) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("linkId", link.linkId());
            row.put("questId", link.questId());
            row.put("storyNodeId", link.storyNodeId());
            row.put("npcId", link.npcId());
            row.put("narrativeHint", link.narrativeHint());
            row.put("ecoState", state.name());
            row.put("triggerEvent", "ECO_STORY_BRIDGE");
            triggered.add(row);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("creatureUid", creatureUid);
        body.put("species", profile.species());
        body.put("ecoState", state.name());
        body.put("triggeredLinks", triggered);
        body.put("hasNarrative", !triggered.isEmpty());
        return body;
    }
}
