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

/**
 * 社交关系图谱 + 事件驱动成就业务流程：
 * 加好友→亲密度→黑名单；助战/家园/组队事件→成就解锁→装备称号头像框。
 */
public class SocialAchievementBusinessFlowTest {

    private SocialRelationGraphService graph;
    private SocialAchievementService achievements;
    private SocialEventMqConsumer consumer;
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
            Object[] args = inv.getArguments();
            String k = String.valueOf(args[0]);
            long d = ((Number) args[1]).longValue();
            long v = Long.parseLong(kv.getOrDefault(k, "0")) + d;
            kv.put(k, String.valueOf(v));
            return v;
        });
        when(redis.expire(anyString(), any(java.time.Duration.class))).thenReturn(true);
        when(redis.delete(anyString())).thenAnswer(inv -> kv.remove(String.valueOf(inv.getArguments()[0])) != null);
        when(setOps.add(anyString(), anyString())).thenAnswer(inv -> {
            sets.computeIfAbsent(inv.getArgument(0), k -> new HashSet<>()).add(inv.getArgument(1));
            return 1L;
        });
        when(setOps.isMember(anyString(), anyString())).thenAnswer(inv ->
                sets.getOrDefault((String) inv.getArgument(0), Set.of()).contains(inv.getArgument(1)));
        when(setOps.members(anyString())).thenAnswer(inv ->
                new HashSet<>(sets.getOrDefault((String) inv.getArgument(0), Set.of())));
        when(setOps.remove(anyString(), anyString())).thenAnswer(inv -> {
            Set<String> s = sets.get(inv.getArgument(0));
            if (s == null) {
                return 0L;
            }
            return s.remove((String) inv.getArgument(1)) ? 1L : 0L;
        });
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
        consumer = new SocialEventMqConsumer(achievements, "127.0.0.1:9876", "SOCIAL_EVENTS", "test-group");
    }

    @Test
    public void friendGraphIntimacyAndBlockFlow() {
        assertThat(graph.addFriend(1L, 2L).get("ok")).isEqualTo(true);
        assertThat(graph.addFriend(1L, 3L).get("ok")).isEqualTo(true);
        assertThat(graph.addIntimacy(1L, 2L, 5).get("intimacy")).isEqualTo(6L);
        Map<String, Object> g = graph.graphOf(1L);
        assertThat(g.get("friendCount")).isEqualTo(2);

        assertThat(graph.block(1L, 2L).get("ok")).isEqualTo(true);
        assertThat(graph.isBlocked(1L, 2L)).isTrue();
        // 拉黑后不应再加好友
        assertThat(graph.addFriend(1L, 2L).get("error")).isEqualTo("blocked");
        assertThat(graph.unblock(1L, 2L).get("ok")).isEqualTo(true);
    }

    @Test
    public void mqBodyIngestUnlocksAchievementsAndEquip() {
        // 模拟 RocketMQ 消息体被 SocialEventMqConsumer 解析
        for (int i = 0; i < 10; i++) {
            consumer.handle("social|type=ASSIST_SETTLED|actor=100|target=200|ts=1|payload=victory=true");
        }
        for (int i = 0; i < 20; i++) {
            consumer.handle("social|type=HOME_VISITED|actor=100|target=300|ts=1|payload=");
        }
        for (int i = 0; i < 5; i++) {
            consumer.handle("social|type=PARTY_FORMED|actor=100|target=0|ts=1|payload=members=3");
        }

        Map<String, Object> profile = achievements.profile(100L);
        @SuppressWarnings("unchecked")
        List<String> unlocked = (List<String>) profile.get("unlocked");
        assertThat(unlocked).contains("assist_10", "home_visit_20", "party_5");
        @SuppressWarnings("unchecked")
        List<String> titles = (List<String>) profile.get("titles");
        assertThat(titles).contains("热心助战", "家园访客", "组队先锋");

        assertThat(achievements.equipTitle(100L, "热心助战").get("ok")).isEqualTo(true);
        assertThat(achievements.equipFrame(100L, "frame_assist_1").get("ok")).isEqualTo(true);
        Map<String, Object> after = achievements.profile(100L);
        assertThat(after.get("equippedTitle")).isEqualTo("热心助战");
        assertThat(after.get("equippedFrame")).isEqualTo("frame_assist_1");
    }

    @Test
    public void fiftyFriendsUnlocksSocialTitle() {
        for (long i = 2; i <= 51; i++) {
            assertThat(graph.addFriend(1L, i).get("ok")).isEqualTo(true);
        }
        Map<String, Object> profile = achievements.profile(1L);
        @SuppressWarnings("unchecked")
        List<String> unlocked = (List<String>) profile.get("unlocked");
        assertThat(unlocked).contains("friends_10", "friends_50");
        @SuppressWarnings("unchecked")
        List<String> titles = (List<String>) profile.get("titles");
        assertThat(titles).contains("人脉达人");
    }
}
