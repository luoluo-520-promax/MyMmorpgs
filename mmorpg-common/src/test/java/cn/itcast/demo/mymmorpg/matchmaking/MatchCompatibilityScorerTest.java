package cn.itcast.demo.mymmorpg.matchmaking;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MatchCompatibilityScorerTest {

    @Test
    public void closerLevelAndPower_scoresHigher() {
        long now = System.currentTimeMillis();
        double close = MatchCompatibilityScorer.score(10, 1000, now, 10, 1050, now);
        double far = MatchCompatibilityScorer.score(10, 1000, now, 20, 5000, now);
        assertThat(close).isGreaterThan(far);
    }

    @Test
    public void invalidLevel_returnsNegativeInfinity() {
        assertThat(MatchCompatibilityScorer.score(0, 1, 1L, 10, 1, 1L))
                .isEqualTo(Double.NEGATIVE_INFINITY);
    }

    @Test
    public void tankHealerComplement_scoresHigherThanSameRole() {
        long now = System.currentTimeMillis();
        MatchCompatibilityScorer.Profile tank = new MatchCompatibilityScorer.Profile(
                20, 2000, now, "tank", 0.5, "aggressive");
        MatchCompatibilityScorer.Profile healer = new MatchCompatibilityScorer.Profile(
                20, 2100, now, "healer", 0.52, "aggressive");
        MatchCompatibilityScorer.Profile tank2 = new MatchCompatibilityScorer.Profile(
                20, 2100, now, "tank", 0.52, "aggressive");
        assertThat(MatchCompatibilityScorer.score(tank, healer))
                .isGreaterThan(MatchCompatibilityScorer.score(tank, tank2));
    }
}
