package cn.itcast.demo.mymmorpg.world.resource;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class WorldResourceServiceTest {

    @Test
    public void collectRespectsCooldownAndMechanism() {
        WorldResourceService svc = new WorldResourceService();
        svc.registerPoint(new RespawnPoint(
                "p1", 1, 1, RespawnPoint.RespawnKind.GATHER, 1, 0, 1, 10, 60, false));
        long now = 1_000_000L;
        assertThat(svc.collect("p1", 9L, now).get("ok")).isEqualTo(true);
        assertThat(svc.collect("p1", 9L, now + 1000).get("ok")).isEqualTo(false);

        var act = svc.activateMechanism("door-1", false, 5000, now);
        assertThat(act.ok()).isTrue();
        assertThat(act.to()).isEqualTo(MechanismStateMachine.State.ACTIVE);
        var done = svc.completeMechanism("door-1", 5000, now);
        assertThat(done.to()).isEqualTo(MechanismStateMachine.State.COOLDOWN);
        svc.tick(now + 6000);
        assertThat(svc.mechanisms().get("door-1")).isEqualTo(MechanismStateMachine.State.IDLE);
    }
}
