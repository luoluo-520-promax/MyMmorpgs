package cn.itcast.demo.mymmorpg.service;

import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 玩法驱动轻社交：事件小队创建/满员/换队/解散完整流程。
 */
public class WorldEventSquadFlowTest {

    @Test
    public void squadLifecycleAroundWorldEvent() {
        WorldEventSquadService svc = new WorldEventSquadService();
        WorldEventSquadService.Squad created = svc.create(1L, "wb-raid", 3);
        assertThat(created.leaderId()).isEqualTo(1L);
        assertThat(svc.currentOf(1L).squadId()).isEqualTo(created.squadId());

        svc.join(2L, created.squadId());
        assertThat(svc.listOpenByEvent("wb-raid")).hasSize(1);

        WorldEventSquadService.Squad full = svc.join(3L, created.squadId());
        assertThat(full.status()).isEqualTo(WorldEventSquadService.SquadStatus.FULL);
        assertThatThrownBy(() -> svc.join(4L, created.squadId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("squad_full");

        WorldEventSquadService.Squad afterLeave = svc.leave(2L);
        assertThat(afterLeave.status()).isEqualTo(WorldEventSquadService.SquadStatus.OPEN);
        assertThat(afterLeave.members()).hasSize(2);

        // 换队：玩家 3 创建新队会先离开旧队
        WorldEventSquadService.Squad other = svc.create(3L, "wb-raid", 4);
        assertThat(svc.currentOf(3L).squadId()).isEqualTo(other.squadId());
        assertThat(svc.get(created.squadId()).members()).extracting(WorldEventSquadService.SquadMember::playerId)
                .containsExactly(1L);

        Map<String, Object> view = svc.toView(svc.startEvent(other.squadId()));
        assertThat(view.get("status")).isEqualTo("IN_EVENT");
        assertThat(view.get("ok")).isEqualTo(true);
    }

    @Test
    public void lastMemberLeave_disbands() {
        WorldEventSquadService svc = new WorldEventSquadService();
        WorldEventSquadService.Squad squad = svc.create(9L, "solo-event", 4);
        WorldEventSquadService.Squad disbanded = svc.leave(9L);
        assertThat(disbanded.status()).isEqualTo(WorldEventSquadService.SquadStatus.DISBANDED);
        assertThat(svc.currentOf(9L)).isNull();
    }

    @Test
    public void invalidCreate_throws() {
        WorldEventSquadService svc = new WorldEventSquadService();
        assertThatThrownBy(() -> svc.create(0L, "x", 4)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> svc.create(1L, " ", 4)).isInstanceOf(IllegalArgumentException.class);
    }
}
