package cn.itcast.demo.mymmorpg.world.narrative;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 剧情分支：玩家选择影响故事走向与后续解锁。
 */
@Service
public class StoryBranchService {

    public record StoryNode(
            String nodeId,
            String title,
            String body,
            List<Choice> choices) {

        public record Choice(String choiceId, String text, String nextNodeId, Map<String, Object> flags) {
            public Choice {
                flags = flags == null ? Map.of() : Map.copyOf(flags);
            }
        }

        public StoryNode {
            choices = choices == null ? List.of() : List.copyOf(choices);
        }
    }

    private final ConcurrentHashMap<String, StoryNode> nodes = new ConcurrentHashMap<>();
    /** playerId → 当前节点 */
    private final ConcurrentHashMap<Long, String> cursor = new ConcurrentHashMap<>();
    /** playerId → 累积 flags */
    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, Object>> flags =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, List<String>> history = new ConcurrentHashMap<>();

    public void register(StoryNode node) {
        nodes.put(node.nodeId(), node);
    }

    public Map<String, Object> start(long playerId, String nodeId) {
        if (!nodes.containsKey(nodeId)) {
            return Map.of("ok", false, "error", "node_not_found");
        }
        cursor.put(playerId, nodeId);
        history.computeIfAbsent(playerId, id -> new ArrayList<>()).add(nodeId);
        return view(playerId);
    }

    public Map<String, Object> choose(long playerId, String choiceId) {
        String current = cursor.get(playerId);
        if (current == null) {
            return Map.of("ok", false, "error", "story_not_started");
        }
        StoryNode node = nodes.get(current);
        if (node == null) {
            return Map.of("ok", false, "error", "node_missing");
        }
        StoryNode.Choice chosen = null;
        for (StoryNode.Choice c : node.choices()) {
            if (c.choiceId().equals(choiceId)) {
                chosen = c;
                break;
            }
        }
        if (chosen == null) {
            return Map.of("ok", false, "error", "invalid_choice");
        }
        ConcurrentHashMap<String, Object> f =
                flags.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>());
        f.putAll(chosen.flags());
        cursor.put(playerId, chosen.nextNodeId());
        history.computeIfAbsent(playerId, id -> new ArrayList<>()).add(chosen.nextNodeId());
        Map<String, Object> body = new LinkedHashMap<>(view(playerId));
        body.put("chosen", choiceId);
        body.put("appliedFlags", chosen.flags());
        return body;
    }

    public Map<String, Object> view(long playerId) {
        String nodeId = cursor.get(playerId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("nodeId", nodeId == null ? "" : nodeId);
        StoryNode node = nodeId == null ? null : nodes.get(nodeId);
        if (node != null) {
            body.put("title", node.title());
            body.put("body", node.body());
            body.put("choices", node.choices().stream().map(c -> Map.of(
                    "choiceId", c.choiceId(),
                    "text", c.text(),
                    "nextNodeId", c.nextNodeId()
            )).toList());
            body.put("ended", node.choices().isEmpty());
        } else {
            body.put("ended", true);
        }
        body.put("flags", Map.copyOf(flags.getOrDefault(playerId, new ConcurrentHashMap<>())));
        body.put("history", List.copyOf(history.getOrDefault(playerId, List.of())));
        return body;
    }
}
