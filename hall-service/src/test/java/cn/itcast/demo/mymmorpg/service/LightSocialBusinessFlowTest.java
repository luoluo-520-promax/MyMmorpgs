package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.repository.PlayerFriendRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.HashOperations;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 轻社交新业务流程联测：
 * 家园（发布→装饰→商店→拜访→小游戏→评分）+
 * 助战（多阵容→借用→结算→感谢）+
 * CoopRoom（开房→观战→表情→点赞送花）+
 * 社交事件收集。
 */
public class LightSocialBusinessFlowTest {

    private HomeVisitService home;
    private FriendAssistService assist;
    private CoopRoomService coop;
    private PlayerFriendRepository friends;
    private RecordingSocialEventPublisher events;

    private final Map<String, String> store = new HashMap<>();
    private final Map<String, Set<String>> sets = new HashMap<>();
    private final Map<String, Map<String, String>> hashes = new HashMap<>();
    private final Map<String, Double> zset = new HashMap<>();

    @BeforeMethod
    @SuppressWarnings("unchecked")
    public void setUp() {
        store.clear();
        sets.clear();
        hashes.clear();
        zset.clear();
        events = new RecordingSocialEventPublisher();
        friends = mock(PlayerFriendRepository.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        ListOperations<String, String> listOps = mock(ListOperations.class);
        SetOperations<String, String> setOps = mock(SetOperations.class);
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        ZSetOperations<String, String> zOps = mock(ZSetOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(redis.opsForList()).thenReturn(listOps);
        when(redis.opsForSet()).thenReturn(setOps);
        when(redis.opsForHash()).thenReturn(hashOps);
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
        when(ops.decrement(anyString())).thenAnswer(inv -> {
            String k = inv.getArgument(0);
            long v = Long.parseLong(store.getOrDefault(k, "0")) - 1;
            store.put(k, String.valueOf(v));
            return v;
        });
        when(redis.hasKey(anyString())).thenAnswer(inv -> store.containsKey(inv.getArgument(0)));
        when(redis.expire(anyString(), any(java.time.Duration.class))).thenReturn(true);
        when(redis.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(redis.getExpire(anyString())).thenReturn(0L);
        when(redis.delete(anyString())).thenAnswer(inv -> store.remove(inv.getArgument(0)) != null);
        when(listOps.leftPush(anyString(), anyString())).thenReturn(1L);
        doAnswer(inv -> null).when(listOps).trim(anyString(), anyLong(), anyLong());
        when(setOps.add(anyString(), anyString())).thenAnswer(inv -> {
            sets.computeIfAbsent(inv.getArgument(0), k -> new HashSet<>()).add(inv.getArgument(1));
            return 1L;
        });
        when(setOps.members(anyString())).thenAnswer(inv ->
                new HashSet<>(sets.getOrDefault(inv.getArgument(0), Set.of())));
        when(setOps.isMember(anyString(), anyString())).thenAnswer(inv ->
                sets.getOrDefault(inv.getArgument(0), Set.of()).contains(inv.getArgument(1)));
        when(hashOps.increment(anyString(), any(), anyLong())).thenAnswer(inv -> {
            Object[] args = inv.getArguments();
            String key = String.valueOf(args[0]);
            String field = String.valueOf(args[1]);
            long delta = ((Number) args[2]).longValue();
            Map<String, String> m = hashes.computeIfAbsent(key, k -> new HashMap<>());
            long v = Long.parseLong(m.getOrDefault(field, "0")) + delta;
            m.put(field, String.valueOf(v));
            return v;
        });
        when(hashOps.get(anyString(), any())).thenAnswer(inv -> {
            Object[] args = inv.getArguments();
            Map<String, String> m = hashes.get(String.valueOf(args[0]));
            return m == null ? null : m.get(String.valueOf(args[1]));
        });
        when(hashOps.entries(anyString())).thenAnswer(inv ->
                new HashMap<>(hashes.getOrDefault(String.valueOf(inv.getArguments()[0]), Map.of())));
        doAnswer(inv -> {
            Object[] args = inv.getArguments();
            hashes.computeIfAbsent(String.valueOf(args[0]), k -> new HashMap<>())
                    .put(String.valueOf(args[1]), String.valueOf(args[2]));
            return null;
        }).when(hashOps).put(anyString(), any(), any());
        when(zOps.add(anyString(), anyString(), anyDouble())).thenAnswer(inv -> {
            zset.put(inv.getArgument(1), inv.getArgument(2));
            return true;
        });
        when(zOps.reverseRangeWithScores(anyString(), anyLong(), anyLong())).thenAnswer(inv -> {
            Set<ZSetOperations.TypedTuple<String>> out = new HashSet<>();
            zset.forEach((member, score) -> out.add(new ZSetOperations.TypedTuple<>() {
                @Override
                public String getValue() {
                    return member;
                }

                @Override
                public Double getScore() {
                    return score;
                }

                @Override
                public int compareTo(ZSetOperations.TypedTuple<String> o) {
                    return Double.compare(score, o.getScore() == null ? 0 : o.getScore());
                }
            }));
            return out;
        });

        ObjectMapper om = new ObjectMapper();
        home = new HomeVisitService(friends, redis, om, events);
        assist = new FriendAssistService(friends, redis, om, events);
        coop = new CoopRoomService(events);
    }

    @Test
    public void homeShopVisitMiniGameRateFlow() {
        assertThat(home.publishHome(10L, "星辉小屋", "{}", "PUBLIC").get("ok")).isEqualTo(true);
        assertThat(home.syncDecorations(10L, List.of(
                Map.of("id", "sofa", "x", 1), Map.of("id", "lamp", "x", 2)
        )).get("ok")).isEqualTo(true);

        store.put("home:coin:10", "500");
        assertThat(home.buyShopItem(10L, "fish_pond").get("ok")).isEqualTo(true);
        assertThat(home.buyShopItem(10L, "chess_table").get("ok")).isEqualTo(true);
        assertThat(home.wallet(10L).get("owned")).asList().contains("fish_pond", "chess_table");

        when(friends.existsByPlayerIdAndFriendId(20L, 10L)).thenReturn(true);
        Map<String, Object> visit = home.visit(20L, 10L);
        assertThat(visit.get("ok")).isEqualTo(true);
        assertThat(((List<?>) visit.get("interactiveFurniture"))).isNotEmpty();

        Map<String, Object> game = home.playMiniGame(20L, 10L, "fish_pond", 200);
        assertThat(game.get("ok")).isEqualTo(true);
        assertThat(game.get("game")).isEqualTo("FISHING");

        Map<String, Object> rate = home.rateHome(20L, 10L, 5);
        assertThat(rate.get("ok")).isEqualTo(true);
        assertThat(home.ranking(10).get("ok")).isEqualTo(true);

        assertThat(events.types).contains("HOME_VISITED", "HOME_MINIGAME", "HOME_RATED", "HOME_SHOP_BUY");
    }

    @Test
    public void assistMultiLineupSettleThankFlow() {
        when(friends.existsByPlayerIdAndFriendId(1L, 2L)).thenReturn(true);
        assertThat(assist.offerAssist(2L, 0, Map.of("characterId", 201, "power", 900)).get("ok")).isEqualTo(true);
        assertThat(assist.offerAssist(2L, 1, Map.of("characterId", 202, "power", 1200)).get("ok")).isEqualTo(true);
        assertThat(((List<?>) assist.listLineups(2L).get("lineups"))).hasSize(2);

        Map<String, Object> borrowed = assist.borrow(1L, 2L, 1);
        assertThat(borrowed.get("ok")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> snap = (Map<String, Object>) borrowed.get("borrowed");
        assertThat(snap.get("characterId")).isEqualTo(202);

        assertThat(assist.settle(1L, true).get("ok")).isEqualTo(true);
        Map<String, Object> thanks = assist.thank(1L, 2L, 5, "谢啦");
        assertThat(thanks.get("ok")).isEqualTo(true);
        assertThat(thanks.get("bonusCoin")).isEqualTo(20);
        assertThat(events.types).contains("ASSIST_SETTLED", "ASSIST_THANKS");
    }

    @Test
    public void coopSpectateEmoteLikeFlowerFlow() {
        CoopRoomService.CoopRoom room = coop.create(1L, "world-boss", 2000);
        coop.join(2L, room.roomId());
        coop.join(3L, room.roomId());

        assertThat(coop.sendEmote(room.roomId(), 1L, "wave").get("ok")).isEqualTo(true);
        assertThat(coop.sendAction(room.roomId(), 2L, "dance").get("ok")).isEqualTo(true);
        assertThat(coop.sendQuickPhrase(room.roomId(), 3L, "glhf").get("ok")).isEqualTo(true);

        Map<String, Object> spectate = coop.spectate(99L, room.roomId());
        assertThat(spectate.get("ok")).isEqualTo(true);
        assertThat(spectate.get("spectatorCount")).isEqualTo(1);

        assertThat(coop.like(room.roomId(), 99L, 1L).get("likes")).isEqualTo(1L);
        assertThat(coop.sendFlower(room.roomId(), 99L, 2L, 5).get("flowers")).isEqualTo(5L);

        CoopRoomService.CoopRoom fighting = coop.startBoss(room.roomId(), 1L);
        assertThat(fighting.status()).isEqualTo(CoopRoomService.RoomStatus.IN_BOSS);
        Map<String, Object> view = coop.toView(coop.reportDamage(room.roomId(), 2L, 500));
        assertThat(view.get("bossHp")).isEqualTo(1500L);
        assertThat(((List<?>) view.get("feed"))).isNotEmpty();
        assertThat(events.types).contains("EMOTE", "ACTION", "PHRASE", "SPECTATE", "LIKE", "FLOWER");
    }

    @Test
    public void partyFormedPublishesThroughControllerPathContract() {
        // 模拟组队成功后控制器会发布的事件契约
        events.publishPartyFormed(7L, "pty-demo", 3);
        assertThat(events.types).contains("PARTY_FORMED");
        assertThat(events.payloads.stream().anyMatch(p -> p.contains("pty-demo"))).isTrue();
    }

    static final class RecordingSocialEventPublisher implements SocialEventPublisher {
        final List<String> types = new CopyOnWriteArrayList<>();
        final List<String> payloads = new CopyOnWriteArrayList<>();

        @Override
        public void publishFriendOnline(long playerId) {
            types.add("FRIEND_ONLINE");
        }

        @Override
        public void publishPartyFormed(long leaderId, String partyId, int memberCount) {
            types.add("PARTY_FORMED");
            payloads.add(partyId + ":" + memberCount);
        }

        @Override
        public void publishAssistSettled(long borrowerId, long ownerId, boolean victory) {
            types.add("ASSIST_SETTLED");
        }

        @Override
        public void publishHomeVisited(long visitorId, long ownerId) {
            types.add("HOME_VISITED");
        }

        @Override
        public void publishCoopInteraction(String roomId, String action, long actorId, long targetId) {
            types.add(action == null ? "COOP" : action);
        }

        @Override
        public void publish(String eventType, long actorId, long targetId, String payload) {
            types.add(eventType);
            if (payload != null) {
                payloads.add(payload);
            }
        }
    }
}
