package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.PlayerFriend;
import cn.itcast.demo.mymmorpg.repository.PlayerFriendRepository;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SocialRelationGraphServiceTest {

    private SocialRelationGraphService graph;
    private SocialAchievementService achievements;
    private final List<PlayerFriend> friendRows = new ArrayList<>();
    private final Map<String, String> kv = new HashMap<>();
    private final Map<String, Set<String>> sets = new HashMap<>();
    private final Map<String, Map<String, String>> hashes = new HashMap<>();

    @BeforeMethod
    @SuppressWarnings("unchecked")
    public void setUp() {
        friendRows.clear();
        kv.clear();
        sets.clear();
        hashes.clear();
        PlayerFriendRepository friends = mock(PlayerFriendRepository.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        SetOperations<String, String> setOps = mock(SetOperations.class);
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(redis.opsForSet()).thenReturn(setOps);
        when(redis.opsForHash()).thenReturn(hashOps);
        when(ops.get(anyString())).thenAnswer(inv -> kv.get(String.valueOf(inv.getArguments()[0])));
        when(ops.increment(anyString(), anyLong())).thenAnswer(inv -> {
            String k = String.valueOf(inv.getArguments()[0]);
            long d = ((Number) inv.getArguments()[1]).longValue();
            long v = Long.parseLong(kv.getOrDefault(k, "0")) + d;
            kv.put(k, String.valueOf(v));
            return v;
        });
        when(redis.expire(anyString(), any(java.time.Duration.class))).thenReturn(true);
        when(redis.delete(anyString())).thenAnswer(inv -> kv.remove(String.valueOf(inv.getArguments()[0])) != null);
        doAnswer(inv -> {
            Object[] args = inv.getArguments();
            String key = String.valueOf(args[0]);
            String member = args.length > 1 && args[1] instanceof String[] arr && arr.length > 0
                    ? arr[0] : String.valueOf(args[1]);
            Set<String> s = sets.computeIfAbsent(key, k -> new HashSet<>());
            return s.add(member) ? 1L : 0L;
        }).when(setOps).add(anyString(), org.mockito.ArgumentMatchers.<String>any());
        when(setOps.isMember(anyString(), any())).thenAnswer(inv ->
                sets.getOrDefault(String.valueOf(inv.getArguments()[0]), Set.of())
                        .contains(String.valueOf(inv.getArguments()[1])));
        when(setOps.members(anyString())).thenAnswer(inv ->
                new HashSet<>(sets.getOrDefault(String.valueOf(inv.getArguments()[0]), Set.of())));
        when(setOps.remove(anyString(), any())).thenReturn(1L);
        doAnswer(inv -> {
            Object[] args = inv.getArguments();
            hashes.computeIfAbsent(String.valueOf(args[0]), k -> new HashMap<>())
                    .put(String.valueOf(args[1]), String.valueOf(args[2]));
            return null;
        }).when(hashOps).put(any(), any(), any());
        when(hashOps.increment(anyString(), any(), anyLong())).thenAnswer(inv -> {
            Object[] args = inv.getArguments();
            Map<String, String> m = hashes.computeIfAbsent(String.valueOf(args[0]), k -> new HashMap<>());
            String field = String.valueOf(args[1]);
            long d = ((Number) args[2]).longValue();
            long v = Long.parseLong(m.getOrDefault(field, "0")) + d;
            m.put(field, String.valueOf(v));
            return v;
        });
        when(hashOps.entries(anyString())).thenAnswer(inv ->
                new HashMap<>(hashes.getOrDefault(String.valueOf(inv.getArguments()[0]), Map.of())));
        doAnswer(inv -> {
            Object[] args = inv.getArguments();
            kv.put(String.valueOf(args[0]), String.valueOf(args[1]));
            return null;
        }).when(ops).set(anyString(), anyString());
        when(friends.existsByPlayerIdAndFriendId(anyLong(), anyLong())).thenAnswer(inv -> {
            long a = ((Number) inv.getArguments()[0]).longValue();
            long b = ((Number) inv.getArguments()[1]).longValue();
            return friendRows.stream().anyMatch(r -> r.getPlayerId() == a && r.getFriendId() == b);
        });
        when(friends.save(any(PlayerFriend.class))).thenAnswer(inv -> {
            PlayerFriend f = (PlayerFriend) inv.getArguments()[0];
            friendRows.add(f);
            return f;
        });
        when(friends.findByPlayerId(anyLong())).thenAnswer(inv -> {
            long pid = ((Number) inv.getArguments()[0]).longValue();
            return friendRows.stream().filter(r -> r.getPlayerId() == pid).toList();
        });
        doAnswer(inv -> {
            long a = ((Number) inv.getArguments()[0]).longValue();
            long b = ((Number) inv.getArguments()[1]).longValue();
            friendRows.removeIf(r -> r.getPlayerId() == a && r.getFriendId() == b);
            return null;
        }).when(friends).deleteByPlayerIdAndFriendId(anyLong(), anyLong());
        achievements = new SocialAchievementService(redis);
        graph = new SocialRelationGraphService(friends, redis, new NoOpSocialEventPublisher(), achievements);
    }

    @Test
    public void addFriendBuildsGraphAndUnlocksPath() {
        Map<String, Object> added = graph.addFriend(1L, 2L);
        assertThat(added.get("ok")).isEqualTo(true);
        assertThat(graph.getIntimacy(1L, 2L)).isEqualTo(1L);
        Map<String, Object> g = graph.graphOf(1L);
        assertThat(g.get("friendCount")).isEqualTo(1);
        assertThat(graph.block(1L, 3L).get("ok")).isEqualTo(true);
    }

    @Test
    public void achievementsFromAssistEvents() {
        for (int i = 0; i < 10; i++) {
            achievements.applySocialEvent("ASSIST_SETTLED", 8L, 9L);
        }
        Map<String, Object> profile = achievements.profile(8L);
        @SuppressWarnings("unchecked")
        List<String> unlocked = (List<String>) profile.get("unlocked");
        assertThat(unlocked).contains("assist_10");
    }
}
