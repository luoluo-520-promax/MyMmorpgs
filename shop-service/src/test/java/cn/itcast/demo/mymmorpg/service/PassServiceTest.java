package cn.itcast.demo.mymmorpg.service;

import org.springframework.beans.factory.ObjectProvider;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PassServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    public void battlePassAndFirstChargeDouble() {
        ObjectProvider provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        PassService svc = new PassService(provider);
        svc.addDailyXp(1L, 1, 2500);
        Map<String, Object> st = svc.status(1L, 1);
        assertThat(st.get("level")).isEqualTo(3);
        assertThat(svc.unlockPaid(1L, 1).get("paidUnlocked")).isEqualTo(true);
        assertThat(svc.claim(1L, 1, 2, PassService.Tier.PAID).get("ok")).isEqualTo(true);
        assertThat(svc.applyFirstChargeDouble(1L, 1, 60).get("amount")).isEqualTo(120);
        assertThat(svc.applyFirstChargeDouble(1L, 1, 60).get("ok")).isEqualTo(false);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void autoRenewAndRenewMonthlyCardIfDue() {
        ObjectProvider provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        PassService svc = new PassService(provider);

        assertThat(svc.renewMonthlyCardIfDue(10L, 1).get("error")).isEqualTo("auto_renew_disabled");
        svc.setAutoRenew(10L, 1, true);
        assertThat(svc.status(10L, 1).get("autoRenewEnabled")).isEqualTo(true);
        assertThat(svc.renewMonthlyCardIfDue(10L, 1).get("error")).isEqualTo("no_monthly_card");

        svc.activateMonthlyCard(10L, 1, 30);
        assertThat(svc.renewMonthlyCardIfDue(10L, 1).get("error")).isEqualTo("not_due");

        svc.setMonthlyCardExpireMs(10L, 1, System.currentTimeMillis() - 1_000L);
        Map<String, Object> renewed = svc.renewMonthlyCardIfDue(10L, 1);
        assertThat(renewed.get("ok")).isEqualTo(true);
        assertThat(renewed.get("renewed")).isEqualTo(true);
        assertThat((Long) renewed.get("monthlyCardExpireMs")).isGreaterThan(System.currentTimeMillis());
        assertThat(renewed.get("monthlyCardActive")).isEqualTo(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void claimCompensation_forExpiredUnclaimedRewards() {
        ObjectProvider provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        PassService svc = new PassService(provider);

        svc.addDailyXp(20L, 1, 2500);
        svc.unlockPaid(20L, 1);
        svc.activateMonthlyCard(20L, 1, 30);
        svc.claim(20L, 1, 1, PassService.Tier.FREE);

        assertThat(svc.claimCompensation(20L, 1).get("error")).isEqualTo("not_eligible");

        svc.setMonthlyCardExpireMs(20L, 1, System.currentTimeMillis() - 5_000L);
        Map<String, Object> comp = svc.claimCompensation(20L, 1);
        assertThat(comp.get("ok")).isEqualTo(true);
        assertThat(comp.get("compensation")).isEqualTo(true);
        assertThat((Integer) comp.get("rewardCount")).isGreaterThan(0);
        @SuppressWarnings("unchecked")
        List<Integer> claimedFree = (List<Integer>) comp.get("claimedFree");
        assertThat(claimedFree).contains(1, 2, 3);
        @SuppressWarnings("unchecked")
        List<Integer> claimedPaid = (List<Integer>) comp.get("claimedPaid");
        assertThat(claimedPaid).contains(1, 2, 3);

        assertThat(svc.claimCompensation(20L, 1).get("error")).isEqualTo("already_claimed");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void syncFromPlatform_stubActivatesMonthlyCard() {
        ObjectProvider provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        PassService svc = new PassService(provider);

        assertThat(svc.syncFromPlatform(30L, 1, "", "x").get("error")).isEqualTo("platform_required");
        assertThat(svc.syncFromPlatform(30L, 1, "APPLE", "").get("error")).isEqualTo("receipt_required");
        assertThat(svc.syncFromPlatform(30L, 1, "FOO", "r").get("error")).isEqualTo("unsupported_platform");

        Map<String, Object> synced = svc.syncFromPlatform(30L, 1, "APPLE", "iap.receipt.autorenew");
        assertThat(synced.get("ok")).isEqualTo(true);
        assertThat(synced.get("synced")).isEqualTo(true);
        assertThat(synced.get("stub")).isEqualTo(true);
        assertThat(synced.get("monthlyCardActive")).isEqualTo(true);
        assertThat(synced.get("autoRenewEnabled")).isEqualTo(true);
    }
}
