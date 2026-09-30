package cn.itcast.demo.mymmorpg.world.npc;

import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class NpcBehaviorFsmTest {

    @Test
    public void shopAndPatrolStates() {
        NpcBehaviorFsm fsm = new NpcBehaviorFsm();
        fsm.register(new NpcBehaviorFsm.NpcProfile(
                1L, "店员", true, 8, 22, List.of()));
        fsm.register(new NpcBehaviorFsm.NpcProfile(
                2L, "守卫", false, 0, 0,
                List.of(
                        new NpcBehaviorFsm.PatrolWaypoint(0, 0, 0, 1),
                        new NpcBehaviorFsm.PatrolWaypoint(10, 0, 0, 1))));
        assertThat(fsm.tick(1L, 10, 1000L).state()).isEqualTo(NpcBehaviorFsm.State.SHOP_OPEN);
        assertThat(fsm.tick(1L, 23, 1000L).state()).isEqualTo(NpcBehaviorFsm.State.SHOP_CLOSED);
        var patrol = fsm.tick(2L, 12, 1000L);
        assertThat(patrol.state()).isEqualTo(NpcBehaviorFsm.State.PATROL);
        assertThat(fsm.startDialogue(2L, 5000, 2000L).state()).isEqualTo(NpcBehaviorFsm.State.DIALOGUE);
    }
}
