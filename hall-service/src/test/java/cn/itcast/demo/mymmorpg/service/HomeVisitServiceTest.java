package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.PlayerFriend;
import cn.itcast.demo.mymmorpg.repository.PlayerFriendRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 家园拜访流程：发布 → 好友参观 → 列表 → 非好友拒绝；评分/小游戏/商店。
 */
public class HomeVisitServiceTest {

    private HomeVisitService service;
    private PlayerFriendRepository friends;
    private final Map<String, String> store = new HashMap<>();
    private final Map<String, List<String>> lists = new HashMap<>();
    private final Map<String, Set<String>> sets = new HashMap<>();
    private final Map<String, Double> zset = new HashMap<>();

    @BeforeMethod
    @SuppressWarnings("unchecked")
    public void setUp() {
        store.clear();
        lists.clear();
        sets.clear();
        zset.clear();
        friends = mock(PlayerFriendRepository.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        ListOperations<String, String> listOps = mock(ListOperations.class);
        SetOperations<String, String> setOps = mock(SetOperations.class);
        ZSetOperations<String, String> zOps = mock(ZSetOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(redis.opsForList()).thenReturn(listOps);
        when(redis.opsForSet()).thenReturn(setOps);
        when(redis.opsForZSet()).thenReturn(zOps);
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
        when(ops.increment(anyString(), anyLong())).thenAnswer(inv -> {
            String k = inv.getArgument(0);
            long delta = inv.getArgument(1);
            long v = Long.parseLong(store.getOrDefault(k, "0")) + delta;
            store.put(k, String.valueOf(v));
            return v;
        });
        when(redis.hasKey(anyString())).thenAnswer(inv -> store.containsKey(inv.getArgument(0)));
        when(listOps.leftPush(anyString(), anyString())).thenAnswer(inv -> {
            lists.computeIfAbsent(inv.getArgument(0), k -> new ArrayList<>()).add(0, inv.getArgument(1));
            return 1L;
        });
        doAnswer(inv -> null).when(listOps).trim(anyString(), anyLong(), anyLong());
        when(setOps.members(anyString())).thenAnswer(inv ->
                new HashSet<>(sets.getOrDefault(inv.getArgument(0), Set.of())));
        when(setOps.add(anyString(), anyString())).thenAnswer(inv -> {
            sets.computeIfAbsent(inv.getArgument(0), k -> new HashSet<>()).add(inv.getArgument(1));
            return 1L;
        });
        when(setOps.isMember(anyString(), anyString())).thenAnswer(inv ->
                sets.getOrDefault(inv.getArgument(0), Set.of()).contains(inv.getArgument(1)));
        when(zOps.add(anyString(), anyString(), anyDouble())).thenAnswer(inv -> {
            zset.put(inv.getArgument(1), inv.getArgument(2));
            return true;
        });
        when(zOps.reverseRangeWithScores(anyString(), anyLong(), anyLong())).thenReturn(Set.of());
        service = new HomeVisitService(friends, redis, new ObjectMapper(), new NoOpSocialEventPublisher());
    }

    @Test
    public void publishVisitAndListFriendHomes() {
        Map<String, Object> published = service.publishHome(10L, "星轨壶", "{\"furniture\":[1,2]}");
        assertThat(published.get("ok")).isEqualTo(true);

        when(friends.existsByPlayerIdAndFriendId(20L, 10L)).thenReturn(true);
        Map<String, Object> visit = service.visit(20L, 10L);
        assertThat(visit.get("ok")).isEqualTo(true);
        assertThat(String.valueOf(visit.get("message"))).contains("星轨壶");

        PlayerFriend rel = new PlayerFriend();
        rel.setPlayerId(20L);
        rel.setFriendId(10L);
        when(friends.findByPlayerId(20L)).thenReturn(List.of(rel));
        List<Map<String, Object>> homes = service.listFriendHomes(20L);
        assertThat(homes).hasSize(1);
        assertThat(homes.get(0).get("name")).isEqualTo("星轨壶");
    }

    @Test
    public void visitRejectsNonFriend() {
        service.publishHome(10L, "私密家园", "{}");
        when(friends.existsByPlayerIdAndFriendId(20L, 10L)).thenReturn(false);
        Map<String, Object> visit = service.visit(20L, 10L);
        assertThat(visit.get("ok")).isEqualTo(false);
        assertThat(visit.get("error")).isEqualTo("not_friend");
    }

    @Test
    public void shopBuyAndMiniGame() {
        service.publishHome(10L, "乐园", "{}");
        store.put("home:coin:10", "300");
        Map<String, Object> buy = service.buyShopItem(10L, "fish_pond");
        assertThat(buy.get("ok")).isEqualTo(true);
        when(friends.existsByPlayerIdAndFriendId(20L, 10L)).thenReturn(true);
        Map<String, Object> game = service.playMiniGame(20L, 10L, "fish_pond", 100);
        assertThat(game.get("ok")).isEqualTo(true);
        assertThat(game.get("game")).isEqualTo("FISHING");
    }
}
