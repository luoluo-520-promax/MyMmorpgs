package cn.itcast.demo.mymmorpg.analytics;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 玩家流失 / 付费意向规则评分（离线 ML 占位；可替换为 XGBoost/Onnx）。
 */
public final class PlayerRiskScorer {

    public record Features(
            double loginDays7,
            double questCompletionRate,
            double onlineMinutes7,
            double rechargeAmount30d,
            double rechargeCount30d,
            long daysSinceLastLogin) {
    }

    public record Prediction(
            double churnRisk,
            double payIntent,
            String churnBucket,
            String payBucket,
            String suggestedAction,
            Map<String, Double> weights) {
    }

    public Prediction predict(Features f) {
        if (f == null) {
            return new Prediction(0.5, 0.2, "UNKNOWN", "UNKNOWN", "none", Map.of());
        }
        double inactivity = clamp(f.daysSinceLastLogin() / 7.0, 0, 1.5);
        double lowLogin = clamp(1.0 - f.loginDays7() / 7.0, 0, 1);
        double lowQuest = clamp(1.0 - f.questCompletionRate(), 0, 1);
        double lowOnline = clamp(1.0 - f.onlineMinutes7() / (7 * 60.0), 0, 1);

        double churn = clamp(0.35 * inactivity + 0.25 * lowLogin + 0.25 * lowQuest + 0.15 * lowOnline, 0, 1);
        double pay = clamp(
                0.40 * Math.log1p(f.rechargeAmount30d()) / Math.log(1 + 10_000)
                        + 0.25 * clamp(f.rechargeCount30d() / 5.0, 0, 1)
                        + 0.20 * (1.0 - churn)
                        + 0.15 * clamp(f.onlineMinutes7() / (7 * 90.0), 0, 1),
                0, 1);

        String churnBucket = churn >= 0.7 ? "HIGH" : churn >= 0.4 ? "MID" : "LOW";
        String payBucket = pay >= 0.6 ? "HIGH" : pay >= 0.3 ? "MID" : "LOW";
        String action;
        if (churn >= 0.7 && pay < 0.4) {
            action = "push_retention_quest";
        } else if (pay >= 0.6 && churn < 0.5) {
            action = "push_limited_offer";
        } else if (churn >= 0.5) {
            action = "push_login_bonus";
        } else {
            action = "none";
        }

        Map<String, Double> weights = new LinkedHashMap<>();
        weights.put("inactivity", inactivity);
        weights.put("lowLogin", lowLogin);
        weights.put("lowQuest", lowQuest);
        weights.put("lowOnline", lowOnline);
        weights.put("churn", churn);
        weights.put("pay", pay);

        return new Prediction(round3(churn), round3(pay), churnBucket, payBucket, action, weights);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
