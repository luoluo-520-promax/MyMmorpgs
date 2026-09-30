package cn.itcast.demo.mymmorpg.quest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 任务 DAG：前置任务 → 解锁分支。支持环检测。
 */
public final class QuestGraph {

    private final Map<Integer, List<Integer>> prerequisites = new HashMap<>();
    private final Map<Integer, List<Integer>> unlocks = new HashMap<>();

    public QuestGraph() {
    }

    public static QuestGraph fromTemplates(List<QuestTemplate> templates) {
        QuestGraph g = new QuestGraph();
        if (templates == null) {
            return g;
        }
        for (QuestTemplate tpl : templates) {
            if (tpl == null) {
                continue;
            }
            List<Integer> prereqs = tpl.prerequisiteQuestIds == null
                    ? List.of() : List.copyOf(tpl.prerequisiteQuestIds);
            g.prerequisites.put(tpl.questId, prereqs);
            for (Integer pre : prereqs) {
                if (pre == null) {
                    continue;
                }
                g.unlocks.computeIfAbsent(pre, k -> new ArrayList<>()).add(tpl.questId);
            }
        }
        return g;
    }

    public List<Integer> prerequisitesOf(int questId) {
        return prerequisites.getOrDefault(questId, List.of());
    }

    public List<Integer> unlockedBy(int questId) {
        return List.copyOf(unlocks.getOrDefault(questId, List.of()));
    }

    /**
     * 玩家是否可解锁该任务：所有前置均已领取（CLAIMED）或完成（COMPLETED）。
     *
     * @param completedOrClaimed 已完成或已领奖的 questId 集合
     */
    public boolean isUnlocked(int questId, Set<Integer> completedOrClaimed) {
        List<Integer> prereqs = prerequisitesOf(questId);
        if (prereqs.isEmpty()) {
            return true;
        }
        Set<Integer> done = completedOrClaimed == null ? Set.of() : completedOrClaimed;
        for (Integer pre : prereqs) {
            if (pre == null || !done.contains(pre)) {
                return false;
            }
        }
        return true;
    }

    /** @return 若存在环返回环上任一节点路径描述；无环返回 empty */
    public List<Integer> findCycle() {
        Set<Integer> nodes = new HashSet<>(prerequisites.keySet());
        for (List<Integer> deps : prerequisites.values()) {
            nodes.addAll(deps);
        }
        Set<Integer> visiting = new HashSet<>();
        Set<Integer> visited = new HashSet<>();
        List<Integer> stack = new ArrayList<>();
        for (Integer n : nodes) {
            List<Integer> cycle = dfs(n, visiting, visited, stack);
            if (cycle != null) {
                return cycle;
            }
        }
        return List.of();
    }

    public boolean hasCycle() {
        return !findCycle().isEmpty();
    }

    private List<Integer> dfs(int node, Set<Integer> visiting, Set<Integer> visited, List<Integer> stack) {
        if (visited.contains(node)) {
            return null;
        }
        if (visiting.contains(node)) {
            int idx = stack.indexOf(node);
            List<Integer> cycle = new ArrayList<>(stack.subList(Math.max(0, idx), stack.size()));
            cycle.add(node);
            return cycle;
        }
        visiting.add(node);
        stack.add(node);
        for (Integer pre : prerequisitesOf(node)) {
            if (pre == null) {
                continue;
            }
            List<Integer> cycle = dfs(pre, visiting, visited, stack);
            if (cycle != null) {
                return cycle;
            }
        }
        stack.remove(stack.size() - 1);
        visiting.remove(node);
        visited.add(node);
        return null;
    }

    /** 拓扑序（仅无环时有意义）。 */
    public List<Integer> topologicalOrder() {
        if (hasCycle()) {
            return List.of();
        }
        Map<Integer, Integer> indegree = new HashMap<>();
        Set<Integer> nodes = new HashSet<>(prerequisites.keySet());
        for (Map.Entry<Integer, List<Integer>> e : prerequisites.entrySet()) {
            indegree.putIfAbsent(e.getKey(), 0);
            for (Integer pre : e.getValue()) {
                nodes.add(pre);
                indegree.merge(e.getKey(), 1, Integer::sum);
                indegree.putIfAbsent(pre, 0);
            }
        }
        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (Integer n : nodes) {
            if (indegree.getOrDefault(n, 0) == 0) {
                q.add(n);
            }
        }
        List<Integer> order = new ArrayList<>();
        while (!q.isEmpty()) {
            Integer n = q.removeFirst();
            order.add(n);
            for (Integer child : unlocks.getOrDefault(n, List.of())) {
                int d = indegree.merge(child, -1, Integer::sum);
                if (d == 0) {
                    q.add(child);
                }
            }
        }
        return order;
    }
}
