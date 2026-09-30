package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.analytics.PlayerRiskScorer;
import cn.itcast.demo.mymmorpg.ml.ModelVersionRegistry;
import cn.itcast.demo.mymmorpg.telemetry.TrainingDataCollector;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 玩家行为预测：规则评分占位，可替换为离线 XGBoost；输出运营干预建议。
 */
@Service
public class AdminPlayerPredictionService {

    private final PlayerRiskScorer scorer = new PlayerRiskScorer();
    private final ModelVersionRegistry modelRegistry = new ModelVersionRegistry();
    private final TrainingDataCollector trainingData = new TrainingDataCollector();
    private final AdminAiPlatformBridge aiPlatformBridge;

    public AdminPlayerPredictionService(AdminAiPlatformBridge aiPlatformBridge) {
        this.aiPlatformBridge = aiPlatformBridge;
        modelRegistry.register("churn_xgb", "rules-v1", "local://rules/churn");
        modelRegistry.register("pay_xgb", "rules-v1", "local://rules/pay");
    }

    public Map<String, Object> predict(long playerId, Map<String, Object> body) {
        PlayerRiskScorer.Features features = toFeatures(body);
        PlayerRiskScorer.Prediction pred = scorer.predict(features);
        trainingData.collect("prediction_request", playerId, Map.of(
                "churnRisk", pred.churnRisk(),
                "payIntent", pred.payIntent(),
                "action", pred.suggestedAction()));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("playerId", playerId);
        out.put("churnRisk", pred.churnRisk());
        out.put("payIntent", pred.payIntent());
        out.put("churnBucket", pred.churnBucket());
        out.put("payBucket", pred.payBucket());
        out.put("suggestedAction", pred.suggestedAction());
        out.put("weights", pred.weights());
        out.put("modelVersions", modelRegistry.snapshot());
        out.put("activityHook", Map.of(
                "suggestedAction", pred.suggestedAction(),
                "hint", "可联动 activity-service 推送优惠/引导任务"));
        out.put("intervention", aiPlatformBridge.retentionEvaluate(playerId, body == null ? Map.of() : body));
        return out;
    }

    public Map<String, Object> modelSnapshot() {
        return modelRegistry.snapshot();
    }

    public Map<String, Object> rollbackModel(String modelName, String version) {
        return modelRegistry.rollback(modelName, version)
                .<Map<String, Object>>map(v -> Map.of("ok", true, "model", v.modelName(), "version", v.version()))
                .orElse(Map.of("ok", false, "error", "version_not_found"));
    }

    public TrainingDataCollector trainingData() {
        return trainingData;
    }

    private static PlayerRiskScorer.Features toFeatures(Map<String, Object> body) {
        if (body == null) {
            body = Map.of();
        }
        return new PlayerRiskScorer.Features(
                num(body, "loginDays7", 3),
                num(body, "questCompletionRate", 0.5),
                num(body, "onlineMinutes7", 120),
                num(body, "rechargeAmount30d", 0),
                num(body, "rechargeCount30d", 0),
                (long) num(body, "daysSinceLastLogin", 1));
    }

    private static double num(Map<String, Object> body, String key, double def) {
        Object v = body.get(key);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v != null) {
            try {
                return Double.parseDouble(String.valueOf(v));
            } catch (Exception ignored) {
                return def;
            }
        }
        return def;
    }
}
