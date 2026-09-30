package cn.itcast.demo.mymmorpg.service;

import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 世界 BOSS 伤害截断反挂 + 结算 grantPlans。
 */
public class WorldBossAntiCheatFlowTest {

    @Test
    public void overMaxHit_isFlaggedAndNotCounted() {
        // 默认 maxHitDamage=500000；用小值构造：通过反射不可行时用默认并上报超大伤害
        WorldEventService svc = new WorldEventService();
        long now = System.currentTimeMillis();
        svc.schedule("anti", WorldEventService.EventType.WORLD_BOSS, 1, 0, 0, now, now + 3600000, 1_000_000L);
        svc.activate("anti");

        // 合法伤害
        svc.reportDamage("anti", 1L, 100);
        // 超理论最大值：默认 500000，上报 500001 应截断不累计
        WorldEventService.WorldEvent afterCheat = svc.reportDamage("anti", 2L, 500_001L);
        assertThat(afterCheat.bossHp()).isEqualTo(999_900L); // 仅扣了玩家1的100

        List<WorldEventService.Contribution> board = svc.contributions("anti");
        assertThat(board.stream().filter(c -> c.playerId() == 1L).findFirst().orElseThrow().damage())
                .isEqualTo(100L);
        // 作弊玩家不应出现在有效伤害榜或 flagged
        boolean p2Present = board.stream().anyMatch(c -> c.playerId() == 2L && c.damage() > 0);
        assertThat(p2Present).isFalse();
    }

    @Test
    public void settle_buildsGrantPlansWithIdempotencyKey() {
        WorldEventService svc = new WorldEventService();
        long now = System.currentTimeMillis();
        svc.schedule("settle-ac", WorldEventService.EventType.WORLD_BOSS, 1, 0, 0, now, now + 1000, 300);
        svc.activate("settle-ac");
        svc.reportDamage("settle-ac", 10L, 200);
        svc.reportDamage("settle-ac", 11L, 100);

        Map<String, Object> settle = svc.settle("settle-ac", 10);
        assertThat(settle.get("ok")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> plans = (List<Map<String, Object>>) settle.get("grantPlans");
        assertThat(plans).hasSize(2);
        assertThat(plans.get(0).get("idempotencyKey")).isEqualTo("world-boss:settle-ac:10");
        assertThat(plans.get(0).get("rewardTier")).isEqualTo("S");
    }
}
