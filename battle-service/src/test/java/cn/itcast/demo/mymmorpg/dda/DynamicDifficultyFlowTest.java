package cn.itcast.demo.mymmorpg.dda;

import cn.itcast.demo.mymmorpg.ai.AiDecisionLogger;
import cn.itcast.demo.mymmorpg.metrics.AiMetrics;
import cn.itcast.demo.mymmorpg.telemetry.TrainingDataCollector;
import cn.itcast.demo.mymmorpg.tlog.TLogEventPublisher;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DDA 业务流程：多次战斗采样 → 评估修正 → 训练埋点 → AI_DECISION TLog。
 */
public class DynamicDifficultyFlowTest {

    @Test
    public void overpoweredSeries_raisesDifficulty_andEmitsTlog() {
        List<Map<String, Object>> tlogEvents = new CopyOnWriteArrayList<>();
        TLogEventPublisher tlog = (type, playerId, fields) -> {
            Map<String, Object> row = new java.util.LinkedHashMap<>(fields);
            row.put("eventType", type);
            row.put("playerId", playerId);
            tlogEvents.add(row);
        };
        TrainingDataCollector training = new TrainingDataCollector();
        DynamicDifficultyService svc = new DynamicDifficultyService(
                new AiMetrics(), new AiDecisionLogger(tlog), training);

        long playerId = 1001L;
        for (int i = 0; i < 5; i++) {
            svc.recordBattleResult(playerId, 2800 + i * 50, true, 50_000L + i * 1000);
        }

        Map<String, Object> snap = svc.snapshot(playerId);
        assertThat(snap.get("samples")).isEqualTo(5);
        assertThat(((Number) snap.get("overallDifficulty")).doubleValue()).isGreaterThan(1.0);
        assertThat(((Number) snap.get("skillFrequencyMul")).doubleValue()).isGreaterThan(1.0);
        assertThat(snap.get("experiment")).isIn("baseline", "aggressive");
        assertThat(snap).containsKey("strategyTier");
        @SuppressWarnings("unchecked")
        Map<String, Object> tier = (Map<String, Object>) snap.get("strategyTier");
        assertThat(tier.get("tier")).isIn("STANDARD", "VETERAN", "ELITE");

        List<TrainingDataCollector.Event> flushed = training.flush(10);
        assertThat(flushed).hasSize(5);
        assertThat(flushed.get(0).category()).isEqualTo("battle_result");

        assertThat(tlogEvents).isNotEmpty();
        assertThat(tlogEvents.get(tlogEvents.size() - 1).get("eventType"))
                .isEqualTo(AiDecisionLogger.EVENT_TYPE);
        assertThat(tlogEvents.get(tlogEvents.size() - 1).get("component")).isEqualTo("dda");
    }

    @Test
    public void strugglingSeries_boostsRewardMul() {
        DynamicDifficultyService svc = new DynamicDifficultyService();
        long playerId = 1002L;
        for (int i = 0; i < 4; i++) {
            svc.recordBattleResult(playerId, 350, false, 420_000L);
        }
        var adj = svc.evaluate(playerId);
        assertThat(adj.overallDifficulty()).isLessThan(1.0);
        assertThat(adj.rewardMul()).isGreaterThanOrEqualTo(1.0);
        assertThat(adj.reason()).contains("struggling");
    }

    @Test
    public void abVariant_stableForSamePlayer() {
        DynamicDifficultyService svc = new DynamicDifficultyService();
        String a = String.valueOf(svc.snapshot(55L).get("experiment"));
        String b = String.valueOf(svc.snapshot(55L).get("experiment"));
        assertThat(a).isEqualTo(b);
    }
}
