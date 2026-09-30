package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.protocol.protobuf.ItemReward;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 背包消费 ActivityRewardCommand 的 items 解析。
 */
public class ActivityRewardCommandParseTest {

    @Test
    public void parseItems_csv() {
        List<ItemReward> items = ActivityRewardCommandMqConsumer.parseItems("10:2,20:3,bad,x:y,0:1,-1:2");
        assertThat(items).hasSize(2);
        assertThat(items.get(0).getItemId()).isEqualTo(10);
        assertThat(items.get(0).getCount()).isEqualTo(2);
        assertThat(items.get(1).getItemId()).isEqualTo(20);
        assertThat(items.get(1).getCount()).isEqualTo(3);
        assertThat(ActivityRewardCommandMqConsumer.parseItems("")).isEmpty();
        assertThat(ActivityRewardCommandMqConsumer.parseItems(null)).isEmpty();
    }
}
