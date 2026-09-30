package cn.itcast.demo.mymmorpg.ai.teammate;

import java.util.Locale;
import java.util.Map;

/**
 * AI 队友策略模式：保守 / 进攻 / 辅助，可由 JSON/Admin 热更参数。
 */
public enum TeammateStrategy {
    CONSERVATIVE("保守型"),
    AGGRESSIVE("进攻型"),
    SUPPORT("辅助型");

    private final String label;

    TeammateStrategy(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static TeammateStrategy fromHint(String hint) {
        if (hint == null || hint.isBlank()) {
            return AGGRESSIVE;
        }
        String h = hint.toLowerCase(Locale.ROOT);
        if (h.contains("保守") || h.contains("conservative") || h.contains("defend")) {
            return CONSERVATIVE;
        }
        if (h.contains("辅助") || h.contains("support") || h.contains("heal")) {
            return SUPPORT;
        }
        if (h.contains("进攻") || h.contains("aggressive") || h.contains("attack")) {
            return AGGRESSIVE;
        }
        return AGGRESSIVE;
    }

    /**
     * 策略参数（可被 Admin JSON 覆盖）。
     */
    public Map<String, Object> defaultParams() {
        return switch (this) {
            case CONSERVATIVE -> Map.of(
                    "engageDistance", 4.0,
                    "healThreshold", 0.65,
                    "focusFireWeight", 0.4,
                    "spreadWeight", 0.8);
            case AGGRESSIVE -> Map.of(
                    "engageDistance", 1.5,
                    "healThreshold", 0.35,
                    "focusFireWeight", 1.0,
                    "spreadWeight", 0.2);
            case SUPPORT -> Map.of(
                    "engageDistance", 6.0,
                    "healThreshold", 0.75,
                    "focusFireWeight", 0.3,
                    "spreadWeight", 0.5);
        };
    }

    public String preferAction(String role) {
        return switch (this) {
            case CONSERVATIVE -> "defend";
            case SUPPORT -> "heal".equalsIgnoreCase(role) || "healer".equalsIgnoreCase(role) ? "heal" : "follow";
            case AGGRESSIVE -> "focus_fire";
        };
    }
}
