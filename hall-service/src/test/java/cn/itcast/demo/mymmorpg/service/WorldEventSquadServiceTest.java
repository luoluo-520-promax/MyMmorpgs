package cn.itcast.demo.mymmorpg.service;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class WorldEventSquadServiceTest {

    @Test
    public void createJoinLeaveAroundWorldEvent() {
        WorldEventSquadService svc = new WorldEventSquadService();
        WorldEventSquadService.Squad squad = svc.create(1L, "boss-weekly", 3);
        assertThat(squad.status()).isEqualTo(WorldEventSquadService.SquadStatus.OPEN);
        assertThat(svc.join(2L, squad.squadId()).members()).hasSize(2);
        assertThat(svc.listOpenByEvent("boss-weekly")).hasSize(1);
        svc.join(3L, squad.squadId());
        assertThat(svc.get(squad.squadId()).status()).isEqualTo(WorldEventSquadService.SquadStatus.FULL);
        svc.leave(2L);
        assertThat(svc.get(squad.squadId()).status()).isEqualTo(WorldEventSquadService.SquadStatus.OPEN);
        assertThat(svc.startEvent(squad.squadId()).status())
                .isEqualTo(WorldEventSquadService.SquadStatus.IN_EVENT);
    }
}
