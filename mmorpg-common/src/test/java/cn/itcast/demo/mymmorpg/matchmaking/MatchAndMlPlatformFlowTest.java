package cn.itcast.demo.mymmorpg.matchmaking;

import cn.itcast.demo.mymmorpg.experiment.ExperimentAssigner;
import cn.itcast.demo.mymmorpg.ml.ModelVersionRegistry;
import cn.itcast.demo.mymmorpg.telemetry.TrainingDataCollector;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 匹配增强 + 实验分桶 + 训练数据 / 模型注册闭环。
 */
public class MatchAndMlPlatformFlowTest {

    @Test
    public void multiObjectiveMatchPreferComplementRoles() {
        long now = System.currentTimeMillis();
        MatchCompatibilityScorer.Profile tank = new MatchCompatibilityScorer.Profile(
                30, 5000, now - 5_000, "tank", 0.48, "aggressive");
        MatchCompatibilityScorer.Profile healer = new MatchCompatibilityScorer.Profile(
                30, 4900, now - 4_000, "healer", 0.50, "aggressive");
        MatchCompatibilityScorer.Profile dps = new MatchCompatibilityScorer.Profile(
                30, 5100, now - 3_000, "dps", 0.49, "aggressive");
        MatchCompatibilityScorer.Profile tank2 = new MatchCompatibilityScorer.Profile(
                30, 5050, now - 2_000, "tank", 0.47, "passive");

        double tankHeal = MatchCompatibilityScorer.score(tank, healer);
        double tankTank = MatchCompatibilityScorer.score(tank, tank2);
        double tankDps = MatchCompatibilityScorer.score(tank, dps);
        assertThat(tankHeal).isGreaterThan(tankTank);
        assertThat(tankHeal).isGreaterThan(tankDps);
    }

    @Test
    public void experimentBuckets_coverBothVariants() {
        Set<String> variants = new HashSet<>();
        for (long i = 1; i <= 200; i++) {
            variants.add(ExperimentAssigner.assign(i, "dda_algo", "baseline", "aggressive"));
        }
        assertThat(variants).containsExactlyInAnyOrder("baseline", "aggressive");
        assertThat(ExperimentAssigner.assign(7L, "dda_algo", "baseline", "aggressive"))
                .isEqualTo(ExperimentAssigner.assign(7L, "dda_algo", "baseline", "aggressive"));
    }

    @Test
    public void trainingBuffer_dropWhenFull_andModelRegistry() {
        TrainingDataCollector c = new TrainingDataCollector(3);
        c.collect("a", 1L, Map.of("x", 1));
        c.collect("b", 2L, Map.of("x", 2));
        c.collect("c", 3L, Map.of("x", 3));
        c.collect("d", 4L, Map.of("x", 4)); // dropped
        Map<String, Object> stats = c.stats();
        assertThat(((Number) stats.get("accepted")).longValue()).isEqualTo(3L);
        assertThat(((Number) stats.get("dropped")).longValue()).isEqualTo(1L);
        assertThat(c.flush(10)).hasSize(3);
        assertThat(c.flush(10)).isEmpty();

        ModelVersionRegistry reg = new ModelVersionRegistry();
        reg.register("anomaly", "v1", "local://1");
        reg.register("anomaly", "v2", "local://2");
        assertThat(reg.snapshot()).containsKey("anomaly");
        assertThat(reg.rollback("anomaly", "v1").orElseThrow().version()).isEqualTo("v1");
        assertThat(reg.rollback("anomaly", "missing")).isEmpty();
    }
}
