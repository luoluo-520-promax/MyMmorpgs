package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.ml.ModelVersionRegistry;
import cn.itcast.demo.mymmorpg.service.ai.AiDraftVersionStore;
import cn.itcast.demo.mymmorpg.telemetry.TrainingDataCollector;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Admin AI 运营闭环：预测干预建议 → 草稿审核发布 → 模型版本回滚。
 */
public class AdminAiOpsBusinessFlowTest {

    private static AdminAiPlatformBridge testBridge() {
        return new AdminAiPlatformBridge(false, EmptyAiPlatformClientProvider.INSTANCE);
    }

    @Test
    public void churnPrediction_suggestsRetentionAction() {
        AdminPlayerPredictionService svc = new AdminPlayerPredictionService(testBridge());
        Map<String, Object> out = svc.predict(9001L, Map.of(
                "loginDays7", 0,
                "questCompletionRate", 0.05,
                "onlineMinutes7", 5,
                "rechargeAmount30d", 0,
                "rechargeCount30d", 0,
                "daysSinceLastLogin", 12));
        assertThat(out.get("ok")).isEqualTo(true);
        assertThat(((Number) out.get("churnRisk")).doubleValue()).isGreaterThan(0.5);
        assertThat(out.get("suggestedAction")).isIn("push_retention_quest", "push_login_bonus");
        @SuppressWarnings("unchecked")
        Map<String, Object> hook = (Map<String, Object>) out.get("activityHook");
        assertThat(hook.get("suggestedAction")).isEqualTo(out.get("suggestedAction"));

        TrainingDataCollector td = svc.trainingData();
        assertThat(td.flush(5)).isNotEmpty();
    }

    @Test
    public void payIntent_suggestsOffer() {
        AdminPlayerPredictionService svc = new AdminPlayerPredictionService(testBridge());
        Map<String, Object> out = svc.predict(9002L, Map.of(
                "loginDays7", 7,
                "questCompletionRate", 0.95,
                "onlineMinutes7", 800,
                "rechargeAmount30d", 8000,
                "rechargeCount30d", 10,
                "daysSinceLastLogin", 0));
        assertThat(((Number) out.get("payIntent")).doubleValue()).isGreaterThan(0.5);
        assertThat(out.get("suggestedAction")).isIn("push_limited_offer", "none", "push_login_bonus");
    }

    @Test
    public void draftApprovePublish_andRejectPath() {
        AiDraftVersionStore store = new AiDraftVersionStore();
        AiDraftVersionStore.DraftVersion d = store.create(1L, "activity", "{\"type\":\"SIGN\"}", "v2");
        assertThat(store.submit(d.draftId(), 1L)).isPresent();
        assertThat(store.reject(d.draftId(), 99L, "数值过高")).isPresent();
        assertThat(store.get(d.draftId()).orElseThrow().status()).isEqualTo("REJECTED");

        AiDraftVersionStore.DraftVersion d2 = store.create(1L, "quest", "{\"name\":\"q\"}", "v2");
        assertThat(store.submit(d2.draftId(), 1L)).isPresent();
        assertThat(store.approve(d2.draftId(), 2L, "ok")).isPresent();
        assertThat(store.publish(d2.draftId(), 1L)).isPresent();
        assertThat(store.get(d2.draftId()).orElseThrow().history().size()).isGreaterThanOrEqualTo(3);

        assertThat(store.revise(d2.draftId(), 1L, "{\"name\":\"q2\"}")).isPresent();
        assertThat(store.get(d2.draftId()).orElseThrow().status()).isEqualTo("DRAFT");
        assertThat(store.get(d2.draftId()).orElseThrow().version()).isGreaterThan(1);
    }

    @Test
    public void modelRegisterRollbackFlow() {
        AdminPlayerPredictionService svc = new AdminPlayerPredictionService(testBridge());
        ModelVersionRegistry reg = new ModelVersionRegistry();
        reg.register("churn_xgb", "rules-v1", "local://a");
        reg.register("churn_xgb", "xgb-v2", "s3://models/churn-v2");
        assertThat(reg.current("churn_xgb").orElseThrow().version()).isEqualTo("xgb-v2");
        assertThat(reg.rollback("churn_xgb", "rules-v1")).isPresent();
        assertThat(reg.current("churn_xgb").orElseThrow().version()).isEqualTo("rules-v1");

        Map<String, Object> rb = svc.rollbackModel("churn_xgb", "rules-v1");
        assertThat(rb.get("ok")).isEqualTo(true);
        assertThat(svc.rollbackModel("missing", "v0").get("ok")).isEqualTo(false);
    }
}
