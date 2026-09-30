package cn.itcast.demo.mymmorpg.ai;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AiCostMeterTest {

    @Test
    public void overBudget_triggersAlert() {
        AiCostMeter meter = new AiCostMeter();
        meter.setBudgetTokens(100);
        meter.recordSuccess(80, 30);
        assertThat(meter.isOverBudget()).isTrue();
        assertThat(meter.snapshot().get("budgetAlerts")).isEqualTo(1L);
    }

    @Test
    public void estimateTokens_handlesChinese() {
        assertThat(AiCostMeter.estimateTokens("你好世界")).isGreaterThan(0L);
    }
}
