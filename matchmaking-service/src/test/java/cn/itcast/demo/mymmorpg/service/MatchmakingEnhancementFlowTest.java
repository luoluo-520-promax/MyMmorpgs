package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.Player;
import cn.itcast.demo.mymmorpg.port.PlayerCachePort;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.protocol.protobuf.EnqueueMatchCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.EnqueueMatchScRsp;
import cn.itcast.demo.mymmorpg.repository.PlayerRepository;
import cn.itcast.demo.mymmorpg.world.scene.SceneBackgroundPrecreator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 匹配增强流程：心跳幽灵清理、等待超时、战力分段路由、两阶段预留失败回队。
 */
public class MatchmakingEnhancementFlowTest {

    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private ObjectProvider<PlayerCachePort> playerCachePortProvider;
    @Mock
    private PlayerCachePort playerCachePort;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private SetOperations<String, String> setOps;
    @Mock
    private ZSetOperations<String, String> zSetOps;
    @Mock
    private ObjectProvider<SceneBackgroundPrecreator> scenePrepProvider;

    private AutoCloseable mocks;
    private MatchmakingService matchmakingService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, String> redisStore = new ConcurrentHashMap<>();
    private final java.util.Set<String> activeQueues = ConcurrentHashMap.newKeySet();
    private final Map<String, Double> timeoutZset = new ConcurrentHashMap<>();

    @BeforeMethod
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        redisStore.clear();
        activeQueues.clear();
        timeoutZset.clear();
        when(playerCachePortProvider.getIfAvailable()).thenReturn(playerCachePort);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(stringRedisTemplate.opsForSet()).thenReturn(setOps);
        lenient().when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOps);
        lenient().when(valueOps.get(anyString())).thenAnswer(inv -> redisStore.get(inv.getArgument(0)));
        lenient().doAnswer(inv -> {
            redisStore.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(valueOps).set(anyString(), anyString());
        lenient().doAnswer(inv -> {
            redisStore.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(valueOps).set(anyString(), anyString(), any(Duration.class));
        lenient().when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenAnswer(inv -> {
            String k = inv.getArgument(0);
            if (redisStore.containsKey(k)) {
                return Boolean.FALSE;
            }
            redisStore.put(k, inv.getArgument(1));
            return Boolean.TRUE;
        });
        lenient().when(stringRedisTemplate.delete(anyString())).thenAnswer(inv -> {
            redisStore.remove(inv.getArgument(0));
            return Boolean.TRUE;
        });
        lenient().when(setOps.add(anyString(), any())).thenAnswer(inv -> {
            Object arg1 = inv.getArguments()[1];
            if (arg1 instanceof String s) {
                activeQueues.add(s);
            } else if (arg1 instanceof Object[] arr) {
                for (Object o : arr) {
                    activeQueues.add(String.valueOf(o));
                }
            }
            return 1L;
        });
        lenient().when(setOps.members(anyString())).thenAnswer(inv -> new java.util.HashSet<>(activeQueues));
        lenient().when(zSetOps.add(anyString(), anyString(), any(Double.class))).thenAnswer(inv -> {
            timeoutZset.put(inv.getArgument(1), inv.getArgument(2));
            return Boolean.TRUE;
        });
        lenient().when(zSetOps.range(anyString(), any(Long.class), any(Long.class)))
                .thenAnswer(inv -> new java.util.HashSet<>(timeoutZset.keySet()));
        lenient().when(zSetOps.rangeByScore(anyString(), any(Double.class), any(Double.class)))
                .thenAnswer(inv -> {
                    double max = inv.getArgument(2);
                    java.util.Set<String> due = new java.util.HashSet<>();
                    timeoutZset.forEach((k, v) -> {
                        if (v <= max) {
                            due.add(k);
                        }
                    });
                    return due;
                });
        lenient().when(zSetOps.remove(anyString(), any())).thenAnswer(inv -> {
            Object member = inv.getArgument(1);
            if (member instanceof Object[] arr) {
                for (Object o : arr) {
                    timeoutZset.remove(String.valueOf(o));
                }
            } else {
                timeoutZset.remove(String.valueOf(member));
            }
            return 1L;
        });
        lenient().when(stringRedisTemplate.execute(any(), any(), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            java.util.List<String> keys = (java.util.List<String>) inv.getArgument(1);
            String token = null;
            if (inv.getArguments().length > 2) {
                Object arg2 = inv.getArguments()[2];
                if (arg2 instanceof Object[] arr && arr.length > 0) {
                    token = String.valueOf(arr[0]);
                } else if (arg2 != null) {
                    token = String.valueOf(arg2);
                }
            }
            if (keys != null && !keys.isEmpty() && token != null) {
                String k = keys.get(0);
                if (token.equals(redisStore.get(k))) {
                    redisStore.remove(k);
                    return 1L;
                }
            }
            return 0L;
        });
        lenient().when(scenePrepProvider.getIfAvailable()).thenReturn(new SceneBackgroundPrecreator());
        matchmakingService = new MatchmakingService(
                playerRepository, playerCachePortProvider, stringRedisTemplate, objectMapper,
                scenePrepProvider, null);
        matchmakingService.setRouteByPowerBand(true);
        matchmakingService.setHeartbeatTtlSec(30);
        matchmakingService.setQueueTimeoutSec(300);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    public void heartbeat_refreshKeepsPlayerAlive_andGhostPurgeRemovesStale() throws Exception {
        long playerId = 501L;
        when(playerCachePort.findById(playerId)).thenReturn(player(playerId, 10, 500));

        EnqueueMatchScRsp rsp = EnqueueMatchScRsp.parseFrom(
                matchmakingService.handleEnqueueMatch(playerId, dungeonReq(9)).payload());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(matchmakingService.touchHeartbeat(playerId)).isTrue();
        assertThat(matchmakingService.purgeGhostPlayers()).isEqualTo(0);
        assertThat(matchmakingService.getMatchStatus(rsp.getQueueId()).status)
                .isEqualTo(MatchmakingService.STATUS_QUEUED);

        // 模拟心跳过期
        redisStore.keySet().removeIf(k -> k.startsWith("match:heartbeat:"));
        int purged = matchmakingService.purgeGhostPlayers();
        assertThat(purged).isEqualTo(1);
        assertThat(matchmakingService.getMatchStatus(rsp.getQueueId()).status)
                .isEqualTo(MatchmakingService.STATUS_TIMEOUT);
        assertThat(matchmakingService.touchHeartbeat(playerId)).isFalse();
    }

    @Test
    public void queueTimeout_popsWaitingPlayerAndMarksTimeout() throws Exception {
        long playerId = 502L;
        when(playerCachePort.findById(playerId)).thenReturn(player(playerId, 10, 500));
        matchmakingService.setQueueTimeoutSec(60);

        EnqueueMatchScRsp rsp = EnqueueMatchScRsp.parseFrom(
                matchmakingService.handleEnqueueMatch(playerId, dungeonReq(10)).payload());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.OK);

        // 将超时分数拨到过去，触发清道夫
        String member = playerId + ":" + rsp.getQueueId();
        timeoutZset.put(member, (double) (System.currentTimeMillis() - 1_000L));
        int timedOut = matchmakingService.processQueueTimeouts();
        assertThat(timedOut).isEqualTo(1);
        assertThat(matchmakingService.getMatchStatus(rsp.getQueueId()).status)
                .isEqualTo(MatchmakingService.STATUS_TIMEOUT);
    }

    @Test
    public void powerBandRouting_sameBandPlayersCanMatch() throws Exception {
        long a = 510L;
        long b = 511L;
        when(playerCachePort.findById(a)).thenReturn(player(a, 12, 800));
        when(playerCachePort.findById(b)).thenReturn(player(b, 13, 850));

        EnqueueMatchScRsp ra = EnqueueMatchScRsp.parseFrom(
                matchmakingService.handleEnqueueMatch(a, dungeonReq(11)).payload());
        EnqueueMatchScRsp rb = EnqueueMatchScRsp.parseFrom(
                matchmakingService.handleEnqueueMatch(b, dungeonReq(11)).payload());

        assertThat(ra.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(rb.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(matchmakingService.getMatchStatus(ra.getQueueId()).status)
                .isEqualTo(MatchmakingService.STATUS_MATCHED);
        assertThat(matchmakingService.getMatchStatus(rb.getQueueId()).status)
                .isEqualTo(MatchmakingService.STATUS_MATCHED);
        assertThat(activeQueues.stream().anyMatch(k -> k.contains(":pb0"))).isTrue();
    }

    @Test
    public void powerBandRouting_farBandsDoNotImmediateMatch() throws Exception {
        long low = 520L;
        long high = 521L;
        when(playerCachePort.findById(low)).thenReturn(player(low, 12, 200));
        when(playerCachePort.findById(high)).thenReturn(player(high, 12, 2500));

        EnqueueMatchScRsp rLow = EnqueueMatchScRsp.parseFrom(
                matchmakingService.handleEnqueueMatch(low, dungeonReq(12)).payload());
        EnqueueMatchScRsp rHigh = EnqueueMatchScRsp.parseFrom(
                matchmakingService.handleEnqueueMatch(high, dungeonReq(12)).payload());

        assertThat(matchmakingService.getMatchStatus(rLow.getQueueId()).status)
                .isEqualTo(MatchmakingService.STATUS_QUEUED);
        assertThat(matchmakingService.getMatchStatus(rHigh.getQueueId()).status)
                .isEqualTo(MatchmakingService.STATUS_QUEUED);
        assertThat(activeQueues.stream().anyMatch(k -> k.contains(":pb0"))).isTrue();
        assertThat(activeQueues.stream().anyMatch(k -> k.contains(":pb2"))).isTrue();
    }

    @Test
    public void twoPhaseReserveFail_requeuesPartyWithPriorityBoost() throws Exception {
        SceneBackgroundPrecreator failing = mock(SceneBackgroundPrecreator.class);
        long now = System.currentTimeMillis();
        when(failing.prepareCrossDungeon(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                any(),
                org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(new SceneBackgroundPrecreator.PrepHandle(
                        "prep-fail", "", "", 9101, 1, "", "", 0,
                        List.of(601L, 602L, 603L, 604L), Map.of(),
                        SceneBackgroundPrecreator.PrepStatus.FAILED, now, 0L, now + 30_000L));
        when(scenePrepProvider.getIfAvailable()).thenReturn(failing);
        matchmakingService = new MatchmakingService(
                playerRepository, playerCachePortProvider, stringRedisTemplate, objectMapper,
                scenePrepProvider, null);
        matchmakingService.setRouteByPowerBand(true);

        long[] ids = {601L, 602L, 603L, 604L};
        for (long id : ids) {
            when(playerCachePort.findById(id)).thenReturn(player(id, 20, 1000));
        }
        EnqueueMatchCsReq req = EnqueueMatchCsReq.newBuilder()
                .setMatchType(MatchmakingService.MATCH_TYPE_CROSS_DUNGEON)
                .setModeId(1)
                .build();
        String[] queueIds = new String[4];
        for (int i = 0; i < ids.length; i++) {
            EnqueueMatchScRsp rsp = EnqueueMatchScRsp.parseFrom(
                    matchmakingService.handleEnqueueMatch(ids[i], req).payload());
            assertThat(rsp.getRetcode()).isEqualTo(RetCode.OK);
            queueIds[i] = rsp.getQueueId();
        }
        // 预留失败：应仍在排队，而非 MATCHED
        for (String qid : queueIds) {
            assertThat(matchmakingService.getMatchStatus(qid).status)
                    .isEqualTo(MatchmakingService.STATUS_QUEUED);
        }
        assertThat(matchmakingService.touchHeartbeat(601L)).isTrue();
    }

    private static EnqueueMatchCsReq dungeonReq(int modeId) {
        return EnqueueMatchCsReq.newBuilder()
                .setMatchType(MatchmakingService.MATCH_TYPE_DUNGEON)
                .setModeId(modeId)
                .build();
    }

    private static Player player(long id, int level, int power) {
        Player p = new Player();
        p.setId(id);
        p.setName("p" + id);
        p.setLevel(level);
        p.setPowerScore(power);
        return p;
    }
}
