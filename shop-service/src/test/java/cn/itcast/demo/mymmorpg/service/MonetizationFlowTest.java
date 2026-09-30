package cn.itcast.demo.mymmorpg.service;

import org.springframework.beans.factory.ObjectProvider;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 商业化战令流程：首充双倍 → 解锁付费档 → 日任务升级 → 月卡日领。
 */
public class MonetizationFlowTest {

    @Test
    @SuppressWarnings("unchecked")
    public void firstChargePassAndMonthlyJourney() {
        ObjectProvider tlog = mock(ObjectProvider.class);
        when(tlog.getIfAvailable()).thenReturn(null);
        PassService pass = new PassService(tlog);

        long playerId = 2026L;
        int season = 1;

        Map<String, Object> first = pass.applyFirstChargeDouble(playerId, season, 60);
        assertThat(first.get("amount")).isEqualTo(120);
        assertThat(pass.applyFirstChargeDouble(playerId, season, 60).get("ok")).isEqualTo(false);

        pass.unlockPaid(playerId, season);
        pass.activateMonthlyCard(playerId, season, 30);
        assertThat(pass.status(playerId, season).get("paidUnlocked")).isEqualTo(true);
        assertThat(pass.status(playerId, season).get("monthlyCardActive")).isEqualTo(true);

        pass.addDailyXp(playerId, season, 500);
        pass.addDailyXp(playerId, season, 800);
        Map<String, Object> leveled = pass.status(playerId, season);
        int level = (Integer) leveled.get("level");
        assertThat(level).isGreaterThanOrEqualTo(2);

        assertThat(pass.claim(playerId, season, Math.min(level, 2), PassService.Tier.FREE).get("ok")).isEqualTo(true);
        assertThat(pass.claim(playerId, season, Math.min(level, 2), PassService.Tier.PAID).get("ok")).isEqualTo(true);
        assertThat(pass.claim(playerId, season, Math.min(level, 2), PassService.Tier.FREE).get("error"))
                .isEqualTo("already_claimed");

        Map<String, Object> daily = pass.claimMonthlyDaily(playerId, season);
        assertThat(daily.get("dailyGem")).isEqualTo(90);
        assertThat(pass.claimMonthlyDaily(playerId, season).get("error")).isEqualTo("already_claimed_today");
    }
}
