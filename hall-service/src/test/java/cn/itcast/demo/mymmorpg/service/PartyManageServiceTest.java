package cn.itcast.demo.mymmorpg.service;

import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class PartyManageServiceTest {

    @Test
    public void create_join_kick_transfer_leave() {
        PartyManageService svc = new PartyManageService();
        Map<String, Object> created = svc.create(1L);
        assertThat(created.get("ok")).isEqualTo(true);
        String invite = String.valueOf(created.get("inviteCode"));

        assertThat(svc.joinByInvite(2L, invite).get("ok")).isEqualTo(true);
        assertThat(svc.joinByInvite(3L, invite).get("ok")).isEqualTo(true);
        assertThat(svc.kick(1L, 3L).get("memberCount")).isEqualTo(2);
        assertThat(svc.transferLeader(1L, 2L).get("leaderId")).isEqualTo(2L);
        assertThat(svc.leave(1L).get("ok")).isEqualTo(true);
        assertThat(svc.mine(2L).get("ok")).isEqualTo(true);
    }
}
