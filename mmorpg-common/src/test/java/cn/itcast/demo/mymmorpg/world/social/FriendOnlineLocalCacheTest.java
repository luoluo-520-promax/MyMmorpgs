package cn.itcast.demo.mymmorpg.world.social;

import cn.itcast.demo.mymmorpg.rpc.PlayerOnlineKeys;
import org.springframework.data.redis.connection.Message;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 好友在线本地缓存 + Pub/Sub 消息解析。
 */
public class FriendOnlineLocalCacheTest {

    @Test
    public void onMessage_updatesCache() {
        FriendOnlineLocalCache cache = new FriendOnlineLocalCache(mock(org.springframework.beans.factory.ObjectProvider.class),
                mock(org.springframework.beans.factory.ObjectProvider.class));
        Message msg = mock(Message.class);
        org.mockito.Mockito.when(msg.getBody()).thenReturn("12345:1".getBytes());
        cache.onMessage(msg, PlayerOnlineKeys.ONLINE_CHANNEL.getBytes());
        assertThat(cache.snapshot()).containsEntry(12345L, true);

        org.mockito.Mockito.when(msg.getBody()).thenReturn("12345:0".getBytes());
        cache.onMessage(msg, null);
        assertThat(cache.snapshot()).containsEntry(12345L, false);
    }

    @Test
    public void put_andSnapshot() {
        FriendOnlineLocalCache cache = new FriendOnlineLocalCache(null, null);
        cache.put(9L, true);
        cache.put(10L, false);
        assertThat(cache.snapshot()).hasSize(2);
    }
}
