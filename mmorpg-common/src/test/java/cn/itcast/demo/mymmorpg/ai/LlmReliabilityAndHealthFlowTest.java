package cn.itcast.demo.mymmorpg.ai;

import cn.itcast.demo.mymmorpg.health.AiServiceHealthIndicator;
import cn.itcast.demo.mymmorpg.metrics.AiMetrics;
import org.springframework.boot.actuate.health.Status;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LLM 成本控制 + 健康降级 + 决策日志流程。
 */
public class LlmReliabilityAndHealthFlowTest {

    @Test
    public void costBudget_thenHealthDown_thenReset() {
        AiCostMeter meter = new AiCostMeter();
        meter.setBudgetTokens(50);
        meter.recordSuccess(30, 10);
        assertThat(meter.isOverBudget()).isFalse();
        meter.recordSuccess(20, 5);
        assertThat(meter.isOverBudget()).isTrue();
        assertThat(((Number) meter.snapshot().get("budgetAlerts")).longValue()).isGreaterThan(0);

        AiMetrics metrics = new AiMetrics();
        for (int i = 0; i < 6; i++) {
            metrics.recordLlm(false, 5_000_000L);
        }
        AiServiceHealthIndicator ind = new AiServiceHealthIndicator(metrics, meter, true, 0.5, 50.0);
        assertThat(ind.health().getStatus()).isEqualTo(Status.DOWN);

        meter.reset();
        assertThat(meter.isOverBudget()).isFalse();
        assertThat(meter.totalTokens()).isZero();
    }

    @Test
    public void decisionLogger_emitsToTlog() {
        AtomicInteger calls = new AtomicInteger();
        List<String> types = new ArrayList<>();
        AiDecisionLogger logger = new AiDecisionLogger((type, playerId, fields) -> {
            calls.incrementAndGet();
            types.add(type);
            assertThat(fields.get("component")).isEqualTo("bt");
            assertThat(playerId).isEqualTo(8L);
        });
        logger.log("bt", 8L, "enrage", "hp_low", Map.of("hp", 0.2), Map.of("skill", "aoe"));
        assertThat(calls.get()).isEqualTo(1);
        assertThat(types).containsExactly(AiDecisionLogger.EVENT_TYPE);
    }

    @Test
    public void aiMetrics_recordsBtLlmNpc() {
        AiMetrics m = new AiMetrics();
        m.recordBtTick(1_000_000L);
        m.recordLlm(true, 2_000_000L);
        m.recordNpc(3_000_000L);
        m.recordDecision("dda");
        Map<String, Object> snap = m.snapshot();
        assertThat(((Number) snap.get("btTicks")).longValue()).isEqualTo(1);
        assertThat(((Number) snap.get("llmSuccess")).longValue()).isEqualTo(1);
        assertThat(((Number) snap.get("npcResponses")).longValue()).isEqualTo(1);
        assertThat(((Number) snap.get("decisions")).longValue()).isGreaterThanOrEqualTo(4);
        assertThat(m.llmSuccessRate()).isEqualTo(1.0);
    }
}
