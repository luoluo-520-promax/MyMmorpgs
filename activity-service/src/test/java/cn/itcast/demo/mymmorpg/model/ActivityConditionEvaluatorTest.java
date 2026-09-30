package cn.itcast.demo.mymmorpg.model;

import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ActivityConditionEvaluatorTest {

    @Test
    public void battleWinMin() {
        PlayerActivityProgress p = new PlayerActivityProgress();
        p.battleWins = 2;
        ActivityConditionPayload c = new ActivityConditionPayload();
        c.type = "BATTLE_WIN_MIN";
        c.intValue = 3;
        assertThat(ActivityConditionEvaluator.allMet(List.of(c), p)).isFalse();
        p.battleWins = 3;
        assertThat(ActivityConditionEvaluator.allMet(List.of(c), p)).isTrue();
    }
}
