package cn.itcast.demo.mymmorpg.ai.bt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 轻量行为树节点：策划可通过 JSON/可视化工具配置 Sequence/Selector/Action/Condition。
 */
public final class BehaviorTree {

    public enum Status { SUCCESS, FAILURE, RUNNING }

    public interface Node {
        Status tick(Blackboard bb);
    }

    /** 黑板：Boss 运行时状态（阶段、狂暴、仇恨目标等）。 */
    public static final class Blackboard {
        private final Map<String, Object> data = new LinkedHashMap<>();

        public void put(String key, Object value) {
            data.put(key, value);
        }

        @SuppressWarnings("unchecked")
        public <T> T get(String key, T defaultValue) {
            Object v = data.get(key);
            return v == null ? defaultValue : (T) v;
        }

        public Map<String, Object> snapshot() {
            return Map.copyOf(data);
        }
    }

    public static Node sequence(Node... children) {
        List<Node> list = List.of(children);
        return bb -> {
            for (Node child : list) {
                Status s = child.tick(bb);
                if (s != Status.SUCCESS) {
                    return s;
                }
            }
            return Status.SUCCESS;
        };
    }

    public static Node selector(Node... children) {
        List<Node> list = List.of(children);
        return bb -> {
            for (Node child : list) {
                Status s = child.tick(bb);
                if (s == Status.SUCCESS || s == Status.RUNNING) {
                    return s;
                }
            }
            return Status.FAILURE;
        };
    }

    public static Node condition(Predicate<Blackboard> pred) {
        return bb -> pred.test(bb) ? Status.SUCCESS : Status.FAILURE;
    }

    public static Node action(String name, java.util.function.Function<Blackboard, Status> fn) {
        return bb -> {
            Status s = fn.apply(bb);
            bb.put("lastAction", name);
            return s;
        };
    }

    /**
     * 默认 Boss 树：低血量狂暴 → 阶段切换 → 锁定仇恨 → 普攻。
     */
    public static Node defaultBossTree() {
        Node enrage = sequence(
                condition(bb -> bb.get("hpRatio", 1.0) <= 0.3),
                action("enrage", bb -> {
                    bb.put("enraged", true);
                    bb.put("atkMul", 1.5);
                    return Status.SUCCESS;
                })
        );
        Node phaseShift = sequence(
                condition(bb -> {
                    int phase = bb.get("phase", 1);
                    double hp = bb.get("hpRatio", 1.0);
                    return phase == 1 && hp <= 0.6;
                }),
                action("phase_shift", bb -> {
                    bb.put("phase", 2);
                    bb.put("skill", "phase2_aoe");
                    return Status.SUCCESS;
                })
        );
        Node lockThreat = sequence(
                condition(bb -> bb.get("threatTargetId", 0L) > 0L),
                action("lock_threat", bb -> {
                    bb.put("focusTargetId", bb.get("threatTargetId", 0L));
                    return Status.SUCCESS;
                })
        );
        Node basicAttack = action("basic_attack", bb -> {
            bb.put("skill", bb.get("enraged", false) ? "enrage_slash" : "slash");
            return Status.SUCCESS;
        });
        return selector(enrage, phaseShift, lockThreat, basicAttack);
    }

    /** 从简易配置构建（可视化工具导出 JSON 的子集）。 */
    public static Node fromConfig(List<Map<String, Object>> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return defaultBossTree();
        }
        List<Node> built = new ArrayList<>();
        for (Map<String, Object> n : nodes) {
            String type = String.valueOf(n.getOrDefault("type", "action"));
            String name = String.valueOf(n.getOrDefault("name", "noop"));
            if ("condition".equalsIgnoreCase(type)) {
                String key = String.valueOf(n.getOrDefault("key", ""));
                Object expect = n.get("equals");
                built.add(condition(bb -> {
                    Object actual = bb.get(key, null);
                    return expect == null ? actual != null : String.valueOf(expect).equals(String.valueOf(actual));
                }));
            } else {
                built.add(action(name, bb -> {
                    bb.put("skill", name);
                    return Status.SUCCESS;
                }));
            }
        }
        return selector(built.toArray(Node[]::new));
    }

    private BehaviorTree() {
    }
}
