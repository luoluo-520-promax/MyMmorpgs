package cn.itcast.demo.mymmorpg.anticheat;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class AntiCheatRuleEngineTest {

    private AntiCheatRuleEngine engine;

    @BeforeMethod
    public void setUp() {
        engine = new AntiCheatRuleEngine();
        engine.loadRules(AntiCheatRuleEngine.defaultRules());
    }

    @Test
    public void evaluateMoveSpeedExceeded() {
        AntiCheatRuleEngine.CheckResult r = engine.evaluate(1L, Map.of("moveSpeed", 200));
        assertThat(r.verdict()).isEqualTo(AntiCheatService.Verdict.STRIKE);
        assertThat(r.ruleId()).isEqualTo("move_speed");
    }

    @Test
    public void evaluateTeleportAndDamage() {
        assertThat(engine.evaluate(1L, Map.of("distance", 500)).verdict())
                .isEqualTo(AntiCheatService.Verdict.STRIKE);
        assertThat(engine.evaluate(1L, Map.of(
                "actualDamage", 500,
                "expectedMaxDamage", 100)).verdict())
                .isEqualTo(AntiCheatService.Verdict.STRIKE);
    }

    @Test
    public void updateThresholdDynamically() {
        assertThat(engine.updateThreshold("move_speed", 250)).isTrue();
        assertThat(engine.evaluate(1L, Map.of("moveSpeed", 200)).verdict())
                .isEqualTo(AntiCheatService.Verdict.OK);
        assertThat(engine.updateThreshold("missing", 1)).isFalse();
    }

    @Test
    public void serviceDelegatesToEngine() {
        AntiCheatService ac = new AntiCheatService(engine);
        AntiCheatService.CheckResult bad = ac.evaluateRules(9L, Map.of("strikeRate", 99));
        assertThat(bad.verdict()).isIn(AntiCheatService.Verdict.STRIKE, AntiCheatService.Verdict.BAN);

        AntiCheatService.CheckResult move = ac.checkMove(8L, 0, 0, 0, 10, 0, 0, 50f,
                System.currentTimeMillis(), System.currentTimeMillis());
        assertThat(move.verdict()).isEqualTo(AntiCheatService.Verdict.OK);
    }

    @Test
    public void loadRulesReplacesSet() {
        engine.loadRules(List.of(new AntiCheatRuleEngine.Rule(
                "only_dmg", AntiCheatRuleEngine.RuleType.DAMAGE, 2.0, true,
                AntiCheatRuleEngine.Severity.REJECT)));
        assertThat(engine.listRules()).hasSize(1);
        assertThat(engine.evaluate(1L, Map.of("damageRatio", 3.0)).verdict())
                .isEqualTo(AntiCheatService.Verdict.REJECT);
    }
}
