package cn.itcast.demo.mymmorpg.service;

import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class GuildServiceTest {

    private GuildService guildService;
    private GuildExpeditionService expeditionService;

    @BeforeMethod
    public void setUp() {
        ObjectProvider empty = Mockito.mock(ObjectProvider.class);
        Mockito.when(empty.getIfAvailable()).thenReturn(null);
        guildService = new GuildService(empty, empty, empty);
        expeditionService = new GuildExpeditionService(guildService, empty, empty, empty);
    }

    @Test
    public void createJoinTechAndCapacity() {
        Map<String, Object> created = guildService.create(1L, "晨曦旅团");
        assertThat(created.get("ok")).isEqualTo(true);
        long guildId = ((Number) created.get("guildId")).longValue();
        assertThat(created.get("capacity")).isEqualTo(20);

        assertThat(guildService.join(2L, guildId).get("ok")).isEqualTo(true);
        Map<String, Object> tech = guildService.upgradeTech(1L, "ATTACK");
        assertThat(tech.get("ok")).isEqualTo(true);
        assertThat(tech.get("attackBonusRatio")).isEqualTo(0.02);

        guildService.requireGuild(guildId).setLevel(3);
        assertThat(guildService.info(guildId).get("capacity")).isEqualTo(40);
    }

    @Test
    public void leaderInactiveAutoTransferToHighestContribution() {
        Map<String, Object> created = guildService.create(10L, "远征团");
        long guildId = ((Number) created.get("guildId")).longValue();
        guildService.join(11L, guildId);
        guildService.join(12L, guildId);
        guildService.addContribution(guildId, 11L, 100);
        guildService.addContribution(guildId, 12L, 500);

        var leader = guildService.requireGuild(guildId).getMembers().get(10L);
        leader.setLastLoginMs(System.currentTimeMillis() - GuildService.LEADER_INACTIVE_MS - 1000);

        List<Map<String, Object>> transferred = guildService.scanLeaderTransfer();
        assertThat(transferred).hasSize(1);
        assertThat(guildService.requireGuild(guildId).getLeaderId()).isEqualTo(12L);
    }

    @Test
    public void expeditionSettleByDamagePercentile() {
        Map<String, Object> created = guildService.create(100L, "Boss团");
        long guildId = ((Number) created.get("guildId")).longValue();
        for (long pid = 101; pid <= 110; pid++) {
            guildService.join(pid, guildId);
        }
        assertThat(expeditionService.open(100L, guildId).get("ok")).isEqualTo(true);

        // 10 名成员伤害递减：前 10%→GOLD(1人)、前30%→SILVER、前50%→BRONZE
        for (int i = 0; i < 10; i++) {
            long pid = 100L + i;
            expeditionService.reportDamage(guildId, pid, 1000 - i * 10L);
        }
        Map<String, Object> settled = expeditionService.settle(guildId);
        assertThat(settled.get("ok")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> plans = (List<Map<String, Object>>) settled.get("grantPlans");
        assertThat(plans).isNotEmpty();
        assertThat(plans.get(0).get("tier")).isEqualTo("GOLD");
        assertThat(plans.get(0).get("count")).isEqualTo(200);
        assertThat(GuildExpeditionService.tierOf(0, 10)).isEqualTo("GOLD");
        assertThat(GuildExpeditionService.tierOf(2, 10)).isEqualTo("SILVER");
        assertThat(GuildExpeditionService.tierOf(4, 10)).isEqualTo("BRONZE");
        assertThat(GuildExpeditionService.tierOf(5, 10)).isEqualTo("NONE");
    }
}
