package cn.itcast.demo.mymmorpg.health;

import cn.itcast.demo.mymmorpg.ai.AiCostMeter;
import cn.itcast.demo.mymmorpg.metrics.AiMetrics;
import org.springframework.boot.actuate.health.Status;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AiServiceHealthIndicatorTest {

    @Test
    public void overBudget_isDown() {
        AiCostMeter meter = new AiCostMeter();
        meter.setBudgetTokens(10);
        meter.recordSuccess(20, 0);
        AiServiceHealthIndicator ind = new AiServiceHealthIndicator(new AiMetrics(), meter, true, 0.5, 50);
        assertThat(ind.health().getStatus()).isEqualTo(Status.DOWN);
        assertThat(ind.health().getDetails().get("reason")).isEqualTo("token_budget_exceeded");
    }

    @Test
    public void healthyByDefault() {
        AiServiceHealthIndicator ind = new AiServiceHealthIndicator(new AiMetrics(), new AiCostMeter());
        assertThat(ind.health().getStatus()).isEqualTo(Status.UP);
    }
}
