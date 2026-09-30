package cn.itcast.demo.mymmorpg.experiment;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ExperimentAssignerTest {

    @Test
    public void samePlayer_stableAssignment() {
        String a = ExperimentAssigner.assign(42L, "dda_algo", "baseline", "aggressive");
        String b = ExperimentAssigner.assign(42L, "dda_algo", "baseline", "aggressive");
        assertThat(a).isEqualTo(b);
        assertThat(a).isIn("baseline", "aggressive");
    }

    @Test
    public void treatmentPercent_bounds() {
        assertThat(ExperimentAssigner.inTreatment(1L, "x", 0)).isFalse();
        assertThat(ExperimentAssigner.inTreatment(1L, "x", 100)).isTrue();
    }
}
