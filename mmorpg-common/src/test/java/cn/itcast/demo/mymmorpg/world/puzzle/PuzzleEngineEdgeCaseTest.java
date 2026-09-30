package cn.itcast.demo.mymmorpg.world.puzzle;

import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 解谜子系统边界：未知模板、缺参、物理过期、火焰蔓延。
 */
public class PuzzleEngineEdgeCaseTest {

    @Test
    public void instantiateRejectsUnknownTypeAndMissingId() {
        PuzzleTemplateService svc = new PuzzleTemplateService();
        assertThat(svc.instantiateFromJson(Map.of(
                "puzzleId", "x", "type", "NOT_A_TYPE")).get("error")).isEqualTo("unknown_type");
        assertThat(svc.instantiateFromJson(Map.of("type", "SHOOTING_TARGET")).get("error"))
                .isEqualTo("puzzleId_and_type_required");
        // 缺 config 时模板默认值补齐 requiredParams，仍可成功实例化
        assertThat(svc.instantiateFromJson(Map.of(
                "puzzleId", "y", "type", "SHOOTING_TARGET")).get("ok")).isEqualTo(true);
    }

    @Test
    public void ruleOneShotDoesNotRefire() {
        RuleTriggerService rules = new RuleTriggerService();
        rules.register(new RuleTriggerService.RuleDef(
                "once", "g1", "INTERACT", RuleTriggerService.LogicOp.AND,
                List.of(new RuleTriggerService.Condition("a", "EQ", "1")),
                List.of(new RuleTriggerService.Action("SET_STATE", "g1", Map.of("state", "ON"))),
                true));
        assertThat(rules.fire("INTERACT", Map.of("a", "1")).get("matchedCount")).isEqualTo(1);
        assertThat(rules.fire("INTERACT", Map.of("a", "1")).get("matchedCount")).isEqualTo(0);
    }

    @Test
    public void physicsExpiresAndSpreadRequiresFlammableHint() {
        PhysicsLayerService physics = new PhysicsLayerService();
        physics.configure(16);
        long now = 1_000L;
        // 最短 TTL 为 1000ms
        physics.apply(1, 16f, 16f, PhysicsLayerService.PhysicsKind.FIRE, 2, 100L, now);
        assertThat(physics.queryAoi(1, 16f, 16f, 1, now + 100).get("count")).isEqualTo(1);
        assertThat(physics.queryAoi(1, 16f, 16f, 1, now + 1_100).get("count")).isEqualTo(0);

        physics.apply(1, 32f, 32f, PhysicsLayerService.PhysicsKind.FIRE, 3, 10_000L, now);
        // grid 2,2 — 无 hint 不蔓延
        assertThat(physics.tickSpread(1, now + 1, List.of()).get("spreadCount")).isEqualTo(0);
        // 邻格 hint
        Map<String, Object> spread = physics.tickSpread(1, now + 2, List.of("1:3:2", "1:1:2", "1:2:3", "1:2:1"));
        assertThat((Integer) spread.get("spreadCount")).isGreaterThanOrEqualTo(1);
    }

    @Test
    public void orLogicMatchesAnyCondition() {
        RuleTriggerService rules = new RuleTriggerService();
        rules.register(new RuleTriggerService.RuleDef(
                "or-rule", "g2", "CAST", RuleTriggerService.LogicOp.OR,
                List.of(
                        new RuleTriggerService.Condition("element", "EQ", "FIRE"),
                        new RuleTriggerService.Condition("element", "EQ", "CRYO")),
                List.of(new RuleTriggerService.Action("GRANT_ITEM", "box", Map.of("itemId", "x"))),
                false));
        assertThat(rules.fire("CAST", Map.of("element", "CRYO")).get("matchedCount")).isEqualTo(1);
        assertThat(rules.fire("CAST", Map.of("element", "ANEMO")).get("matchedCount")).isEqualTo(0);
    }
}
