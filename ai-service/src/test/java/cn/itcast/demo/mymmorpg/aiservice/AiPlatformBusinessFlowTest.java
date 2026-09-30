package cn.itcast.demo.mymmorpg.aiservice;

import cn.itcast.demo.mymmorpg.ai.content.ActivityParamOptimizer;
import cn.itcast.demo.mymmorpg.ai.content.ContentQualityValidator;
import cn.itcast.demo.mymmorpg.ai.gateway.AiInferenceGateway;
import cn.itcast.demo.mymmorpg.ai.gateway.AiInferenceRequest;
import cn.itcast.demo.mymmorpg.ai.gateway.AiInferenceResponse;
import cn.itcast.demo.mymmorpg.ai.analyst.CombatAnalystService;
import cn.itcast.demo.mymmorpg.ai.companion.CompanionBotService;
import cn.itcast.demo.mymmorpg.ai.narrative.DynamicNarrativeService;
import cn.itcast.demo.mymmorpg.ai.npc.NpcEmotionVector;
import cn.itcast.demo.mymmorpg.ai.npc.NpcLongTermMemory;
import cn.itcast.demo.mymmorpg.ai.ops.RetentionInterventionEngine;
import cn.itcast.demo.mymmorpg.ai.persona.DynamicPersonaEngine;
import cn.itcast.demo.mymmorpg.ai.platform.AiPlatformFacade;
import cn.itcast.demo.mymmorpg.ai.recommend.RecommendationEngine;
import cn.itcast.demo.mymmorpg.ai.support.AiContentGuard;
import cn.itcast.demo.mymmorpg.ai.support.FaqRagService;
import cn.itcast.demo.mymmorpg.ai.support.LlmDailyQuota;
import cn.itcast.demo.mymmorpg.ai.tactical.TacticalAdvisorEngine;
import cn.itcast.demo.mymmorpg.ai.vision.ScreenWaypointTranslator;
import cn.itcast.demo.mymmorpg.analytics.PlayerRiskScorer;
import cn.itcast.demo.mymmorpg.aiservice.config.AiPlatformConfiguration;
import cn.itcast.demo.mymmorpg.ml.ModelDriftMonitor;
import cn.itcast.demo.mymmorpg.ml.ModelVersionRegistry;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 平台端到端业务流程：
 * 画像→推荐→NPC 关系→客服→流失干预→战术→内容生成→活动优化→MLOps。
 */
public class AiPlatformBusinessFlowTest {

    private AnnotationConfigApplicationContext ctx;

    @BeforeMethod
    public void setUp() {
        System.setProperty("spring.application.name", "ai-service");
        ctx = new AnnotationConfigApplicationContext();
        ctx.register(AiPlatformConfiguration.class);
        ctx.refresh();
    }

    @AfterMethod
    public void tearDown() {
        if (ctx != null) {
            ctx.close();
        }
        System.clearProperty("spring.application.name");
    }

    @Test
    public void fullPlayerJourney_recommendSupportRetentionTactical() {
        RecommendationEngine recommend = ctx.getBean(RecommendationEngine.class);
        FaqRagService faq = ctx.getBean(FaqRagService.class);
        AiContentGuard guard = ctx.getBean(AiContentGuard.class);
        RetentionInterventionEngine retention = ctx.getBean(RetentionInterventionEngine.class);
        TacticalAdvisorEngine tactical = ctx.getBean(TacticalAdvisorEngine.class);
        LlmDailyQuota quota = ctx.getBean(LlmDailyQuota.class);

        long playerId = 9001L;

        // 1) 行为采集 → 画像 → 推荐
        recommend.recordInteraction(playerId, "relic_gladiator", "combat");
        recommend.recordInteraction(playerId, "quest_abyss_weekly", "combat");
        Map<String, Object> rec = recommend.recommendPayload(playerId, null, 5);
        assertThat(rec.get("ok")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) rec.get("items");
        assertThat(items).isNotEmpty();
        assertThat(items.get(0)).containsKeys("itemId", "score", "reason");

        // 2) 智能客服：FAQ 缓存命中
        Map<String, Object> faqHit = faq.answer("怎么升级快", true);
        assertThat(faqHit.get("source")).isEqualTo("faq_cache");
        assertThat(guard.check(String.valueOf(faqHit.get("answer"))).allowed()).isTrue();

        // 3) 误封申诉 → 工单转接
        Map<String, Object> ticket = faq.answer("我要误封申诉", true);
        assertThat(ticket.get("needTicket")).isEqualTo(true);
        assertThat(String.valueOf(ticket.get("source"))).contains("ticket");

        // 4) 流失干预
        RetentionInterventionEngine.Intervention i = retention.evaluate(playerId,
                new PlayerRiskScorer.Features(1, 0.1, 30, 0, 0, 14));
        assertThat(i.triggered()).isTrue();
        assertThat(i.giftPackId()).isNotBlank();

        // 5) 实时战术：破盾优先
        TacticalAdvisorEngine.Advice advice = tactical.advise(new TacticalAdvisorEngine.Battlefield(
                0.6, 0.8, 0.5, true, "火", 1, false, null));
        assertThat(advice.priority()).isEqualTo("break_shield");
        assertThat(advice.hint()).contains("火");

        // 6) LLM 日限
        assertThat(quota.tryAcquire(playerId)).isTrue();
        assertThat(quota.remaining(playerId)).isLessThan(50);
    }

    @Test
    public void npcBondFlow_giftAndQuestUnlockAssist() {
        NpcLongTermMemory memory = ctx.getBean(NpcLongTermMemory.class);

        long playerId = 42L;
        String npcId = "katherine";

        memory.interact(playerId, npcId, NpcEmotionVector.InteractionKind.GIFT, "送花", 0.9);
        memory.interact(playerId, npcId, NpcEmotionVector.InteractionKind.QUEST_HELP, "帮忙清怪", 0.7);
        memory.interact(playerId, npcId, NpcEmotionVector.InteractionKind.COMBAT_ASSIST, "并肩作战", 0.6);

        Map<String, Object> bond = memory.toMap(playerId, npcId);
        assertThat(bond.get("giftCount")).isEqualTo(1);
        assertThat(bond.get("questHelps")).isEqualTo(1);
        assertThat(bond.get("combatAssists")).isEqualTo(1);
        @SuppressWarnings("unchecked")
        Map<String, Double> weights = (Map<String, Double>) bond.get("behaviorWeights");
        assertThat(weights.get("assist")).isGreaterThan(0.4);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> recent = (List<Map<String, Object>>) bond.get("recentMemories");
        assertThat(recent).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    public void contentGenerateValidateAndActivityOptimizeLoop() {
        ContentQualityValidator validator = ctx.getBean(ContentQualityValidator.class);
        ActivityParamOptimizer optimizer = ctx.getBean(ActivityParamOptimizer.class);

        String activityId = "spring_festival_2026";

        // 生成 → 校验 → 可进 Admin 草稿
        Map<String, Object> draft = validator.generateDraft("activity", "春节", Map.of(
                "rewardItemId", "item_mora", "rewardMul", 1.2));
        ContentQualityValidator.ValidationResult vr = validator.validate(draft);
        assertThat(vr.pass()).isTrue();
        assertThat(vr.qualityScore()).isGreaterThan(0.3);

        // 模拟上线反馈：曝光低 → 自动调高奖励
        for (int i = 0; i < 100; i++) {
            optimizer.recordExposure(activityId);
        }
        optimizer.recordJoin(activityId);
        optimizer.recordJoin(activityId);
        Map<String, Object> snap = optimizer.snapshot(activityId);
        assertThat(((Number) snap.get("joinRate")).doubleValue()).isLessThan(0.15);
        assertThat(((Number) snap.get("nextRewardMul")).doubleValue()).isGreaterThan(1.0);
        assertThat(snap.get("reason")).isEqualTo("low_join_boost_reward");
    }

    @Test
    public void mlopsRegisterInferDriftAndRollback() {
        AiInferenceGateway gateway = ctx.getBean(AiInferenceGateway.class);
        ModelDriftMonitor drift = ctx.getBean(ModelDriftMonitor.class);
        ModelVersionRegistry registry = gateway.versions();

        // 注册模型版本
        registry.register("churn_xgb", "v2-onnx", "s3://models/churn/v2.onnx");
        assertThat(registry.current("churn_xgb").orElseThrow().version()).isEqualTo("v2-onnx");

        // 统一推理：流失预测
        AiInferenceResponse resp = gateway.infer(AiInferenceRequest.builder()
                .modelName("churn_xgb")
                .task("churn")
                .playerId(100L)
                .feature("loginDays7", 1)
                .feature("daysSinceLastLogin", 10)
                .feature("questCompletionRate", 0.15)
                .build());
        assertThat(resp.ok()).isTrue();
        assertThat(resp.result()).isInstanceOf(Map.class);

        // 漂移监控：准确率下降触发告警
        drift.setBaseline("recommend_cf", 0.85);
        for (int n = 0; n < 30; n++) {
            drift.record("recommend_cf", n % 10 == 0);
        }
        assertThat(drift.check("recommend_cf").alert()).isTrue();

        // 回滚
        registry.register("churn_xgb", "v3", "s3://models/churn/v3.onnx");
        assertThat(registry.rollback("churn_xgb", "v2-onnx").orElseThrow().version()).isEqualTo("v2-onnx");
    }

    @Test
    public void gatewayDegradesOnBackendFailure() {
        AiInferenceGateway gateway = ctx.getBean(AiInferenceGateway.class);
        gateway.registerBackend(new cn.itcast.demo.mymmorpg.ai.gateway.AiModelBackend() {
            @Override
            public String name() {
                return "faulty-ml";
            }

            @Override
            public boolean supports(String modelName) {
                return "faulty-model".equals(modelName);
            }

            @Override
            public AiInferenceResponse infer(AiInferenceRequest request) {
                throw new IllegalStateException("model_crash");
            }
        });

        AiInferenceRequest req = AiInferenceRequest.builder()
                .modelName("faulty-model")
                .task("faq")
                .feature("question", "深渊怎么打")
                .build();

        for (int i = 0; i < 6; i++) {
            AiInferenceResponse r = gateway.infer(req);
            assertThat(r.ok()).isTrue();
            assertThat(r.degraded()).isTrue();
            assertThat(r.backend()).isEqualTo("rule-engine");
        }
        assertThat(gateway.health().get("circuitBreakers")).isNotNull();
    }

    @Test
    public void p15NarrativeCompanionAnalystPersonaVision() {
        DynamicNarrativeService narrative = ctx.getBean(DynamicNarrativeService.class);
        CompanionBotService companion = ctx.getBean(CompanionBotService.class);
        CombatAnalystService analyst = ctx.getBean(CombatAnalystService.class);
        DynamicPersonaEngine persona = ctx.getBean(DynamicPersonaEngine.class);
        ScreenWaypointTranslator vision = ctx.getBean(ScreenWaypointTranslator.class);
        AiPlatformFacade facade = ctx.getBean(AiPlatformFacade.class);

        Map<String, Object> line = narrative.generateFromRequest(Map.of(
                "playerId", 77L, "npcId", "katherine", "triggerEventId", "BOSS_CLEAR",
                "specialBagItems", List.of("龙牙碎片")));
        assertThat(line.get("ok")).isEqualTo(true);
        assertThat(line.get("emotionTag")).isNotNull();

        companion.remember(77L, "COMBAT", "AGGRESSIVE style");
        Map<String, Object> coach = companion.battleCoach(77L, "missing-squad",
                Map.of("partyHpRatio", 0.2, "bossHpRatio", 0.5));
        assertThat(coach.get("ok")).isEqualTo(true);

        Map<String, Object> report = analyst.feedback("demo", Map.of("ok", true, "frames", List.of(
                Map.of("tick", 1, "tsMs", 1L, "actionType", "SKILL", "payload", Map.of("castDelaySec", 1.0)))),
                "CASUAL", false);
        assertThat(report.get("advice")).asString().isNotBlank();

        persona.setStyle(77L, "详细");
        Map<String, Object> p = persona.resolveFromRequest(Map.of(
                "playerId", 77L, "abyssStars", 15, "regionExplorePercent", 90, "handbookProgress", 0.95));
        assertThat(p.get("cluster")).isEqualTo("HARDCORE");

        Map<String, Object> wp = vision.help(77L, "1", List.of("ruin_gate"), 350f, 0f, 100f, System.currentTimeMillis());
        assertThat(wp.get("ok")).isEqualTo(true);
        assertThat(facade.health().get("p15")).isNotNull();
    }
}
