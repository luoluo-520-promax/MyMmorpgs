package cn.itcast.demo.mymmorpg.ai.gateway;

import cn.itcast.demo.mymmorpg.analytics.PlayerRiskScorer;
import cn.itcast.demo.mymmorpg.dda.DifficultyEvaluator;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 默认规则后端：覆盖推荐打分、流失预测、DDA、FAQ 等确定性任务。
 */
public final class RuleEngineBackend implements AiModelBackend {

    public static final String NAME = "rule-engine";

    private final PlayerRiskScorer riskScorer = new PlayerRiskScorer();
    private final DifficultyEvaluator difficultyEvaluator = new DifficultyEvaluator();

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean supports(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return true;
        }
        String m = modelName.toLowerCase(Locale.ROOT);
        return m.startsWith("rule") || m.contains("churn") || m.contains("dda")
                || m.contains("recommend") || m.contains("faq") || m.contains("tactical");
    }

    @Override
    public AiInferenceResponse infer(AiInferenceRequest request) {
        long t0 = System.nanoTime();
        String task = request.task() == null ? "" : request.task().toLowerCase(Locale.ROOT);
        Object result = switch (task) {
            case "churn", "pay", "risk" -> predictRisk(request.features());
            case "dda" -> evaluateDda(request.features());
            case "recommend" -> scoreRecommend(request.features());
            case "faq" -> faqAnswer(request.features());
            case "tactical" -> tacticalHint(request.features());
            default -> Map.of("echo", request.features(), "hint", "unknown_task_use_rule_default");
        };
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        return AiInferenceResponse.success(request.modelName(), "rules-v1", NAME, result, ms);
    }

    private Map<String, Object> predictRisk(Map<String, Object> f) {
        PlayerRiskScorer.Features features = new PlayerRiskScorer.Features(
                num(f, "loginDays7", 3),
                num(f, "questCompletionRate", 0.5),
                num(f, "onlineMinutes7", 120),
                num(f, "rechargeAmount30d", 0),
                num(f, "rechargeCount30d", 0),
                (long) num(f, "daysSinceLastLogin", 1));
        PlayerRiskScorer.Prediction p = riskScorer.predict(features);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("churnRisk", p.churnRisk());
        out.put("payIntent", p.payIntent());
        out.put("churnBucket", p.churnBucket());
        out.put("payBucket", p.payBucket());
        out.put("suggestedAction", p.suggestedAction());
        out.put("weights", p.weights());
        return out;
    }

    private Map<String, Object> evaluateDda(Map<String, Object> f) {
        DifficultyEvaluator.Adjustment adj = difficultyEvaluator.evaluate(new DifficultyEvaluator.BattleStats(
                num(f, "dps", 1000),
                num(f, "survivalRate", 0.7),
                (long) num(f, "clearTimeMs", 180_000),
                (long) num(f, "expectedClearTimeMs", 180_000),
                num(f, "expectedDps", 1000)));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("skillFrequencyMul", adj.skillFrequencyMul());
        out.put("mobCountMul", adj.mobCountMul());
        out.put("rewardMul", adj.rewardMul());
        out.put("overallDifficulty", adj.overallDifficulty());
        out.put("reason", adj.reason());
        out.put("weights", adj.weights());
        return out;
    }

    private Map<String, Object> scoreRecommend(Map<String, Object> f) {
        double affinity = num(f, "affinity", 0.5);
        double rarity = num(f, "rarity", 0.5);
        double freshness = num(f, "freshness", 0.5);
        double score = 0.45 * affinity + 0.30 * rarity + 0.25 * freshness;
        return Map.of("score", Math.round(score * 1000.0) / 1000.0, "rankHint", score >= 0.7 ? "top" : "mid");
    }

    private Map<String, Object> faqAnswer(Map<String, Object> f) {
        String q = String.valueOf(f.getOrDefault("question", ""));
        String lower = q.toLowerCase(Locale.ROOT);
        String answer;
        if (lower.contains("升级") || lower.contains("level")) {
            answer = "优先完成主线与日常委托，合理分配体力可更快升级。";
        } else if (lower.contains("深渊") || lower.contains("abyss")) {
            answer = "深渊建议配置元素共鸣队伍，优先破盾再输出。";
        } else if (lower.contains("充值") || lower.contains("pay")) {
            answer = "充值问题请联系人工客服，勿在聊天中透露订单号。";
        } else {
            answer = "你可以问我：升级、深渊、活动参与。复杂问题可转人工客服。";
        }
        return Map.of("answer", answer, "source", "faq-rule");
    }

    private Map<String, Object> tacticalHint(Map<String, Object> f) {
        double bossHp = num(f, "bossHpRatio", 1.0);
        boolean shield = Boolean.TRUE.equals(f.get("hasElementShield"))
                || "true".equalsIgnoreCase(String.valueOf(f.getOrDefault("hasElementShield", "false")));
        String element = String.valueOf(f.getOrDefault("weakElement", "火"));
        String hint;
        if (shield) {
            hint = "使用" + element + "元素破盾，再集中输出。";
        } else if (bossHp < 0.3) {
            hint = "Boss 残血，保留爆发技能集火核心部位。";
        } else if (bossHp > 0.8) {
            hint = "开场注意走位，优先清杂兵再集火 Boss。";
        } else {
            hint = "保持输出循环，注意躲避红圈技能。";
        }
        return Map.of("hint", hint, "priority", shield ? "break_shield" : "dps");
    }

    private static double num(Map<String, Object> f, String key, double def) {
        Object v = f.get(key);
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
