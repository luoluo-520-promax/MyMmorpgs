package cn.itcast.demo.mymmorpg.challenge;

import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 挑战关卡结算：胜负与按耗时星级。
 */
public class ChallengeRuntimeTest {

    @Test
    public void finish_defeat_zeroStarsAndScore() {
        ChallengeRuntime rt = new ChallengeRuntime(1L, 9L, 1, 101, 3);
        rt.finish(false);
        assertThat(rt.getStatus()).isEqualTo(ChallengeRuntime.STATUS_DEFEAT);
        assertThat(rt.getStars()).isZero();
        assertThat(rt.getScore()).isZero();
    }

    @Test
    public void finish_victory_quickClear_threeStars() {
        ChallengeRuntime rt = new ChallengeRuntime(2L, 9L, 1, 101, 3);
        rt.finish(true);
        assertThat(rt.getStatus()).isEqualTo(ChallengeRuntime.STATUS_VICTORY);
        assertThat(rt.getStars()).isEqualTo(3);
        assertThat(rt.getScore()).isGreaterThanOrEqualTo(100);
    }

    @Test
    public void finish_victory_elapsedBetweenThresholds_twoStars() throws Exception {
        ChallengeRuntime rt = new ChallengeRuntime(3L, 9L, 1, 101, 3, List.of(60, 120, 180));
        setStartMillis(rt, System.currentTimeMillis() - 90_000L);
        rt.finish(true);
        assertThat(rt.getStars()).isEqualTo(2);
    }

    @Test
    public void finish_victory_slow_oneStar() throws Exception {
        ChallengeRuntime rt = new ChallengeRuntime(4L, 9L, 1, 101, 3, List.of(60, 120, 180));
        setStartMillis(rt, System.currentTimeMillis() - 200_000L);
        rt.finish(true);
        assertThat(rt.getStars()).isEqualTo(1);
    }

    private static void setStartMillis(ChallengeRuntime rt, long millis) throws Exception {
        Field f = ChallengeRuntime.class.getDeclaredField("startTimeMillis");
        f.setAccessible(true);
        f.set(rt, millis);
    }
}
