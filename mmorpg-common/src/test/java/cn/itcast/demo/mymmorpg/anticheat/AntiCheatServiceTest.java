package cn.itcast.demo.mymmorpg.anticheat;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AntiCheatServiceTest {

    @Test
    public void rejectsTeleportAndBansAfterStrikes() {
        AntiCheatService ac = new AntiCheatService();
        ac.configure(120f, 50f, 2, 60_000L, 3.0);
        long now = System.currentTimeMillis();
        AntiCheatService.CheckResult r1 = ac.checkMove(9L, 0, 0, 0, 500, 0, 0, 100f, now, now);
        assertThat(r1.verdict()).isEqualTo(AntiCheatService.Verdict.STRIKE);

        AntiCheatService.CheckResult r2 = ac.checkMove(9L, 0, 0, 0, 500, 0, 0, 100f, now, now);
        assertThat(r2.verdict()).isEqualTo(AntiCheatService.Verdict.BAN);
        assertThat(ac.isBanned(9L)).isTrue();
    }

    @Test
    public void damageOverflowStrikes() {
        AntiCheatService ac = new AntiCheatService();
        AntiCheatService.CheckResult ok = ac.checkDamage(1L, 100, 200);
        assertThat(ok.verdict()).isEqualTo(AntiCheatService.Verdict.OK);
        AntiCheatService.CheckResult bad = ac.checkDamage(1L, 100, 500);
        assertThat(bad.verdict()).isIn(AntiCheatService.Verdict.STRIKE, AntiCheatService.Verdict.BAN);
    }
}
