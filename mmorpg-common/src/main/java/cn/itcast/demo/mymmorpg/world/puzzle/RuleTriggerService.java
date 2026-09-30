package cn.itcast.demo.mymmorpg.world.puzzle;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 规则触发器（ECA）：机关从静态开关升级为「事件-条件-动作」脚本化组合。
 * <p>
 * 条件支持 AND 组合（如 element=FIRE AND target=VINE AND time=NIGHT → 隐藏路径）。
 */
@Service
public class RuleTriggerService {

    public enum LogicOp {
        AND, OR
    }

    public record Condition(String key, String op, String value) {
        public Condition {
            key = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
            op = op == null || op.isBlank() ? "EQ" : op.trim().toUpperCase(Locale.ROOT);
            value = value == null ? "" : value.trim();
        }
    }

    public record Action(String type, String targetId, Map<String, Object> params) {
        public Action {
            type = type == null ? "SET_STATE" : type.trim().toUpperCase(Locale.ROOT);
            targetId = targetId == null ? "" : targetId.trim();
            params = params == null ? Map.of() : Map.copyOf(params);
        }
    }

    public record RuleDef(
            String ruleId,
            String gadgetId,
            String eventType,
            LogicOp logic,
            List<Condition> conditions,
            List<Action> actions,
            boolean oneShot) {

        public RuleDef {
            if (ruleId == null || ruleId.isBlank()) {
                throw new IllegalArgumentException("ruleId required");
            }
            gadgetId = gadgetId == null ? "" : gadgetId.trim();
            eventType = eventType == null ? "INTERACT" : eventType.trim().toUpperCase(Locale.ROOT);
            logic = logic == null ? LogicOp.AND : logic;
            conditions = conditions == null ? List.of() : List.copyOf(conditions);
            actions = actions == null ? List.of() : List.copyOf(actions);
        }
    }

    private final ConcurrentHashMap<String, RuleDef> rules = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> fired = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> gadgetStates = new ConcurrentHashMap<>();
    private final AtomicLong fireSeq = new AtomicLong();

    public void register(RuleDef rule) {
        rules.put(rule.ruleId(), rule);
        if (!rule.gadgetId().isBlank()) {
            gadgetStates.putIfAbsent(rule.gadgetId(), "IDLE");
        }
    }

    /**
     * 投递世界事件并匹配 ECA 规则；返回命中的动作结果。
     */
    public Map<String, Object> fire(String eventType, Map<String, Object> context) {
        Map<String, Object> ctx = normalizeContext(context);
        String ev = eventType == null ? "INTERACT" : eventType.trim().toUpperCase(Locale.ROOT);
        List<Map<String, Object>> matched = new ArrayList<>();
        for (RuleDef rule : rules.values()) {
            if (!rule.eventType().equals(ev)) {
                continue;
            }
            if (rule.oneShot() && Boolean.TRUE.equals(fired.get(rule.ruleId()))) {
                continue;
            }
            if (!matchConditions(rule, ctx)) {
                continue;
            }
            List<Map<String, Object>> applied = applyActions(rule);
            fired.put(rule.ruleId(), true);
            Map<String, Object> hit = new LinkedHashMap<>();
            hit.put("ruleId", rule.ruleId());
            hit.put("gadgetId", rule.gadgetId());
            hit.put("actions", applied);
            hit.put("fireSeq", fireSeq.incrementAndGet());
            matched.add(hit);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("eventType", ev);
        body.put("matched", matched);
        body.put("matchedCount", matched.size());
        body.put("context", ctx);
        return body;
    }

    public Map<String, Object> gadgetState(String gadgetId) {
        return Map.of(
                "ok", true,
                "gadgetId", gadgetId,
                "state", gadgetStates.getOrDefault(gadgetId, "UNKNOWN"));
    }

    public List<Map<String, Object>> listRules() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (RuleDef r : rules.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ruleId", r.ruleId());
            m.put("gadgetId", r.gadgetId());
            m.put("eventType", r.eventType());
            m.put("logic", r.logic().name());
            m.put("conditionCount", r.conditions().size());
            m.put("actionCount", r.actions().size());
            m.put("oneShot", r.oneShot());
            m.put("fired", Boolean.TRUE.equals(fired.get(r.ruleId())));
            out.add(m);
        }
        return out;
    }

    private boolean matchConditions(RuleDef rule, Map<String, Object> ctx) {
        if (rule.conditions().isEmpty()) {
            return true;
        }
        boolean and = rule.logic() == LogicOp.AND;
        boolean any = false;
        for (Condition c : rule.conditions()) {
            boolean ok = eval(c, ctx);
            if (and && !ok) {
                return false;
            }
            if (!and && ok) {
                return true;
            }
            any = any || ok;
        }
        return and || any;
    }

    private static boolean eval(Condition c, Map<String, Object> ctx) {
        // REQUIRE_PARTY_MEMBERS：队伍成员数 >= N（可写在 key 或作为独立条件类型）
        if ("require_party_members".equals(c.key()) || "REQUIRE_PARTY_MEMBERS".equalsIgnoreCase(c.op())) {
            double need = toDouble(c.value());
            Object party = ctx.get("party_members");
            if (party == null) {
                party = ctx.get("partyMembers");
            }
            double actual = toDouble(party == null ? "0" : String.valueOf(party));
            return actual >= need;
        }
        Object raw = ctx.get(c.key());
        String actual = raw == null ? "" : String.valueOf(raw);
        return switch (c.op()) {
            case "NE", "NEQ" -> !actual.equalsIgnoreCase(c.value());
            case "CONTAINS" -> actual.toLowerCase(Locale.ROOT).contains(c.value().toLowerCase(Locale.ROOT));
            case "GTE" -> toDouble(actual) >= toDouble(c.value());
            case "LTE" -> toDouble(actual) <= toDouble(c.value());
            case "REQUIRE_PARTY_MEMBERS" -> {
                Object party = ctx.get("party_members");
                if (party == null) {
                    party = ctx.get("partyMembers");
                }
                yield toDouble(party == null ? "0" : String.valueOf(party)) >= toDouble(c.value());
            }
            default -> actual.equalsIgnoreCase(c.value());
        };
    }

    private List<Map<String, Object>> applyActions(RuleDef rule) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Action a : rule.actions()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", a.type());
            row.put("targetId", a.targetId());
            row.put("params", a.params());
            switch (a.type()) {
                case "SET_STATE" -> {
                    String state = String.valueOf(a.params().getOrDefault("state", "ACTIVE"));
                    String gid = a.targetId().isBlank() ? rule.gadgetId() : a.targetId();
                    gadgetStates.put(gid, state);
                    row.put("appliedState", state);
                }
                case "REVEAL_PATH", "SPAWN_WIND", "UNLOCK_PORTAL", "GRANT_ITEM" ->
                        row.put("applied", true);
                default -> row.put("applied", true);
            }
            out.add(row);
        }
        return out;
    }

    private static Map<String, Object> normalizeContext(Map<String, Object> context) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        if (context == null) {
            return ctx;
        }
        for (Map.Entry<String, Object> e : context.entrySet()) {
            if (e.getKey() != null) {
                ctx.put(e.getKey().trim().toLowerCase(Locale.ROOT), e.getValue());
            }
        }
        return ctx;
    }

    private static double toDouble(String s) {
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return 0d;
        }
    }
}
