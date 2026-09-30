package cn.itcast.demo.mymmorpg.service;

import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** 世界 BOSS 贡献百分位分档 + 辅助折算 + MVP。 */
public class WorldBossRewardTierTest {

    @Test
    public void settleAssignsPercentileTiersAndMvp() {
        WorldEventService svc = new WorldEventService();
        AtomicReference<Map<String, Object>> mvpEvent = new AtomicReference<>();
        svc.setMvpBroadcastListener(mvpEvent::set);
        long now = System.currentTimeMillis();
        svc.schedule("tier-boss", WorldEventService.EventType.WORLD_BOSS, 1, 0, 0, now, now + 1000, 10_000);
        svc.activate("tier-boss");
        for (long i = 1; i <= 12; i++) {
            svc.reportDamage("tier-boss", i, 100L * (13 - i));
        }
        // 治疗辅助抬升低伤玩家贡献，但不足以反超最高伤害
        svc.reportAssist("tier-boss", 12L, 500L, 0L);

        Map<String, Object> settle = svc.settle("tier-boss", 10);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> plans = (List<Map<String, Object>>) settle.get("grantPlans");
        assertThat(plans).hasSize(12);
        assertThat(plans.get(0).get("rewardTier")).isEqualTo("S");
        assertThat(plans.get(0).get("itemId")).isEqualTo(50001);
        assertThat(plans.get(0).get("playerId")).isEqualTo(1L);
        Map<String, Object> healerPlan = plans.stream()
                .filter(p -> Long.valueOf(12L).equals(((Number) p.get("playerId")).longValue()))
                .findFirst().orElseThrow();
        assertThat(((Number) healerPlan.get("assistScore")).longValue()).isGreaterThan(0L);
        assertThat(settle.get("mvp")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> mvp = (Map<String, Object>) settle.get("mvp");
        assertThat(mvp.get("playerId")).isEqualTo(1L);
        assertThat(mvpEvent.get()).isNotNull();

        // 12 人：前 10%→S(1人)，前 30%→A，前 50%→B
        long sCount = plans.stream().filter(p -> "S".equals(p.get("rewardTier"))).count();
        long aCount = plans.stream().filter(p -> "A".equals(p.get("rewardTier"))).count();
        long bCount = plans.stream().filter(p -> "B".equals(p.get("rewardTier"))).count();
        assertThat(sCount).isEqualTo(1);
        assertThat(aCount).isGreaterThanOrEqualTo(1);
        assertThat(bCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    public void rewardTierByPercentileBoundaries() {
        assertThat(WorldEventService.rewardTierByPercentile(0, 10)).isEqualTo("S");
        assertThat(WorldEventService.rewardTierByPercentile(1, 10)).isEqualTo("A");
        assertThat(WorldEventService.rewardTierByPercentile(2, 10)).isEqualTo("A");
        assertThat(WorldEventService.rewardTierByPercentile(4, 10)).isEqualTo("B");
        assertThat(WorldEventService.rewardTierByPercentile(5, 10)).isEqualTo("C");
    }
}
