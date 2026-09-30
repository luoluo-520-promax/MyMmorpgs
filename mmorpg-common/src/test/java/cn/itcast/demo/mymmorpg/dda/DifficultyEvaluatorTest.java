package cn.itcast.demo.mymmorpg.dda;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DifficultyEvaluatorTest {

    private final DifficultyEvaluator evaluator = new DifficultyEvaluator();

    @Test
    public void overpoweredPlayer_raisesDifficulty() {
        DifficultyEvaluator.Adjustment adj = evaluator.evaluate(
                new DifficultyEvaluator.BattleStats(3000, 0.99, 40_000, 180_000, 1000));
        assertThat(adj.overallDifficulty()).isGreaterThan(1.0);
        assertThat(adj.skillFrequencyMul()).isGreaterThan(1.0);
        assertThat(adj.reason()).isEqualTo("player_overpowered");
    }

    @Test
    public void strugglingPlayer_lowersDifficulty_andBoostsReward() {
        DifficultyEvaluator.Adjustment adj = evaluator.evaluate(
                new DifficultyEvaluator.BattleStats(400, 0.2, 400_000, 180_000, 1000));
        assertThat(adj.overallDifficulty()).isLessThan(1.0);
        assertThat(adj.rewardMul()).isGreaterThan(1.0);
        assertThat(adj.reason()).isEqualTo("player_struggling");
    }

    @Test
    public void nullStats_returnsNeutral() {
        DifficultyEvaluator.Adjustment adj = evaluator.evaluate(null);
        assertThat(adj.overallDifficulty()).isEqualTo(1.0);
        assertThat(adj.reason()).isEqualTo("no_stats");
    }
}
