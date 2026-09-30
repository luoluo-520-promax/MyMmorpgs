package cn.itcast.demo.mymmorpg.world.ecosystem;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 世界对玩家探索行为的正向反馈：解锁捷径、环境美化、NPC 致谢等。
 */
@Service
public class WorldExplorationFeedbackService {

    public enum ActionKind {
        DISCOVER_VISTA, CLEAR_CAMP, FEED_CREATURE, SOLVE_PUZZLE, COMPLETE_REGION
    }

    public record FeedbackRule(
            String ruleId,
            String regionId,
            ActionKind action,
            int countThreshold,
            String rewardLabel,
            Map<String, Object> worldEffect) {

        public FeedbackRule {
            worldEffect = worldEffect == null ? Map.of() : Map.copyOf(worldEffect);
        }
    }

    private final ConcurrentHashMap<String, FeedbackRule> rules = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ConcurrentHashMap<ActionKind, AtomicInteger>> counters =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, List<String>> grantedEffects = new ConcurrentHashMap<>();

    public void register(FeedbackRule rule) {
        if (rule != null && rule.ruleId() != null) {
            rules.put(rule.ruleId(), rule);
        }
    }

    public Map<String, Object> recordAction(long playerId, String regionId, ActionKind action) {
        AtomicInteger counter = counters
                .computeIfAbsent(playerId, id -> new ConcurrentHashMap<>())
                .computeIfAbsent(action, k -> new AtomicInteger(0));
        int count = counter.incrementAndGet();

        List<Map<String, Object>> unlocked = new ArrayList<>();
        for (FeedbackRule rule : rules.values()) {
            if (!rule.regionId().equals(regionId) || rule.action() != action) {
                continue;
            }
            if (count >= rule.countThreshold()) {
                String key = rule.ruleId();
                List<String> effects = grantedEffects.computeIfAbsent(playerId, id -> new ArrayList<>());
                if (!effects.contains(key)) {
                    effects.add(key);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("ruleId", rule.ruleId());
                    row.put("rewardLabel", rule.rewardLabel());
                    row.put("worldEffect", rule.worldEffect());
                    row.put("positiveFeedback", true);
                    unlocked.add(row);
                }
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("regionId", regionId);
        body.put("action", action.name());
        body.put("count", count);
        body.put("newlyUnlocked", unlocked);
        return body;
    }

    public List<String> activeEffects(long playerId) {
        return List.copyOf(grantedEffects.getOrDefault(playerId, List.of()));
    }
}
