package cn.itcast.demo.mymmorpg.observability;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 日志脱敏：对手机号、支付票据、证件/银行卡、会话令牌、密码等敏感字段打码，
 * 避免 TLog / 结构化日志泄露到 ELK。
 */
public final class LogDesensitizer {

    private static final Pattern PHONE = Pattern.compile("(1[3-9]\\d)\\d{4}(\\d{4})");
    private static final Pattern JSON_SENSITIVE = Pattern.compile(
            "(\"(?:phone|mobile|paymentTicket|payment_ticket|ticket|idCard|id_card|bankCard|bank_card|"
                    + "sessionToken|session_token|password|passwd|pwd)\"\\s*:\\s*\")([^\"]*)(\")",
            Pattern.CASE_INSENSITIVE);

    private LogDesensitizer() {
    }

    /** 手机号保留前 3 后 4：138****1234 */
    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return phone;
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < 7) {
            return maskToken(phone);
        }
        if (digits.length() == 11) {
            return digits.substring(0, 3) + "****" + digits.substring(7);
        }
        int keepTail = Math.min(4, digits.length() / 2);
        int keepHead = Math.min(3, digits.length() - keepTail);
        return digits.substring(0, keepHead) + "****" + digits.substring(digits.length() - keepTail);
    }

    /** Token / 票据类：保留前后各 2～4 位，中间 **** */
    public static String maskToken(String token) {
        if (token == null || token.isEmpty()) {
            return token;
        }
        if (token.length() <= 4) {
            return "****";
        }
        if (token.length() <= 8) {
            return token.charAt(0) + "****" + token.charAt(token.length() - 1);
        }
        int head = Math.min(4, token.length() / 4);
        int tail = Math.min(4, token.length() / 4);
        return token.substring(0, head) + "****" + token.substring(token.length() - tail);
    }

    /**
     * 对 Map 中敏感键的值做脱敏，返回新 Map（不修改入参）。
     * 识别键名（忽略大小写与下划线）：phone、paymentTicket、idCard、bankCard、sessionToken、password。
     */
    public static Map<String, Object> desensitize(Map<String, ?> source) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (source == null) {
            return out;
        }
        for (Map.Entry<String, ?> e : source.entrySet()) {
            String key = e.getKey();
            Object value = e.getValue();
            if (key != null && value != null && isSensitiveKey(key)) {
                out.put(key, maskByKey(key, String.valueOf(value)));
            } else if (value instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked")
                Map<String, ?> asMap = (Map<String, ?>) nested;
                out.put(key, desensitize(asMap));
            } else {
                out.put(key, value);
            }
        }
        return out;
    }

    /** 对 JSON 字符串中的敏感字段值做正则替换脱敏。 */
    public static String desensitizeJson(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }
        Matcher m = JSON_SENSITIVE.matcher(json);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String field = m.group(1).toLowerCase(Locale.ROOT);
            String raw = m.group(2);
            String masked = field.contains("phone") || field.contains("mobile")
                    ? maskPhone(raw)
                    : maskToken(raw);
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + masked + m.group(3)));
        }
        m.appendTail(sb);
        // 顺带打码裸手机号
        return PHONE.matcher(sb.toString()).replaceAll("$1****$2");
    }

    private static boolean isSensitiveKey(String key) {
        String n = normalizeKey(key);
        return n.equals("phone") || n.equals("mobile")
                || n.equals("paymentticket") || n.equals("ticket")
                || n.equals("idcard") || n.equals("bankcard")
                || n.equals("sessiontoken") || n.equals("password")
                || n.equals("passwd") || n.equals("pwd");
    }

    private static String maskByKey(String key, String value) {
        String n = normalizeKey(key);
        if (n.equals("phone") || n.equals("mobile")) {
            return maskPhone(value);
        }
        return maskToken(value);
    }

    private static String normalizeKey(String key) {
        return key.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    }
}
