package cn.itcast.demo.mymmorpg.anticheat;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BehaviorAnomalyDetectorTest {

    @Test
    public void robotSkillRhythm_isSuspicious() {
        BehaviorAnomalyDetector det = new BehaviorAnomalyDetector();
        long now = System.currentTimeMillis();
        for (int i = 0; i < 20; i++) {
            det.record(1L, "skill", 1.0, now + i * 200L); // 固定间隔
            det.record(1L, "click", 1.0, now + i * 100L);
        }
        BehaviorAnomalyDetector.AnomalyResult r = det.evaluate(1L, now + 5_000L);
        assertThat(r.score()).isGreaterThan(0.5);
        assertThat(r.suspicious()).isTrue();
        assertThat(r.reason()).isIn("robot_skill_rhythm", "click_flood", "trajectory_jitter", "damage_burst");
    }

    @Test
    public void sparseHumanClicks_notSuspicious() {
        BehaviorAnomalyDetector det = new BehaviorAnomalyDetector();
        long now = System.currentTimeMillis();
        det.record(2L, "click", 1.0, now);
        det.record(2L, "skill", 1.0, now + 800);
        det.record(2L, "skill", 1.0, now + 2100);
        BehaviorAnomalyDetector.AnomalyResult r = det.evaluate(2L, now + 3_000L);
        assertThat(r.suspicious()).isFalse();
    }

    @Test
    public void antiCheatIntegratesAnomaly() {
        AntiCheatService ac = new AntiCheatService();
        long now = System.currentTimeMillis();
        AntiCheatService.CheckResult last = AntiCheatService.CheckResult.ok();
        for (int i = 0; i < 30; i++) {
            last = ac.checkBehaviorAnomaly(7L, "click", 1.0, now + i * 50L);
            ac.checkBehaviorAnomaly(7L, "skill", 1.0, now + i * 50L);
        }
        assertThat(last.verdict()).isIn(AntiCheatService.Verdict.STRIKE, AntiCheatService.Verdict.BAN,
                AntiCheatService.Verdict.OK);
    }
}
