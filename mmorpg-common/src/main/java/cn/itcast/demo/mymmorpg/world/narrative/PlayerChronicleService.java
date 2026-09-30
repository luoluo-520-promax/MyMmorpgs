package cn.itcast.demo.mymmorpg.world.narrative;

import org.springframework.stereotype.Service;

import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 玩家编年史：choice_based 剧情节点选择以 BitMap 持久化（chronicle:{playerId}）。
 */
@Service
public class PlayerChronicleService {

    public record ChoiceNode(String nodeId, int bitOffset, String choiceA, String choiceB) {
        public ChoiceNode {
            nodeId = nodeId == null ? "" : nodeId.trim();
            bitOffset = Math.max(0, bitOffset);
            choiceA = choiceA == null ? "A" : choiceA.trim();
            choiceB = choiceB == null ? "B" : choiceB.trim();
        }
    }

    private final ConcurrentHashMap<String, ChoiceNode> nodes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, BitSet> chronicles = new ConcurrentHashMap<>();
    /** 全服选择统计：nodeId → 选 A / 选 B */
    private final ConcurrentHashMap<String, AtomicInteger> choiceACount = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> choiceBCount = new ConcurrentHashMap<>();

    public void register(ChoiceNode node) {
        if (node != null && !node.nodeId().isBlank()) {
            nodes.put(node.nodeId(), node);
            choiceACount.putIfAbsent(node.nodeId(), new AtomicInteger(0));
            choiceBCount.putIfAbsent(node.nodeId(), new AtomicInteger(0));
        }
    }

    /**
     * 记录选择：BitMap offset 位；true=选择 B，false=选择 A。
     */
    public Map<String, Object> choose(long playerId, String nodeId, String choiceId) {
        ChoiceNode node = nodes.get(nodeId == null ? "" : nodeId.trim());
        if (node == null) {
            return Map.of("ok", false, "error", "node_not_found");
        }
        boolean pickB;
        if (node.choiceB().equals(choiceId)) {
            pickB = true;
        } else if (node.choiceA().equals(choiceId)) {
            pickB = false;
        } else {
            return Map.of("ok", false, "error", "invalid_choice");
        }
        BitSet bits = chronicles.computeIfAbsent(playerId, id -> new BitSet());
        if (pickB) {
            bits.set(node.bitOffset());
            choiceBCount.get(node.nodeId()).incrementAndGet();
        } else {
            bits.clear(node.bitOffset());
            choiceACount.get(node.nodeId()).incrementAndGet();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("nodeId", node.nodeId());
        body.put("choiceId", choiceId);
        body.put("bitOffset", node.bitOffset());
        body.put("bitValue", pickB);
        body.put("redisKey", "chronicle:" + playerId);
        body.put("choice_based", true);
        return body;
    }

    public boolean hasChoiceB(long playerId, String nodeId) {
        ChoiceNode node = nodes.get(nodeId);
        if (node == null) {
            return false;
        }
        BitSet bits = chronicles.get(playerId);
        return bits != null && bits.get(node.bitOffset());
    }

    public Map<String, Object> snapshot(long playerId) {
        BitSet bits = chronicles.getOrDefault(playerId, new BitSet());
        Map<String, Object> choices = new LinkedHashMap<>();
        for (ChoiceNode n : nodes.values()) {
            choices.put(n.nodeId(), bits.get(n.bitOffset()) ? n.choiceB() : n.choiceA());
        }
        return Map.of("ok", true, "playerId", playerId, "choices", choices,
                "redisKey", "chronicle:" + playerId);
    }

    /**
     * 全服因果：若选 B（如屠龙）占比 ≥ threshold，返回应提升的 Boss 刷新倍率。
     */
    public Map<String, Object> evaluateGlobalFlag(String nodeId, double threshold, double bossSpawnBoost) {
        ChoiceNode node = nodes.get(nodeId);
        if (node == null) {
            return Map.of("ok", false, "error", "node_not_found");
        }
        int a = choiceACount.get(node.nodeId()).get();
        int b = choiceBCount.get(node.nodeId()).get();
        int total = a + b;
        double ratioB = total == 0 ? 0d : (double) b / total;
        boolean triggered = total > 0 && ratioB >= threshold;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("nodeId", nodeId);
        body.put("choiceA", a);
        body.put("choiceB", b);
        body.put("ratioB", Math.round(ratioB * 1000d) / 1000d);
        body.put("threshold", threshold);
        body.put("global_flag", triggered);
        body.put("bossSpawnRateMul", triggered ? 1.0 + bossSpawnBoost : 1.0);
        if (triggered) {
            body.put("hotReload", Map.of(
                    "source", "DynamicDifficultyAdjuster",
                    "patch", Map.of("bossSpawnRateMul", 1.0 + bossSpawnBoost)));
        }
        return body;
    }
}
