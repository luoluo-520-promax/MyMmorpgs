package cn.itcast.demo.mymmorpg.service;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SignInServiceTest {

    @Test
    public void sevenDaySignAndMonthlyClaim() {
        SignInService svc = new SignInService();
        assertThat(svc.signToday(5L).get("cycleDay")).isEqualTo(1);
        assertThat(svc.signToday(5L).get("ok")).isEqualTo(false);
        svc.activateMonthlyCard(5L);
        assertThat(svc.claimMonthlyCardDaily(5L).get("dailyGem")).isEqualTo(90);
        assertThat(svc.claimMonthlyCardDaily(5L).get("ok")).isEqualTo(false);
    }
}
