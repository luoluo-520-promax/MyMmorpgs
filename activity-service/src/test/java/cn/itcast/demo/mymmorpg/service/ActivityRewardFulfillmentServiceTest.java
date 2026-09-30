package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.model.ActivityRewardCommand;
import cn.itcast.demo.mymmorpg.model.RewardTierPayload;
import cn.itcast.demo.mymmorpg.port.ActivityItemGrantPort;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ItemReward;
import org.springframework.beans.factory.ObjectProvider;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 活动发奖：Outbox 优先 vs 同步背包回退。
 */
public class ActivityRewardFulfillmentServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    public void outboxEnabled_enqueuesCommand() {
        ActivityItemGrantPort grantPort = mock(ActivityItemGrantPort.class);
        MqOutboxService outbox = mock(MqOutboxService.class);
        ObjectProvider<MqOutboxService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(outbox);

        ActivityRewardFulfillmentService svc =
                new ActivityRewardFulfillmentService(grantPort, provider, true);

        RewardTierPayload t = new RewardTierPayload();
        t.itemId = 100;
        t.count = 2;
        int rc = svc.fulfill(9L, 1L, "activity:1:tiers:1", List.of(t), List.of());
        assertThat(rc).isEqualTo(0);
        verify(outbox).enqueueActivityRewardCommand(eq(9L), eq(1L), eq("activity:1:tiers:1"), eq("100:2"));
        verify(grantPort, never()).grantItemsForActivity(anyLong(), anyString(), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void outboxDisabled_fallsBackToSyncGrant() {
        ActivityItemGrantPort grantPort = mock(ActivityItemGrantPort.class);
        when(grantPort.grantItemsForActivity(anyLong(), anyString(), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(0);
        ObjectProvider<MqOutboxService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        ActivityRewardFulfillmentService svc =
                new ActivityRewardFulfillmentService(grantPort, provider, false);
        RewardTierPayload t = new RewardTierPayload();
        t.itemId = 7;
        t.count = 1;
        List<ItemReward> items = List.of(ItemReward.newBuilder().setItemId(7).setCount(1).build());
        assertThat(svc.fulfill(1L, 2L, "k", List.of(t), items)).isEqualTo(0);
        verify(grantPort).grantItemsForActivity(1L, "k", items);
    }

    @Test
    public void itemsCsv_helpers() {
        assertThat(ActivityRewardCommand.toItemsCsv(1, 2)).isEqualTo("1:2");
        assertThat(ActivityRewardCommand.appendItem("1:2", 3, 4)).isEqualTo("1:2,3:4");
        assertThat(ActivityRewardCommand.appendItem("", 10, 2)).isEqualTo("10:2");
    }
}
