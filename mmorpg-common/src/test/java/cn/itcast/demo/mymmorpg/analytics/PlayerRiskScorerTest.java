package cn.itcast.demo.mymmorpg.analytics;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PlayerRiskScorerTest {

    @Test
    public void inactivePlayer_highChurn() {
        PlayerRiskScorer.Prediction p = new PlayerRiskScorer().predict(
                new PlayerRiskScorer.Features(1, 0.1, 20, 0, 0, 10));
        assertThat(p.churnRisk()).isGreaterThan(0.5);
        assertThat(p.suggestedAction()).isIn("push_retention_quest", "push_login_bonus");
    }

    @Test
    public void whaleActive_highPayIntent() {
        PlayerRiskScorer.Prediction p = new PlayerRiskScorer().predict(
                new PlayerRiskScorer.Features(7, 0.9, 600, 5000, 8, 0));
        assertThat(p.payIntent()).isGreaterThan(0.5);
        assertThat(p.payBucket()).isIn("MID", "HIGH");
    }
}
