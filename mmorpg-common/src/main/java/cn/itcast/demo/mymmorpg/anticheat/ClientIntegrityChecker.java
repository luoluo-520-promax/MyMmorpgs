package cn.itcast.demo.mymmorpg.anticheat;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 客户端可信环境检测桩：根据设备指纹与安全标志打分，任一关键标志即判定不可信。
 * 生产可替换为厂商 SDK / 自建签名校验。
 */
@Component
public class ClientIntegrityChecker {

    public static final String FLAG_ROOTED = "rooted";
    public static final String FLAG_EMULATOR = "emulator";
    public static final String FLAG_DEBUGGER = "debuggerAttached";
    public static final String FLAG_HOOKED = "hooked";
    public static final String FLAG_TAMPERED = "tamperedSignature";

    public record IntegrityResult(boolean trusted, int score, List<String> reasons) {
        public static IntegrityResult ok() {
            return new IntegrityResult(true, 100, List.of());
        }
    }

    /** 关键标志：任一为 true → 不可信 */
    private static final String[] CRITICAL_FLAGS = {
            FLAG_ROOTED, FLAG_HOOKED, FLAG_TAMPERED, FLAG_DEBUGGER
    };

    /** 次要标志：降分但不单独否决（除非叠加到阈值） */
    private static final String[] SOFT_FLAGS = {
            FLAG_EMULATOR
    };

    private volatile int softScorePenalty = 30;
    private volatile int minTrustedScore = 70;

    public void configure(int softScorePenalty, int minTrustedScore) {
        this.softScorePenalty = Math.max(0, softScorePenalty);
        this.minTrustedScore = Math.max(0, Math.min(100, minTrustedScore));
    }

    /**
     * @param deviceFingerprint 设备指纹（空则记一条 reason，但不单独否决）
     * @param flags             标志 Map，值为 Boolean / "true"/"1"
     */
    public IntegrityResult check(String deviceFingerprint, Map<String, ?> flags) {
        List<String> reasons = new ArrayList<>();
        int score = 100;

        if (deviceFingerprint == null || deviceFingerprint.isBlank()) {
            reasons.add("missing_device_fingerprint");
            score -= 10;
        }

        if (flags != null) {
            for (String flag : CRITICAL_FLAGS) {
                if (isSet(flags, flag)) {
                    reasons.add(flag);
                    return new IntegrityResult(false, 0, List.copyOf(reasons));
                }
            }
            for (String flag : SOFT_FLAGS) {
                if (isSet(flags, flag)) {
                    reasons.add(flag);
                    score -= softScorePenalty;
                }
            }
        }

        boolean trusted = score >= minTrustedScore && reasons.stream()
                .noneMatch(r -> isCriticalReason(r));
        if (!trusted && score < minTrustedScore && !reasons.contains("score_below_threshold")) {
            reasons.add("score_below_threshold");
        }
        return new IntegrityResult(trusted, Math.max(0, score), List.copyOf(reasons));
    }

    private static boolean isCriticalReason(String reason) {
        for (String f : CRITICAL_FLAGS) {
            if (f.equals(reason)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSet(Map<String, ?> flags, String key) {
        Object v = flags.get(key);
        if (v == null) {
            // 兼容 snake_case
            v = flags.get(toSnake(key));
        }
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof Number n) {
            return n.intValue() != 0;
        }
        if (v instanceof String s) {
            String n = s.trim().toLowerCase(Locale.ROOT);
            return n.equals("true") || n.equals("1") || n.equals("yes");
        }
        return false;
    }

    private static String toSnake(String camel) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camel.length(); i++) {
            char c = camel.charAt(i);
            if (Character.isUpperCase(c)) {
                sb.append('_').append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
