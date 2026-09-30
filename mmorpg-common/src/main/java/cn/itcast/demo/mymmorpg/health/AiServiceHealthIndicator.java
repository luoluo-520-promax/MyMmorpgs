package cn.itcast.demo.mymmorpg.health;

import cn.itcast.demo.mymmorpg.ai.AiCostMeter;
import cn.itcast.demo.mymmorpg.metrics.AiMetrics;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * AI 服务健康检查：LLM 成功率过低或延迟过高时标记 DOWN，供熔断/降级使用。
 */
public class AiServiceHealthIndicator implements HealthIndicator {

    private final AiMetrics metrics;
    private final AiCostMeter costMeter;
    private final double minSuccessRate;
    private final double maxBtAvgLatencyMs;
    private final boolean llmEnabled;

    public AiServiceHealthIndicator(AiMetrics metrics, AiCostMeter costMeter,
                                    boolean llmEnabled, double minSuccessRate, double maxBtAvgLatencyMs) {
        this.metrics = metrics == null ? new AiMetrics() : metrics;
        this.costMeter = costMeter == null ? new AiCostMeter() : costMeter;
        this.llmEnabled = llmEnabled;
        this.minSuccessRate = minSuccessRate;
        this.maxBtAvgLatencyMs = maxBtAvgLatencyMs;
    }

    public AiServiceHealthIndicator(AiMetrics metrics, AiCostMeter costMeter) {
        this(metrics, costMeter, true, 0.5, 50.0);
    }

    @Override
    public Health health() {
        double successRate = metrics.llmSuccessRate();
        double btAvg = metrics.btAvgLatencyMs();
        boolean degraded = false;
        String reason = "ok";
        if (llmEnabled && metrics.snapshot().get("llmCalls") instanceof Number n && n.longValue() >= 5
                && successRate < minSuccessRate) {
            degraded = true;
            reason = "llm_success_rate_low";
        } else if (btAvg > maxBtAvgLatencyMs) {
            degraded = true;
            reason = "bt_latency_high";
        } else if (costMeter.isOverBudget()) {
            degraded = true;
            reason = "token_budget_exceeded";
        }
        Health.Builder b = degraded ? Health.down() : Health.up();
        return b.withDetail("reason", reason)
                .withDetail("llmEnabled", llmEnabled)
                .withDetail("llmSuccessRate", successRate)
                .withDetail("btAvgLatencyMs", btAvg)
                .withDetail("decisionQps", metrics.decisionQps())
                .withDetail("cost", costMeter.snapshot())
                .withDetail("metrics", metrics.snapshot())
                .build();
    }
}
