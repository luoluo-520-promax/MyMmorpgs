package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.repository.PlayerFriendRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FriendAssistServiceTest {

    private FriendAssistService service;
    private PlayerFriendRepository friends;
    private final Map<String, String> store = new HashMap<>();

    @BeforeMethod
    @SuppressWarnings("unchecked")
    public void setUp() {
        store.clear();
        friends = mock(PlayerFriendRepository.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
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
        when(redis.hasKey(anyString())).thenAnswer(inv -> store.containsKey(inv.getArgument(0)));
        when(redis.getExpire(anyString())).thenReturn(0L);
        when(redis.delete(anyString())).thenAnswer(inv -> store.remove(inv.getArgument(0)) != null);
        org.springframework.data.redis.core.ListOperations<String, String> listOps =
                mock(org.springframework.data.redis.core.ListOperations.class);
        when(redis.opsForList()).thenReturn(listOps);
        when(listOps.leftPush(anyString(), anyString())).thenReturn(1L);
        doAnswer(inv -> null).when(listOps).trim(anyString(), anyLong(), anyLong());
        org.springframework.data.redis.core.HashOperations<String, Object, Object> hashOps =
                mock(org.springframework.data.redis.core.HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashOps);
        when(hashOps.increment(anyString(), any(), anyLong())).thenReturn(1L);
        when(hashOps.get(anyString(), any())).thenReturn(null);
        when(hashOps.entries(anyString())).thenReturn(Map.of());
        doAnswer(inv -> {
            store.put(String.valueOf(inv.getArgument(0)) + ":" + inv.getArgument(1), String.valueOf(inv.getArgument(2)));
            return null;
        }).when(hashOps).put(anyString(), any(), any());
        service = new FriendAssistService(friends, redis, new ObjectMapper(), new NoOpSocialEventPublisher());
    }

    @Test
    public void offerAndBorrow() {
        service.offerAssist(2L, Map.of("characterId", 22, "power", 1200));
        when(friends.existsByPlayerIdAndFriendId(1L, 2L)).thenReturn(true);
        Map<String, Object> borrowed = service.borrow(1L, 2L);
        assertThat(borrowed.get("ok")).isEqualTo(true);
        assertThat(service.myBorrowed(1L).get("borrowed")).isNotNull();
    }

    @Test
    public void borrowRejectsNonFriend() {
        service.offerAssist(2L, Map.of("characterId", 22));
        when(friends.existsByPlayerIdAndFriendId(1L, 2L)).thenReturn(false);
        assertThat(service.borrow(1L, 2L).get("error")).isEqualTo("not_friend");
    }

    @Test
    public void borrowRejectsWhenNoOffer() {
        when(friends.existsByPlayerIdAndFriendId(1L, 2L)).thenReturn(true);
        assertThat(service.borrow(1L, 2L).get("error")).isEqualTo("no_offer");
    }

    @Test
    public void dailyLimitBlocksFourthBorrow() {
        when(friends.existsByPlayerIdAndFriendId(1L, 2L)).thenReturn(true);
        when(friends.existsByPlayerIdAndFriendId(1L, 3L)).thenReturn(true);
        when(friends.existsByPlayerIdAndFriendId(1L, 4L)).thenReturn(true);
        when(friends.existsByPlayerIdAndFriendId(1L, 5L)).thenReturn(true);
        service.offerAssist(2L, Map.of("characterId", 2));
        service.offerAssist(3L, Map.of("characterId", 3));
        service.offerAssist(4L, Map.of("characterId", 4));
        service.offerAssist(5L, Map.of("characterId", 5));
        assertThat(service.borrow(1L, 2L).get("ok")).isEqualTo(true);
        assertThat(service.borrow(1L, 3L).get("ok")).isEqualTo(true);
        assertThat(service.borrow(1L, 4L).get("ok")).isEqualTo(true);
        assertThat(service.borrow(1L, 5L).get("error")).isEqualTo("daily_limit");
    }

    @Test
    public void multiLineupAndThankAfterSettle() {
        service.offerAssist(2L, 0, Map.of("characterId", 22, "name", "A"));
        service.offerAssist(2L, 1, Map.of("characterId", 33, "name", "B"));
        Map<String, Object> lineups = service.listLineups(2L);
        assertThat(((java.util.List<?>) lineups.get("lineups"))).hasSize(2);

        when(friends.existsByPlayerIdAndFriendId(1L, 2L)).thenReturn(true);
        assertThat(service.borrow(1L, 2L, 1).get("ok")).isEqualTo(true);
        assertThat(service.settle(1L, true).get("ok")).isEqualTo(true);
        Map<String, Object> thanks = service.thank(1L, 2L, 5, "多谢助战");
        assertThat(thanks.get("ok")).isEqualTo(true);
        assertThat(thanks.get("bonusCoin")).isEqualTo(20);
    }
}
