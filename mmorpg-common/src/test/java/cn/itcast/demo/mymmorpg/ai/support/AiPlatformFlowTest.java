package cn.itcast.demo.mymmorpg.ai.support;

import cn.itcast.demo.mymmorpg.ai.content.ContentQualityValidator;
import cn.itcast.demo.mymmorpg.ai.ops.RetentionInterventionEngine;
import cn.itcast.demo.mymmorpg.ai.tactical.TacticalAdvisorEngine;
import cn.itcast.demo.mymmorpg.analytics.PlayerRiskScorer;
import cn.itcast.demo.mymmorpg.dda.BossStrategyTier;
import cn.itcast.demo.mymmorpg.dda.DifficultyEvaluator;
import cn.itcast.demo.mymmorpg.ml.ModelDriftMonitor;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.Set;

public class AiPlatformFlowTest {

    @Test
    public void faqRagAndGuard() {
        FaqRagService rag = new FaqRagService();
        rag.upsertDoc("d1", "升级", "优先完成主线与日常委托");
        rag.putFaqCache("怎么升级快", "缓存答案");
        Map<String, Object> hit = rag.answer("怎么升级快", true);
        Assert.assertEquals(hit.get("source"), "faq_cache");
        AiContentGuard guard = new AiContentGuard();
        Assert.assertFalse(guard.check("请使用外挂").allowed());
        Assert.assertTrue(guard.check("你好世界").allowed());
    }

    @Test
    public void contentValidateAndRetentionTacticalDda() {
        ContentQualityValidator v = new ContentQualityValidator(Set.of("item_mora"), Set.of());
        Map<String, Object> draft = v.generateDraft("activity", "春节", Map.of("rewardItemId", "item_mora"));
        Assert.assertTrue(v.validate(draft).pass());

        RetentionInterventionEngine ret = new RetentionInterventionEngine(0.5);
        var i = ret.evaluate(9, new PlayerRiskScorer.Features(1, 0.1, 20, 0, 0, 14));
        Assert.assertTrue(i.triggered());

        TacticalAdvisorEngine tac = new TacticalAdvisorEngine();
        var advice = tac.advise(new TacticalAdvisorEngine.Battlefield(
                0.2, 0.9, 0.8, true, "火", 0, false, null));
        Assert.assertEquals(advice.priority(), "break_shield");

        DifficultyEvaluator.Adjustment adj = new DifficultyEvaluator()
                .evaluate(new DifficultyEvaluator.BattleStats(2000, 0.95, 60_000, 180_000, 1000));
        Map<String, Object> tiered = BossStrategyTier.merge(adj);
        Assert.assertTrue(tiered.containsKey("tier"));

        ModelDriftMonitor drift = new ModelDriftMonitor(0.05);
        drift.setBaseline("m1", 0.9);
        for (int n = 0; n < 30; n++) {
            drift.record("m1", n % 5 == 0);
        }
        Assert.assertTrue(drift.check("m1").alert());
    }

    @Test
    public void llmQuota() {
        LlmDailyQuota q = new LlmDailyQuota(2, java.time.ZoneId.of("UTC"));
        Assert.assertTrue(q.tryAcquire(1));
        Assert.assertTrue(q.tryAcquire(1));
        Assert.assertFalse(q.tryAcquire(1));
        Assert.assertEquals(q.remaining(1), 0);
    }
}
