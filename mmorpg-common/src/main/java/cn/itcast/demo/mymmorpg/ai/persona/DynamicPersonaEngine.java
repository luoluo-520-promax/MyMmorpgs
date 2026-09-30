package cn.itcast.demo.mymmorpg.ai.persona;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * P15 · 动态人格引擎：基于图鉴/探索/深渊数据聚类 CASUAL / HARDCORE / SOCIAL，
 * 并按设置页「AI 交流风格」滑块注入 System Prompt 的 {{PLAYER_PERSONA}}。
 */
public final class DynamicPersonaEngine {

    public enum Cluster {
        CASUAL, HARDCORE, SOCIAL
    }

    public enum StyleSlider {
        CONCISE, DETAILED, EMOTIONAL
    }

    public record PersonaSignals(
            double handbookProgress,
            double regionExplorePercent,
            int abyssStars,
            int friendAssistCount,
            int chatMessages7d) {

        public PersonaSignals {
            handbookProgress = clamp01(handbookProgress);
            regionExplorePercent = clamp01(regionExplorePercent / 100.0) * 100.0;
            abyssStars = Math.max(0, abyssStars);
            friendAssistCount = Math.max(0, friendAssistCount);
            chatMessages7d = Math.max(0, chatMessages7d);
        }
    }

    public record PersonaProfile(
            long playerId,
            Cluster cluster,
            StyleSlider style,
            String systemPromptFragment,
            Map<String, Object> tags) {
    }

    private final ConcurrentHashMap<Long, StyleSlider> stylePrefs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Cluster> overrideCluster = new ConcurrentHashMap<>();

    public Cluster cluster(PersonaSignals signals) {
        if (signals == null) {
            return Cluster.CASUAL;
        }
        double socialScore = signals.friendAssistCount() * 0.4 + signals.chatMessages7d() * 0.05;
        double hardScore = signals.abyssStars() / 3.0 + signals.regionExplorePercent() / 20.0;
        double casualScore = (1.0 - signals.handbookProgress()) * 2
                + (signals.abyssStars() < 6 ? 2 : 0);

        if (socialScore >= hardScore && socialScore >= casualScore && socialScore >= 3) {
            return Cluster.SOCIAL;
        }
        if (hardScore >= casualScore && (signals.abyssStars() >= 9 || signals.regionExplorePercent() >= 70)) {
            return Cluster.HARDCORE;
        }
        return Cluster.CASUAL;
    }

    public void setStyle(long playerId, StyleSlider style) {
        if (style != null) {
            stylePrefs.put(playerId, style);
        }
    }

    public void setStyle(long playerId, String styleName) {
        setStyle(playerId, parseStyle(styleName));
    }

    public StyleSlider styleOf(long playerId) {
        return stylePrefs.getOrDefault(playerId, StyleSlider.DETAILED);
    }

    public void overrideCluster(long playerId, Cluster cluster) {
        if (cluster != null) {
            overrideCluster.put(playerId, cluster);
        }
    }

    public PersonaProfile resolve(long playerId, PersonaSignals signals) {
        Cluster c = overrideCluster.getOrDefault(playerId, cluster(signals));
        StyleSlider style = styleOf(playerId);
        String fragment = buildPromptFragment(c, style);
        Map<String, Object> tags = new LinkedHashMap<>();
        tags.put("cluster", c.name());
        tags.put("style", style.name());
        if (signals != null) {
            tags.put("handbookProgress", signals.handbookProgress());
            tags.put("regionExplorePercent", signals.regionExplorePercent());
            tags.put("abyssStars", signals.abyssStars());
            tags.put("friendAssistCount", signals.friendAssistCount());
            tags.put("chatMessages7d", signals.chatMessages7d());
        }
        tags.put("redisHint", "persona:" + playerId);
        return new PersonaProfile(playerId, c, style, fragment, tags);
    }

    public Map<String, Object> toMap(PersonaProfile p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("playerId", p.playerId());
        m.put("cluster", p.cluster().name());
        m.put("style", p.style().name());
        m.put("systemPromptFragment", p.systemPromptFragment());
        m.put("PLAYER_PERSONA", p.systemPromptFragment());
        m.put("tags", p.tags());
        return m;
    }

    public Map<String, Object> resolveFromRequest(Map<String, Object> body) {
        long playerId = body != null && body.get("playerId") instanceof Number n ? n.longValue() : 0L;
        if (body != null && body.get("style") != null) {
            setStyle(playerId, String.valueOf(body.get("style")));
        }
        PersonaSignals signals = new PersonaSignals(
                num(body, "handbookProgress", 0.4),
                num(body, "regionExplorePercent", 35),
                (int) num(body, "abyssStars", 3),
                (int) num(body, "friendAssistCount", 1),
                (int) num(body, "chatMessages7d", 5));
        return toMap(resolve(playerId, signals));
    }

    /**
     * 按人格改写建议语气（供顾问 / 战术 / 分析师复用）。
     */
    public String adaptAdvice(PersonaProfile profile, String rawAdvice) {
        if (rawAdvice == null || rawAdvice.isBlank()) {
            return "";
        }
        Cluster c = profile == null ? Cluster.CASUAL : profile.cluster();
        StyleSlider style = profile == null ? StyleSlider.DETAILED : profile.style();
        String base = switch (c) {
            case HARDCORE -> rawAdvice + "（帧数窗口与期望请对照伤害期望表。）";
            case SOCIAL -> "跟队友同步一下节奏—— " + rawAdvice;
            case CASUAL -> "慢慢来就好：" + soften(rawAdvice);
        };
        return switch (style) {
            case CONCISE -> truncate(base, 48);
            case EMOTIONAL -> "嗯……" + base + " 我相信你可以。";
            case DETAILED -> base;
        };
    }

    public static StyleSlider parseStyle(String name) {
        if (name == null || name.isBlank()) {
            return StyleSlider.DETAILED;
        }
        String n = name.trim().toUpperCase(Locale.ROOT);
        return switch (n) {
            case "CONCISE", "简洁", "简约" -> StyleSlider.CONCISE;
            case "EMOTIONAL", "感性", "温柔" -> StyleSlider.EMOTIONAL;
            default -> StyleSlider.DETAILED;
        };
    }

    private static String buildPromptFragment(Cluster c, StyleSlider style) {
        String clusterLine = switch (c) {
            case CASUAL -> "对 CASUAL：语气温柔，推荐简单跑图路线，少提数值。";
            case HARDCORE -> "对 HARDCORE：语气直接，给出精确的帧数窗口和伤害期望表格。";
            case SOCIAL -> "对 SOCIAL：强调协作与助战，鼓励组队与分享路线。";
        };
        String styleLine = switch (style) {
            case CONCISE -> "交流风格=简洁，单段≤2 句。";
            case DETAILED -> "交流风格=详细，可列步骤与理由。";
            case EMOTIONAL -> "交流风格=感性，带共情与鼓励。";
        };
        return "{{PLAYER_PERSONA}}\n" + clusterLine + "\n" + styleLine;
    }

    private static String soften(String s) {
        return s.replace("必须", "可以试试").replace("立刻", "有空时");
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private static double num(Map<String, Object> m, String key, double def) {
        if (m == null) {
            return def;
        }
        Object v = m.get(key);
        return v instanceof Number n ? n.doubleValue() : def;
    }
}
