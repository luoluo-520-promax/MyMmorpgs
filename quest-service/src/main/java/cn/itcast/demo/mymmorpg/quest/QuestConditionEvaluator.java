package cn.itcast.demo.mymmorpg.quest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * 任务接取条件：AND / OR / NOT 树。
 * <pre>
 * {"op":"AND","children":[{"op":"LEVEL_MIN","value":10},{"op":"NOT","child":{"op":"VIP_MIN","value":1}}]}
 * </pre>
 */
public final class QuestConditionEvaluator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private QuestConditionEvaluator() {
    }

    public static boolean evaluate(String conditionJson, Map<String, Object> context) {
        if (conditionJson == null || conditionJson.isBlank()) {
            return true;
        }
        try {
            JsonNode root = MAPPER.readTree(conditionJson);
            return evalNode(root, context == null ? Map.of() : context);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean evaluate(JsonNode root, Map<String, Object> context) {
        if (root == null || root.isNull()) {
            return true;
        }
        return evalNode(root, context == null ? Map.of() : context);
    }

    private static boolean evalNode(JsonNode node, Map<String, Object> ctx) {
        if (node == null || node.isNull()) {
            return true;
        }
        String op = text(node, "op");
        if (op == null || op.isBlank()) {
            op = text(node, "type");
        }
        if (op == null || op.isBlank()) {
            return true;
        }
        op = op.trim().toUpperCase(Locale.ROOT);
        return switch (op) {
            case "AND" -> {
                JsonNode children = node.get("children");
                if (children == null || !children.isArray() || children.isEmpty()) {
                    yield true;
                }
                for (JsonNode c : children) {
                    if (!evalNode(c, ctx)) {
                        yield false;
                    }
                }
                yield true;
            }
            case "OR" -> {
                JsonNode children = node.get("children");
                if (children == null || !children.isArray() || children.isEmpty()) {
                    yield true;
                }
                for (JsonNode c : children) {
                    if (evalNode(c, ctx)) {
                        yield true;
                    }
                }
                yield false;
            }
            case "NOT" -> {
                JsonNode child = node.get("child");
                if (child == null) {
                    JsonNode children = node.get("children");
                    child = children != null && children.isArray() && !children.isEmpty()
                            ? children.get(0) : null;
                }
                yield !evalNode(child, ctx);
            }
            case "LEVEL_MIN" -> intCtx(ctx, "level") >= intValue(node);
            case "VIP_MIN" -> intCtx(ctx, "vip") >= intValue(node);
            case "QUEST_CLAIMED" -> {
                Object claimed = ctx.get("claimedQuestIds");
                int need = intValue(node);
                if (claimed instanceof Iterable<?> it) {
                    for (Object o : it) {
                        if (o instanceof Number n && n.intValue() == need) {
                            yield true;
                        }
                        if (o != null && String.valueOf(o).equals(String.valueOf(need))) {
                            yield true;
                        }
                    }
                }
                yield false;
            }
            case "TRUE" -> true;
            case "FALSE" -> false;
            default -> true;
        };
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static int intValue(JsonNode node) {
        if (node.has("value")) {
            return node.get("value").asInt(0);
        }
        if (node.has("intValue")) {
            return node.get("intValue").asInt(0);
        }
        return 0;
    }

    private static int intCtx(Map<String, Object> ctx, String key) {
        Object v = ctx.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v != null) {
            try {
                return Integer.parseInt(String.valueOf(v));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }
}
