package cn.itcast.demo.mymmorpg.ai.content;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 生成内容质量评分 + 兼容性校验（引用完整性 / 数值平衡预检）。
 */
public final class ContentQualityValidator {

    private static final Pattern ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-]{2,64}$");

    public record ValidationResult(
            boolean pass,
            double qualityScore,
            List<String> errors,
            List<String> warnings,
            Map<String, Object> metrics) {

        public ValidationResult {
            errors = errors == null ? List.of() : List.copyOf(errors);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
            metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("pass", pass);
            m.put("qualityScore", qualityScore);
            m.put("errors", errors);
            m.put("warnings", warnings);
            m.put("metrics", metrics);
            return m;
        }
    }

    private final Set<String> knownItemIds;
    private final Set<String> knownQuestIds;

    public ContentQualityValidator() {
        this(Set.of(), Set.of());
    }

    public ContentQualityValidator(Set<String> knownItemIds, Set<String> knownQuestIds) {
        this.knownItemIds = knownItemIds == null ? Set.of() : Set.copyOf(knownItemIds);
        this.knownQuestIds = knownQuestIds == null ? Set.of() : Set.copyOf(knownQuestIds);
    }

    public ValidationResult validate(Map<String, Object> draft) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (draft == null || draft.isEmpty()) {
            return new ValidationResult(false, 0, List.of("empty_draft"), List.of(), Map.of());
        }

        String type = str(draft, "type");
        String id = str(draft, "id");
        String title = str(draft, "title");
        String desc = str(draft, "description");

        if (id.isBlank() || !ID_PATTERN.matcher(id).matches()) {
            errors.add("invalid_id");
        }
        if (title.isBlank() || title.length() < 2) {
            errors.add("title_too_short");
        }
        if (desc.length() < 8) {
            warnings.add("description_thin");
        }
        if (desc.length() > 2000) {
            errors.add("description_too_long");
        }

        Object rewardItem = draft.get("rewardItemId");
        if (rewardItem != null && !knownItemIds.isEmpty() && !knownItemIds.contains(String.valueOf(rewardItem))) {
            errors.add("reward_item_missing:" + rewardItem);
        }
        Object questRef = draft.get("questId");
        if (questRef != null && !knownQuestIds.isEmpty() && !knownQuestIds.contains(String.valueOf(questRef))) {
            errors.add("quest_ref_missing:" + questRef);
        }

        double rewardMul = num(draft, "rewardMul", 1.0);
        double discount = num(draft, "discount", 1.0);
        if (rewardMul < 0.5 || rewardMul > 3.0) {
            errors.add("rewardMul_out_of_range");
        } else if (rewardMul > 2.0) {
            warnings.add("rewardMul_aggressive");
        }
        if (discount < 0.3 || discount > 1.0) {
            errors.add("discount_out_of_range");
        }

        double diversity = diversityScore(title + " " + desc);
        double completeness = (title.isBlank() ? 0 : 0.35) + (desc.length() >= 20 ? 0.35 : 0.15)
                + (id.isBlank() ? 0 : 0.15) + (type.isBlank() ? 0 : 0.15);
        double quality = Math.round((0.55 * completeness + 0.45 * diversity) * 1000.0) / 1000.0;
        if (quality < 0.35) {
            warnings.add("low_quality_score");
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("diversity", diversity);
        metrics.put("completeness", completeness);
        metrics.put("rewardMul", rewardMul);
        metrics.put("discount", discount);

        boolean pass = errors.isEmpty() && quality >= 0.3;
        return new ValidationResult(pass, quality, errors, warnings, metrics);
    }

    /**
     * 基于模板的轻量生成（LLM 可替换）；输出后必须过 validate。
     */
    public Map<String, Object> generateDraft(String type, String theme, Map<String, Object> knobs) {
        String t = type == null || type.isBlank() ? "activity" : type.toLowerCase(Locale.ROOT);
        String th = theme == null || theme.isBlank() ? "周年庆典" : theme;
        Map<String, Object> kn = knobs == null ? Map.of() : knobs;
        String id = t + "_" + Integer.toHexString((th + System.currentTimeMillis()).hashCode());
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("type", t);
        draft.put("id", id);
        draft.put("title", th + "特别企划");
        draft.put("description", "在" + th + "期间完成指定目标可领取限时奖励，记得关注活动日程与兑换商店。");
        draft.put("rewardMul", num(kn, "rewardMul", 1.2));
        draft.put("discount", num(kn, "discount", 0.85));
        if (kn.containsKey("rewardItemId")) {
            draft.put("rewardItemId", kn.get("rewardItemId"));
        }
        if (kn.containsKey("questId")) {
            draft.put("questId", kn.get("questId"));
        }
        draft.put("generator", "template-v2");
        return draft;
    }

    private static double diversityScore(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        long distinct = text.chars().distinct().count();
        return Math.min(1.0, distinct / 40.0);
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static double num(Map<String, Object> m, String key, double def) {
        Object v = m.get(key);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v != null) {
            try {
                return Double.parseDouble(v.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }
}
