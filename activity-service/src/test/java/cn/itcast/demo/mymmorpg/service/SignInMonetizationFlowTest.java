package cn.itcast.demo.mymmorpg.service;

import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 签到商业化流程：七日首签 → 当日防重 → 激活月卡 → 日领宝石防重。
 */
public class SignInMonetizationFlowTest {

    @Test
    public void sevenDaySignAndMonthlyCardDailyClaim() {
        SignInService signIn = new SignInService();
        long playerId = 2026L;

        Map<String, Object> day1 = signIn.signToday(playerId);
        assertThat(day1.get("ok")).isEqualTo(true);
        assertThat(day1.get("cycleDay")).isEqualTo(1);
        assertThat(day1.get("rewardItemId")).isEqualTo(4001);

        assertThat(signIn.signToday(playerId).get("error")).isEqualTo("already_signed");
        assertThat(signIn.status(playerId).get("signedToday")).isEqualTo(true);

        assertThat(signIn.claimMonthlyCardDaily(playerId).get("error")).isEqualTo("no_monthly_card");
        signIn.activateMonthlyCard(playerId);
        assertThat(signIn.status(playerId).get("monthlyCard")).isEqualTo(true);

        Map<String, Object> gem = signIn.claimMonthlyCardDaily(playerId);
        assertThat(gem.get("dailyGem")).isEqualTo(90);
        assertThat(signIn.claimMonthlyCardDaily(playerId).get("ok")).isEqualTo(false);
    }
}
