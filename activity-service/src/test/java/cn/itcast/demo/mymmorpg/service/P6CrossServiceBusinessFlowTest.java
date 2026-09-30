package cn.itcast.demo.mymmorpg.service;

import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 世界 Boss 新业务流程：伤害贡献 + 治疗/护盾折算 + 百分位分档 + MVP 广播。
 */
public class P6CrossServiceBusinessFlowTest {

    @Test
    public void bossDamageAssistPercentileSettleAndMvp() {
        WorldEventService boss = new WorldEventService();
        AtomicReference<Map<String, Object>> mvpEvt = new AtomicReference<>();
        boss.setMvpBroadcastListener(mvpEvt::set);
        long playerId = 77_001L;
        long t = System.currentTimeMillis();
        boss.schedule("p6-boss", WorldEventService.EventType.WORLD_BOSS, 1, 100, 100, t, t + 60_000, 50_000);
        boss.activate("p6-boss");
        boss.reportDamage("p6-boss", playerId, 8_000);
        boss.reportDamage("p6-boss", playerId + 1, 3_000);
        boss.reportDamage("p6-boss", playerId + 2, 1_500);
        boss.reportDamage("p6-boss", playerId + 3, 800);
        boss.reportAssist("p6-boss", playerId + 3, 2_000, 1_000);

        Map<String, Object> settle = boss.settle("p6-boss", 4);
        assertThat(settle.get("ok")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> grants = (List<Map<String, Object>>) settle.get("grantPlans");
        assertThat(grants).hasSize(4);
        assertThat(grants.get(0).get("playerId")).isEqualTo(playerId);
        assertThat(grants.get(0).get("rewardTier")).isEqualTo("S");
        @SuppressWarnings("unchecked")
        Map<String, Object> mvp = (Map<String, Object>) settle.get("mvp");
        assertThat(mvp.get("playerId")).isEqualTo(playerId);
        assertThat(mvp.get("damage")).isEqualTo(8_000L);
        assertThat(mvpEvt.get()).isNotNull();
        assertThat(mvpEvt.get().get("type")).isEqualTo("BOSS_MVP");

        Map<String, Object> healer = grants.stream()
                .filter(p -> ((Number) p.get("playerId")).longValue() == playerId + 3)
                .findFirst().orElseThrow();
        assertThat(((Number) healer.get("assistScore")).longValue()).isGreaterThan(0L);
        assertThat(((Number) healer.get("effectiveScore")).longValue())
                .isGreaterThan(((Number) healer.get("damage")).longValue());
    }
}
