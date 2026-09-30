package cn.itcast.demo.mymmorpg.anticheat;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 可配置反作弊规则引擎：规则可热更新阈值，供 {@link AntiCheatService} 委托评估。
 */
@Component
public class AntiCheatRuleEngine {

    public enum RuleType {
        MOVE_SPEED, TELEPORT, DAMAGE, STRIKE_RATE
    }

    public enum Severity {
        INFO, WARN, REJECT, STRIKE, BAN
    }

    public record Rule(String ruleId, RuleType type, double threshold, boolean enabled, Severity severity) {
    }

    /**
     * 与 {@link AntiCheatService.CheckResult} 兼容：verdict 使用同一枚举。
     */
    public record CheckResult(AntiCheatService.Verdict verdict, String reason, String ruleId, double observed) {
        public static CheckResult ok() {
            return new CheckResult(AntiCheatService.Verdict.OK, "", null, 0);
        }

        public AntiCheatService.CheckResult toServiceResult(int strikeCount) {
            return new AntiCheatService.CheckResult(verdict, reason, strikeCount);
        }
    }

    private final ConcurrentHashMap<String, Rule> rules = new ConcurrentHashMap<>();

    public void loadRules(List<Rule> ruleList) {
        rules.clear();
        if (ruleList == null) {
            return;
        }
        for (Rule r : ruleList) {
            if (r == null || r.ruleId() == null || r.ruleId().isBlank() || r.type() == null) {
                continue;
            }
            rules.put(r.ruleId(), r);
        }
    }

    public Collection<Rule> listRules() {
        return List.copyOf(rules.values());
    }

    public Rule getRule(String ruleId) {
        return rules.get(ruleId);
    }

    /** 动态更新阈值；规则不存在返回 false。 */
    public boolean updateThreshold(String ruleId, double value) {
        Rule existing = rules.get(ruleId);
        if (existing == null) {
            return false;
        }
        rules.put(ruleId, new Rule(existing.ruleId(), existing.type(), value, existing.enabled(), existing.severity()));
        return true;
    }

    /**
     * 按上下文评估全部启用规则。context 常用键：
     * moveSpeed / reportedSpeed, distance / teleportDistance,
     * damage / actualDamage, expectedMaxDamage, strikeRate / strikesPerMinute。
     * 任一规则触发则返回最高严重度结果（BAN &gt; STRIKE &gt; REJECT &gt; WARN）。
     */
    public CheckResult evaluate(long playerId, Map<String, ?> context) {
        if (context == null || context.isEmpty() || rules.isEmpty()) {
            return CheckResult.ok();
        }
        CheckResult worst = CheckResult.ok();
        int worstRank = 0;
        for (Rule rule : rules.values()) {
            if (!rule.enabled()) {
                continue;
            }
            Double observed = observe(rule.type(), context);
            if (observed == null) {
                continue;
            }
            if (observed <= rule.threshold()) {
                continue;
            }
            AntiCheatService.Verdict verdict = toVerdict(rule.severity());
            int rank = rank(verdict);
            if (rank > worstRank) {
                worstRank = rank;
                worst = new CheckResult(verdict, rule.type().name().toLowerCase(Locale.ROOT) + "_exceeded",
                        rule.ruleId(), observed);
            }
        }
        return worst;
    }

    /** 默认规则集（与 AntiCheatService 初始阈值对齐）。 */
    public static List<Rule> defaultRules() {
        List<Rule> list = new ArrayList<>();
        list.add(new Rule("move_speed", RuleType.MOVE_SPEED, 120.0, true, Severity.STRIKE));
        list.add(new Rule("teleport", RuleType.TELEPORT, 400.0, true, Severity.STRIKE));
        list.add(new Rule("damage", RuleType.DAMAGE, 3.0, true, Severity.STRIKE));
        list.add(new Rule("strike_rate", RuleType.STRIKE_RATE, 10.0, true, Severity.BAN));
        return list;
    }

    private static Double observe(RuleType type, Map<String, ?> ctx) {
        return switch (type) {
            case MOVE_SPEED -> firstNumber(ctx, "moveSpeed", "reportedSpeed", "speed");
            case TELEPORT -> firstNumber(ctx, "distance", "teleportDistance", "dist");
            case DAMAGE -> damageRatio(ctx);
            case STRIKE_RATE -> firstNumber(ctx, "strikeRate", "strikesPerMinute", "strikes");
        };
    }

    private static Double damageRatio(Map<String, ?> ctx) {
        Double actual = firstNumber(ctx, "damage", "actualDamage");
        Double expected = firstNumber(ctx, "expectedMaxDamage", "expectedMax");
        if (actual == null) {
            return firstNumber(ctx, "damageRatio", "damageMultiplier");
        }
        if (expected == null || expected <= 0) {
            return actual;
        }
        return actual / expected;
    }

    private static Double firstNumber(Map<String, ?> ctx, String... keys) {
        for (String key : keys) {
            Object v = ctx.get(key);
            if (v instanceof Number n) {
                return n.doubleValue();
            }
            if (v instanceof String s && !s.isBlank()) {
                try {
                    return Double.parseDouble(s.trim());
                } catch (NumberFormatException ignored) {
                    // continue
                }
            }
        }
        return null;
    }

    private static AntiCheatService.Verdict toVerdict(Severity severity) {
        if (severity == null) {
            return AntiCheatService.Verdict.STRIKE;
        }
        return switch (severity) {
            case INFO, WARN -> AntiCheatService.Verdict.REJECT;
            case REJECT -> AntiCheatService.Verdict.REJECT;
            case STRIKE -> AntiCheatService.Verdict.STRIKE;
            case BAN -> AntiCheatService.Verdict.BAN;
        };
    }

    private static int rank(AntiCheatService.Verdict v) {
        return switch (v) {
            case OK -> 0;
            case REJECT -> 1;
            case STRIKE -> 2;
            case BAN -> 3;
        };
    }
}
