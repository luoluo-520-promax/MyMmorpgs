package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.PlayerFriend;
import cn.itcast.demo.mymmorpg.repository.PlayerFriendRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 异步轻社交联合流程：助战发布/借用（含日限） + 家园发布/拜访。
 */
public class AsyncSocialFlowTest {

    private FriendAssistService assist;
    private HomeVisitService home;
    private PlayerFriendRepository friends;
    private final Map<String, String> store = new HashMap<>();

    @BeforeMethod
    @SuppressWarnings("unchecked")
    public void setUp() {
        store.clear();
        friends = mock(PlayerFriendRepository.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        ListOperations<String, String> listOps = mock(ListOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(redis.opsForList()).thenReturn(listOps);
        org.springframework.data.redis.core.SetOperations<String, String> setOps =
                mock(org.springframework.data.redis.core.SetOperations.class);
        when(redis.opsForSet()).thenReturn(setOps);
        when(setOps.members(anyString())).thenReturn(java.util.Set.of());
        org.springframework.data.redis.core.HashOperations<String, Object, Object> hashOps =
                mock(org.springframework.data.redis.core.HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashOps);
        when(hashOps.increment(anyString(), any(), anyLong())).thenReturn(1L);
        when(hashOps.get(anyString(), any())).thenReturn(null);
        when(hashOps.entries(anyString())).thenReturn(Map.of());
        when(ops.get(anyString())).thenAnswer(inv -> store.get(inv.getArgument(0)));
        doAnswer(inv -> {
            store.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(ops).set(anyString(), anyString(), any(java.time.Duration.class));
        when(ops.increment(anyString())).thenAnswer(inv -> {
            String k = inv.getArgument(0);
            long v = Long.parseLong(store.getOrDefault(k, "0")) + 1;
            store.put(k, String.valueOf(v));
            return v;
        });
        when(ops.decrement(anyString())).thenAnswer(inv -> {
            String k = inv.getArgument(0);
            long v = Long.parseLong(store.getOrDefault(k, "0")) - 1;
            store.put(k, String.valueOf(v));
            return v;
        });
        when(redis.expire(anyString(), any(java.time.Duration.class))).thenReturn(true);
        when(redis.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(redis.delete(anyString())).thenAnswer(inv -> store.remove(inv.getArgument(0)) != null);
        when(listOps.leftPush(anyString(), anyString())).thenReturn(1L);
        doAnswer(inv -> null).when(listOps).trim(anyString(), anyLong(), anyLong());

        ObjectMapper om = new ObjectMapper();
        assist = new FriendAssistService(friends, redis, om, new NoOpSocialEventPublisher());
        home = new HomeVisitService(friends, redis, om, new NoOpSocialEventPublisher());
    }

    @Test
    public void assistDailyLimitAndHomeVisitTogether() {
        long borrower = 1L;
        when(friends.existsByPlayerIdAndFriendId(borrower, 2L)).thenReturn(true);
        when(friends.existsByPlayerIdAndFriendId(borrower, 3L)).thenReturn(true);
        when(friends.existsByPlayerIdAndFriendId(borrower, 4L)).thenReturn(true);
        when(friends.existsByPlayerIdAndFriendId(borrower, 5L)).thenReturn(true);

        assist.offerAssist(2L, Map.of("characterId", 200));
        assist.offerAssist(3L, Map.of("characterId", 300));
        assist.offerAssist(4L, Map.of("characterId", 400));
        assist.offerAssist(5L, Map.of("characterId", 500));

        assertThat(assist.borrow(borrower, 2L).get("ok")).isEqualTo(true);
        assertThat(assist.borrow(borrower, 3L).get("ok")).isEqualTo(true);
        assertThat(assist.borrow(borrower, 4L).get("ok")).isEqualTo(true);
        Map<String, Object> fourth = assist.borrow(borrower, 5L);
        assertThat(fourth.get("ok")).isEqualTo(false);
        assertThat(fourth.get("error")).isEqualTo("daily_limit");

        PlayerFriend f2 = new PlayerFriend();
        f2.setPlayerId(borrower);
        f2.setFriendId(2L);
        when(friends.findByPlayerId(borrower)).thenReturn(List.of(f2));
        List<Map<String, Object>> offers = assist.listOffers(borrower);
        assertThat(offers).isNotEmpty();

        home.publishHome(2L, "助战好友家园", "{\"pot\":true}");
        Map<String, Object> visit = home.visit(borrower, 2L);
        assertThat(visit.get("ok")).isEqualTo(true);

        assertThat(assist.revoke(2L).get("ok")).isEqualTo(true);
        assertThat(assist.listOffers(borrower)).isEmpty();
    }
}
