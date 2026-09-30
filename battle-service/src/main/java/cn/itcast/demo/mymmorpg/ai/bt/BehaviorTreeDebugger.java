package cn.itcast.demo.mymmorpg.ai.bt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 行为树调试：记录最近 tick 路径与当前节点，供 GM 查询；支持 JSON 配置热加载。
 */
public final class BehaviorTreeDebugger {

    public record TickTrace(String treeId, String lastAction, String status, Map<String, Object> blackboard, long atMs) {
    }

    private final ConcurrentHashMap<String, BehaviorTree.Node> trees = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BehaviorTree.Blackboard> blackboards = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TickTrace> lastTraces = new ConcurrentHashMap<>();

    public void loadFromConfig(String treeId, List<Map<String, Object>> nodes) {
        trees.put(treeId, BehaviorTree.fromConfig(nodes));
        blackboards.computeIfAbsent(treeId, k -> new BehaviorTree.Blackboard());
    }

    public void loadDefault(String treeId) {
        trees.put(treeId, BehaviorTree.defaultBossTree());
        blackboards.computeIfAbsent(treeId, k -> new BehaviorTree.Blackboard());
    }

    public BehaviorTree.Blackboard blackboard(String treeId) {
        return blackboards.computeIfAbsent(treeId, k -> new BehaviorTree.Blackboard());
    }

    public TickTrace tick(String treeId, long nowMs) {
        BehaviorTree.Node node = trees.computeIfAbsent(treeId, id -> BehaviorTree.defaultBossTree());
        BehaviorTree.Blackboard bb = blackboard(treeId);
        BehaviorTree.Status status = node.tick(bb);
        TickTrace trace = new TickTrace(
                treeId,
                String.valueOf(bb.get("lastAction", "")),
                status.name(),
                bb.snapshot(),
                nowMs);
        lastTraces.put(treeId, trace);
        return trace;
    }

    public Map<String, Object> gmInspect(String treeId) {
        TickTrace t = lastTraces.get(treeId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("treeId", treeId);
        m.put("loaded", trees.containsKey(treeId));
        if (t == null) {
            m.put("trace", Map.of());
            return m;
        }
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("lastAction", t.lastAction());
        trace.put("status", t.status());
        trace.put("atMs", t.atMs());
        trace.put("blackboard", t.blackboard());
        m.put("trace", trace);
        return m;
    }

    public List<String> listTreeIds() {
        return new ArrayList<>(trees.keySet());
    }
}
