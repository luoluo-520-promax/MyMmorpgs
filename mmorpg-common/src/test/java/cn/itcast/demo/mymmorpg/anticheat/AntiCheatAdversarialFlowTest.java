package cn.itcast.demo.mymmorpg.anticheat;

import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 反作弊对抗性流程：模拟外挂点击洪泛 / 固定技能节奏 → 异常分抬升 → strike/ban。
 */
public class AntiCheatAdversarialFlowTest {

    @Test
    public void clickFloodBot_getsStrikes() {
        AntiCheatService ac = new AntiCheatService();
        ac.configure(120f, 400f, 3, 60_000L, 3.0);
        long now = System.currentTimeMillis();
        List<AntiCheatService.Verdict> verdicts = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            AntiCheatService.CheckResult r = ac.checkBehaviorAnomaly(42L, "click", 1.0, now + i * 40L);
            ac.checkBehaviorAnomaly(42L, "skill", 1.0, now + i * 40L);
            verdicts.add(r.verdict());
        }
        assertThat(verdicts).anyMatch(v -> v == AntiCheatService.Verdict.STRIKE
                || v == AntiCheatService.Verdict.BAN);
        assertThat(ac.strikeCount(42L)).isGreaterThan(0);
    }

    @Test
    public void humanSparseActions_stayOk() {
        AntiCheatService ac = new AntiCheatService();
        long now = System.currentTimeMillis();
        for (int i = 0; i < 6; i++) {
            long t = now + i * 900L + (i % 2) * 120L;
            AntiCheatService.CheckResult r = ac.checkBehaviorAnomaly(77L, "click", 1.0, t);
            assertThat(r.verdict()).isEqualTo(AntiCheatService.Verdict.OK);
            ac.checkBehaviorAnomaly(77L, "skill", 1.0, t + 200 + i * 30);
        }
        assertThat(ac.strikeCount(77L)).isZero();
    }

    @Test
    public void moveJitterThenAnomaly_clearsOnClear() {
        AntiCheatService ac = new AntiCheatService();
        long now = System.currentTimeMillis();
        AntiCheatService.CheckResult move = ac.checkMove(9L, 0, 0, 0, 50, 0, 0, 80f, now, now);
        assertThat(move.verdict()).isEqualTo(AntiCheatService.Verdict.OK);
        for (int i = 0; i < 25; i++) {
            ac.checkBehaviorAnomaly(9L, "skill", 1.0, now + i * 100L);
            ac.checkBehaviorAnomaly(9L, "click", 1.0, now + i * 80L);
        }
        BehaviorAnomalyDetector.AnomalyResult r = ac.anomalyDetector().evaluate(9L, now + 5_000L);
        assertThat(r.score()).isGreaterThan(0.0);
        ac.clear(9L);
        assertThat(ac.strikeCount(9L)).isZero();
        assertThat(ac.anomalyDetector().evaluate(9L, now + 6_000L).score()).isEqualTo(0.0);
    }
}
