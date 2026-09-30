package cn.itcast.demo.mymmorpg.service;

import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 世界事件完整流程：排期 → 激活 → 多玩家伤害 → 击杀结算 → 关闭。
 */
public class WorldEventServiceFlowTest {

    @Test
    public void fullWorldBossLifecycle() {
        WorldEventService svc = new WorldEventService();
        long now = System.currentTimeMillis();
        svc.schedule("flow-boss", WorldEventService.EventType.WORLD_BOSS, 3, 100f, 200f,
                now, now + 3_600_000L, 500);
        assertThat(svc.listActive()).hasSize(1);

        svc.activate("flow-boss");
        assertThat(svc.get("flow-boss").state()).isEqualTo(WorldEventService.EventState.ACTIVE);

        svc.reportDamage("flow-boss", 101L, 200);
        WorldEventService.WorldEvent settling = svc.reportDamage("flow-boss", 102L, 400);
        assertThat(settling.state()).isEqualTo(WorldEventService.EventState.SETTLING);
        assertThat(settling.bossHp()).isEqualTo(0L);

        Map<String, Object> settle = svc.settle("flow-boss", 3);
        assertThat(settle.get("ok")).isEqualTo(true);
        assertThat(settle.get("participantCount")).isEqualTo(2);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> top = (List<Map<String, Object>>) settle.get("top");
        assertThat(top.get(0).get("playerId")).isEqualTo(102L);
        assertThat(top.get(0).get("rewardTier")).isEqualTo("S");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> plans = (List<Map<String, Object>>) settle.get("grantPlans");
        assertThat(plans).hasSize(2);
        assertThat(plans.get(0).get("playerId")).isEqualTo(102L);
        assertThat(plans.get(0).get("itemId")).isEqualTo(50001);
        assertThat(plans.get(0).get("count")).isEqualTo(5);
        assertThat(plans.get(0).get("idempotencyKey")).isEqualTo("world-boss:flow-boss:102");
        // 2 人局：百分位前 50% 仅覆盖第 1 名，第 2 名为 C
        assertThat(plans.get(1).get("rewardTier")).isEqualTo("C");
        assertThat(plans.get(1).get("itemId")).isEqualTo(50004);
        assertThat(settle.get("mvp")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> mvp = (Map<String, Object>) settle.get("mvp");
        assertThat(mvp.get("playerId")).isEqualTo(102L);
        assertThat(svc.get("flow-boss").state()).isEqualTo(WorldEventService.EventState.CLOSED);
    }

    @Test
    public void damageOnInactive_throws() {
        WorldEventService svc = new WorldEventService();
        svc.schedule("idle", WorldEventService.EventType.DYNAMIC_EVENT, 1, 0, 0, 0, 0, 100);
        assertThatThrownBy(() -> svc.reportDamage("idle", 1L, 10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("event_not_active");
    }

    @Test
    public void regionPuzzleType_supported() {
        WorldEventService svc = new WorldEventService();
        WorldEventService.WorldEvent ev = svc.schedule("puzzle-1",
                WorldEventService.EventType.REGION_PUZZLE, 2, 1, 1, 0, 0, 1);
        assertThat(ev.type()).isEqualTo(WorldEventService.EventType.REGION_PUZZLE);
        assertThat(svc.toView(ev).get("ok")).isEqualTo(true);
        assertThat(svc.toView(ev).get("type")).isEqualTo("REGION_PUZZLE");
    }
}
