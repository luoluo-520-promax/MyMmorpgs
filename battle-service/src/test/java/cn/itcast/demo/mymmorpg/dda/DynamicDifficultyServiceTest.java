package cn.itcast.demo.mymmorpg.dda;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DynamicDifficultyServiceTest {

    @Test
    public void recordAndEvaluate_returnsAdjustment() {
        DynamicDifficultyService svc = new DynamicDifficultyService();
        svc.recordBattleResult(9L, 1800, true, 90_000);
        svc.recordBattleResult(9L, 1900, true, 80_000);
        var adj = svc.evaluate(9L);
        assertThat(adj.overallDifficulty()).isGreaterThan(0.5);
        assertThat(svc.snapshot(9L).get("samples")).isEqualTo(2);
    }
}
