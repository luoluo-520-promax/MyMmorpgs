package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.world.social.CrossServerPresenceService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 跨服好友 + 在线态 enrichment 业务流程。
 */
public class CrossServerPresenceFlowTest {

    @Test
    @SuppressWarnings("unchecked")
    public void friendsDetailedIncludesPresenceAndRank() {
        ObjectProvider<StringRedisTemplate> redis = mock(ObjectProvider.class);
        when(redis.getIfAvailable()).thenReturn(null);
        CrossServerFriendService friends = new CrossServerFriendService(redis);
        friends.addFriend(1L, 2L);
        friends.presenceHeartbeat(2L, "好友二号", "scene-liyue", 2);

        Map<String, Object> view = friends.toView(1L);
        assertThat(view.get("ok")).isEqualTo(true);
        assertThat((List<Long>) view.get("friends")).contains(2L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> detailed = (List<Map<String, Object>>) view.get("friendsDetailed");
        assertThat(detailed).hasSize(1);
        assertThat(detailed.get(0).get("online")).isEqualTo(true);
        assertThat(detailed.get(0).get("displayName")).isEqualTo("好友二号");
        assertThat(detailed.get(0).get("nodeId")).isEqualTo("scene-liyue");

        friends.upsertRank("world_boss", 2L, 999.0);
        // 无 Redis 时排行榜为空，但调用不应抛错
        assertThat(friends.topRank("world_boss", 10)).isEmpty();
    }
}
