package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.client.GuildBagClient;
import cn.itcast.demo.mymmorpg.client.GuildChatClient;
import cn.itcast.demo.mymmorpg.model.GuildRole;
import cn.itcast.demo.mymmorpg.protocol.BagRetCode;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 公会完整业务流程：创建扣费 → 聊天同步 → 加入/满员 → 科技 → 远征分档结算 → 转让/退会 → 周重置。
 */
public class GuildBusinessFlowTest {

    private GuildBagClient bagClient;
    private GuildChatClient chatClient;
    private GuildService guildService;
    private GuildExpeditionService expeditionService;
    private final AtomicInteger bagConsumeRc = new AtomicInteger(BagRetCode.OK);
    private final List<Map<String, Object>> granted = new ArrayList<>();

    @BeforeMethod
    @SuppressWarnings("unchecked")
    public void setUp() {
        granted.clear();
        bagConsumeRc.set(BagRetCode.OK);
        bagClient = mock(GuildBagClient.class);
        chatClient = mock(GuildChatClient.class);
        when(bagClient.consume(anyLong(), any())).thenAnswer(inv -> bagConsumeRc.get());
        when(bagClient.grant(anyLong(), any())).thenAnswer(inv -> {
            granted.add(Map.copyOf(inv.getArgument(1)));
            return BagRetCode.OK;
        });
        when(chatClient.onCreated(any())).thenReturn(Map.of("ok", true));
        when(chatClient.syncMember(any())).thenReturn(Map.of("ok", true));

        ObjectProvider<GuildBagClient> bagProvider = mock(ObjectProvider.class);
        ObjectProvider<GuildChatClient> chatProvider = mock(ObjectProvider.class);
        ObjectProvider redisProvider = mock(ObjectProvider.class);
        ObjectProvider<LocalDamageCounter> damageCounterProvider = mock(ObjectProvider.class);
        when(bagProvider.getIfAvailable()).thenReturn(bagClient);
        when(chatProvider.getIfAvailable()).thenReturn(chatClient);
        when(redisProvider.getIfAvailable()).thenReturn(null);
        when(damageCounterProvider.getIfAvailable()).thenReturn(new LocalDamageCounter(redisProvider));

        guildService = new GuildService(bagProvider, chatProvider, redisProvider);
        expeditionService = new GuildExpeditionService(guildService, redisProvider, bagProvider, damageCounterProvider);
    }

    @Test
    public void createFailsWhenBagInsufficient_thenSucceedsAndNotifiesChat() {
        bagConsumeRc.set(BagRetCode.COUNT_NOT_ENOUGH);
        Map<String, Object> fail = guildService.create(1L, "贫民公会");
        assertThat(fail.get("error")).isEqualTo("ERR_INSUFFICIENT");
        verify(chatClient, never()).onCreated(any());

        bagConsumeRc.set(BagRetCode.OK);
        Map<String, Object> ok = guildService.create(1L, "晨曦旅团");
        assertThat(ok.get("ok")).isEqualTo(true);
        long guildId = ((Number) ok.get("guildId")).longValue();

        ArgumentCaptor<Map<String, Object>> createdCap = ArgumentCaptor.forClass(Map.class);
        verify(chatClient).onCreated(createdCap.capture());
        assertThat(createdCap.getValue().get("guildId")).isEqualTo(Long.toString(guildId));

        ArgumentCaptor<Map<String, Object>> syncCap = ArgumentCaptor.forClass(Map.class);
        verify(chatClient).syncMember(syncCap.capture());
        assertThat(syncCap.getValue().get("playerId")).isEqualTo(1L);
        assertThat(syncCap.getValue().get("leave")).isEqualTo(false);

        ArgumentCaptor<Map<String, Object>> costCap = ArgumentCaptor.forClass(Map.class);
        verify(bagClient, times(2)).consume(eq(1L), costCap.capture());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> costs = (List<Map<String, Object>>) costCap.getValue().get("costs");
        assertThat(costs).anyMatch(c -> ((Number) c.get("itemId")).intValue() == 10001
                && ((Number) c.get("count")).intValue() == 30_000);
        assertThat(costs).anyMatch(c -> ((Number) c.get("itemId")).intValue() == 10002
                && ((Number) c.get("count")).intValue() == 300);
    }

    @Test
    public void fullLifecycle_joinLeaveTransferTechCapacity() {
        Map<String, Object> created = guildService.create(10L, "秩序骑士");
        long guildId = ((Number) created.get("guildId")).longValue();

        assertThat(guildService.join(11L, guildId).get("ok")).isEqualTo(true);
        assertThat(guildService.join(12L, guildId).get("ok")).isEqualTo(true);
        assertThat(guildService.create(11L, "重复入会").get("error")).isEqualTo("ALREADY_IN_GUILD");
        assertThat(guildService.create(99L, "秩序骑士").get("error")).isEqualTo("NAME_TAKEN");

        // 会长未转让前禁止退会
        assertThat(guildService.leave(10L).get("error")).isEqualTo("LEADER_MUST_TRANSFER");
        assertThat(guildService.transfer(10L, 11L, false).get("ok")).isEqualTo(true);
        assertThat(guildService.requireGuild(guildId).getLeaderId()).isEqualTo(11L);
        assertThat(guildService.leave(10L).get("ok")).isEqualTo(true);

        // 科技满级
        for (int i = 0; i < 3; i++) {
            assertThat(guildService.upgradeTech(11L, "HP").get("ok")).isEqualTo(true);
        }
        assertThat(guildService.upgradeTech(11L, "HP").get("error")).isEqualTo("TECH_MAX");
        assertThat(guildService.info(guildId).get("hpBonusRatio")).isEqualTo(0.06);
        assertThat(guildService.upgradeTech(12L, "ATTACK").get("error")).isEqualTo("NO_PERMISSION");

        guildService.requireGuild(guildId).setLevel(2);
        assertThat(guildService.info(guildId).get("capacity")).isEqualTo(30);
        guildService.requireGuild(guildId).setLevel(3);
        assertThat(guildService.info(guildId).get("capacity")).isEqualTo(40);
    }

    @Test
    public void expeditionFlow_openDamageSettleGrantAndWeeklyReset() {
        Map<String, Object> created = guildService.create(100L, "远征先锋");
        long guildId = ((Number) created.get("guildId")).longValue();
        for (long pid = 101; pid <= 110; pid++) {
            guildService.join(pid, guildId);
        }

        assertThat(expeditionService.open(101L, guildId).get("error")).isEqualTo("NOT_LEADER");
        Map<String, Object> opened = expeditionService.open(100L, guildId);
        assertThat(opened.get("ok")).isEqualTo(true);
        // HP = 1e6 * (1 + 0.2 * guild_level)，1 级 = 1.2e6
        assertThat(((Number) opened.get("bossHpMax")).longValue()).isEqualTo(1_200_000L);
        assertThat(expeditionService.open(100L, guildId).get("error")).isEqualTo("ALREADY_ACTIVE");

        guildService.requireGuild(guildId).setLevel(3);
        // 重置后重新开一局验证血量公式
        expeditionService.weeklyResetAll();
        Map<String, Object> openedLv3 = expeditionService.open(100L, guildId);
        assertThat(((Number) openedLv3.get("bossHpMax")).longValue()).isEqualTo(1_600_000L);

        for (int i = 0; i < 10; i++) {
            long pid = 100L + i;
            Map<String, Object> dmg = expeditionService.reportDamage(guildId, pid, 10_000 - i * 100L);
            assertThat(dmg.get("ok")).isEqualTo(true);
        }
        assertThat(expeditionService.reportDamage(guildId, 999L, 100).get("error")).isEqualTo("NOT_MEMBER");

        granted.clear();
        Map<String, Object> settled = expeditionService.settle(guildId);
        assertThat(settled.get("ok")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> plans = (List<Map<String, Object>>) settled.get("grantPlans");
        long gold = plans.stream().filter(p -> "GOLD".equals(p.get("tier"))).count();
        long silver = plans.stream().filter(p -> "SILVER".equals(p.get("tier"))).count();
        long bronze = plans.stream().filter(p -> "BRONZE".equals(p.get("tier"))).count();
        assertThat(gold).isEqualTo(1);
        assertThat(silver).isEqualTo(2);
        assertThat(bronze).isEqualTo(2);
        assertThat(plans).allMatch(p -> ((Number) p.get("itemId")).intValue() == 90_001);
        assertThat(granted).hasSize(5);

        int cleared = expeditionService.weeklyResetAll();
        assertThat(cleared).isGreaterThanOrEqualTo(0);
        assertThat(expeditionService.get(guildId)).isNull();
    }

    @Test
    public void leaderInactivePrefersViceThenContribution() {
        Map<String, Object> created = guildService.create(1L, "自动转让团");
        long guildId = ((Number) created.get("guildId")).longValue();
        guildService.join(2L, guildId);
        guildService.join(3L, guildId);
        guildService.addContribution(guildId, 2L, 999);
        guildService.addContribution(guildId, 3L, 10);
        // 将 3 设为副会：手动改角色
        guildService.requireGuild(guildId).getMembers().get(3L).setRole(GuildRole.VICE);
        guildService.requireGuild(guildId).getMembers().get(1L)
                .setLastLoginMs(System.currentTimeMillis() - GuildService.LEADER_INACTIVE_MS - 1);

        List<Map<String, Object>> transferred = guildService.scanLeaderTransfer();
        assertThat(transferred).hasSize(1);
        // 副会优先于更高贡献的普通成员
        assertThat(guildService.requireGuild(guildId).getLeaderId()).isEqualTo(3L);
    }
}
