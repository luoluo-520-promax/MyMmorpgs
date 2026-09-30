package cn.itcast.demo.mymmorpg.service;

import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class WorldEventServiceTest {

    @Test
    public void worldBossDamageAndSettle() {
        WorldEventService svc = new WorldEventService();
        svc.schedule("wb-1", WorldEventService.EventType.WORLD_BOSS, 1, 100, 200,
                System.currentTimeMillis(), System.currentTimeMillis() + 3600_000L, 1000);
        svc.activate("wb-1");
        svc.reportDamage("wb-1", 11L, 400);
        svc.reportDamage("wb-1", 22L, 700);
        Map<String, Object> settle = svc.settle("wb-1", 5);
        assertThat(settle.get("ok")).isEqualTo(true);
        assertThat(settle.get("participantCount")).isEqualTo(2);
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> plans =
                (java.util.List<Map<String, Object>>) settle.get("grantPlans");
        assertThat(plans).hasSize(2);
        assertThat(plans.get(0).get("rewardTier")).isEqualTo("S");
        assertThat(plans.get(0).get("itemId")).isEqualTo(50001);
        assertThat(svc.get("wb-1").state()).isEqualTo(WorldEventService.EventState.CLOSED);
    }
}
