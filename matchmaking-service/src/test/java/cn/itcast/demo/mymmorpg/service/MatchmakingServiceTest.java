/**
 * 文件说明：MatchmakingService 单元测试类。
 * 职责：使用 Mockito 模拟依赖，验证入队、自动配对、取消匹配等核心路径与异常路径，并输出中文测试日志。
 */
package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.Player;
import cn.itcast.demo.mymmorpg.port.PlayerCachePort;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.protocol.protobuf.CancelMatchCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.CancelMatchScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.EnqueueMatchCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.EnqueueMatchScRsp;
import cn.itcast.demo.mymmorpg.repository.PlayerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * MatchmakingService 单元测试：Mock 外部依赖，日志输出具体入参与断言结果。
 */
public class MatchmakingServiceTest {

    private static final Logger log = LoggerFactory.getLogger(MatchmakingServiceTest.class);

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
    private org.springframework.data.redis.core.SetOperations<String, String> setOps;
    @Mock
    private org.springframework.data.redis.core.ZSetOperations<String, String> zSetOps;

    private AutoCloseable mocks;
    private MatchmakingService matchmakingService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, String> redisStore = new ConcurrentHashMap<>();
    private final java.util.Set<String> activeQueues = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<String, Double> timeoutZset = new ConcurrentHashMap<>();

    @BeforeMethod
    @BeforeEach
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
            Object[] values = inv.getArguments();
            if (values.length > 1 && values[1] instanceof String s) {
                activeQueues.add(s);
            } else if (values.length > 1 && values[1] instanceof Object[] arr) {
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
            // execute(script, keys, args...) — Mockito may pass varargs as String or Object[]
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
        matchmakingService = new MatchmakingService(
                playerRepository, playerCachePortProvider, stringRedisTemplate, objectMapper, null, null);
        log.info("[测试前置] MatchmakingService 已初始化 | dungeonMinLevel=1 | pvpMinLevel=5 | levelBand=5 | powerBand=200 | partySize=2");
    }

    @AfterMethod
    @AfterEach
    public void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleEnqueueMatch_invalidPlayer() throws Exception {
        long playerId = 0L;
        int matchType = MatchmakingService.MATCH_TYPE_DUNGEON;
        int modeId = 1;
        log.info("[测试开始] 场景=入队未选角色 | playerId={} | matchType={} | modeId={} | 期望retcode={}",
                playerId, matchType, modeId, RetCode.PLAYER_NOT_SELECTED);

        EnqueueMatchCsReq req = EnqueueMatchCsReq.newBuilder().setMatchType(matchType).setModeId(modeId).build();
        ProtocolMessage msg = matchmakingService.handleEnqueueMatch(playerId, req);
        EnqueueMatchScRsp rsp = EnqueueMatchScRsp.parseFrom(msg.payload());

        log.info("[测试断言] 场景=入队未选角色 | msgId={} | retcode={} | queueId={} | estimatedWaitSec={}",
                msg.msgId(), rsp.getRetcode(), rsp.getQueueId(), rsp.getEstimatedWaitSec());
        assertThat(msg.msgId()).isEqualTo(MessageId.ENQUEUE_MATCH_SC_RSP);
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.PLAYER_NOT_SELECTED);
        assertThat(rsp.getQueueId()).isEmpty();
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleEnqueueMatch_invalidMatchType() throws Exception {
        long playerId = 10L;
        int matchType = 99;
        int modeId = 1;
        log.info("[测试开始] 场景=入队匹配类型非法 | playerId={} | matchType={} | modeId={} | 期望retcode={}",
                playerId, matchType, modeId, RetCode.INTERNAL_ERROR);

        when(playerCachePort.findById(playerId)).thenReturn(buildPlayer(playerId, "p10", 10, 500));
        EnqueueMatchCsReq req = EnqueueMatchCsReq.newBuilder().setMatchType(matchType).setModeId(modeId).build();
        EnqueueMatchScRsp rsp = EnqueueMatchScRsp.parseFrom(
                matchmakingService.handleEnqueueMatch(playerId, req).payload());

        log.info("[测试断言] 场景=入队匹配类型非法 | retcode={} | queueId={}", rsp.getRetcode(), rsp.getQueueId());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.INTERNAL_ERROR);
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleEnqueueMatch_playerNotFound() throws Exception {
        long playerId = 11L;
        int matchType = MatchmakingService.MATCH_TYPE_DUNGEON;
        int modeId = 2;
        log.info("[测试开始] 场景=入队玩家不存在 | playerId={} | matchType={} | modeId={} | 期望retcode={}",
                playerId, matchType, modeId, RetCode.PLAYER_NOT_FOUND);

        when(playerCachePort.findById(playerId)).thenReturn(null);
        when(playerRepository.findById(playerId)).thenReturn(Optional.empty());
        EnqueueMatchCsReq req = EnqueueMatchCsReq.newBuilder().setMatchType(matchType).setModeId(modeId).build();
        EnqueueMatchScRsp rsp = EnqueueMatchScRsp.parseFrom(
                matchmakingService.handleEnqueueMatch(playerId, req).payload());

        log.info("[测试断言] 场景=入队玩家不存在 | retcode={} | queueId={}", rsp.getRetcode(), rsp.getQueueId());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.PLAYER_NOT_FOUND);
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleEnqueueMatch_pvpLevelNotEnough() throws Exception {
        long playerId = 12L;
        int matchType = MatchmakingService.MATCH_TYPE_PVP;
        int modeId = 1;
        int level = 3;
        int power = 100;
        log.info("[测试开始] 场景=PVP等级不足 | playerId={} | matchType={} | modeId={} | level={} | power={} | pvpMinLevel=5 | 期望retcode={}",
                playerId, matchType, modeId, level, power, RetCode.MATCH_LEVEL_NOT_ENOUGH);

        when(playerCachePort.findById(playerId)).thenReturn(buildPlayer(playerId, "p12", level, power));
        EnqueueMatchCsReq req = EnqueueMatchCsReq.newBuilder().setMatchType(matchType).setModeId(modeId).build();
        EnqueueMatchScRsp rsp = EnqueueMatchScRsp.parseFrom(
                matchmakingService.handleEnqueueMatch(playerId, req).payload());

        log.info("[测试断言] 场景=PVP等级不足 | retcode={} | queueId={} | estimatedWaitSec={}",
                rsp.getRetcode(), rsp.getQueueId(), rsp.getEstimatedWaitSec());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.MATCH_LEVEL_NOT_ENOUGH);
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleEnqueueMatch_successQueuedThenAlreadyQueued() throws Exception {
        long playerId = 20L;
        int matchType = MatchmakingService.MATCH_TYPE_DUNGEON;
        int modeId = 3;
        int level = 8;
        int power = 400;
        log.info("[测试开始] 场景=副本入队成功 | playerId={} | matchType={} | modeId={} | level={} | power={} | 期望retcode={} | 期望waitSec=15",
                playerId, matchType, modeId, level, power, RetCode.OK);

        when(playerCachePort.findById(playerId)).thenReturn(buildPlayer(playerId, "p20", level, power));
        EnqueueMatchCsReq req = EnqueueMatchCsReq.newBuilder().setMatchType(matchType).setModeId(modeId).build();
        EnqueueMatchScRsp okRsp = EnqueueMatchScRsp.parseFrom(
                matchmakingService.handleEnqueueMatch(playerId, req).payload());

        log.info("[测试断言] 场景=副本入队成功 | retcode={} | queueId={} | estimatedWaitSec={} | status={}",
                okRsp.getRetcode(), okRsp.getQueueId(), okRsp.getEstimatedWaitSec(),
                matchmakingService.getMatchStatus(okRsp.getQueueId()).status);
        assertThat(okRsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(okRsp.getQueueId()).isNotBlank();
        assertThat(okRsp.getEstimatedWaitSec()).isGreaterThan(0);
        assertThat(okRsp.getEstimatedWaitSec()).isLessThanOrEqualTo(180);
        assertThat(matchmakingService.getMatchStatus(okRsp.getQueueId()).status)
                .isEqualTo(MatchmakingService.STATUS_QUEUED);

        log.info("[测试开始] 场景=重复入队 | playerId={} | matchType={} | modeId={} | 期望retcode={}",
                playerId, matchType, modeId, RetCode.MATCH_ALREADY_QUEUED);
        EnqueueMatchScRsp dupRsp = EnqueueMatchScRsp.parseFrom(
                matchmakingService.handleEnqueueMatch(playerId, req).payload());
        log.info("[测试断言] 场景=重复入队 | retcode={} | queueId={}", dupRsp.getRetcode(), dupRsp.getQueueId());
        assertThat(dupRsp.getRetcode()).isEqualTo(RetCode.MATCH_ALREADY_QUEUED);
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleEnqueueMatch_twoPlayersMatchedWithinBand() throws Exception {
        long playerA = 30L;
        long playerB = 31L;
        int matchType = MatchmakingService.MATCH_TYPE_DUNGEON;
        int modeId = 7;
        int levelA = 10;
        int powerA = 500;
        int levelB = 12;
        int powerB = 620;
        log.info("[测试开始] 场景=双人带宽内配对成功 | playerA={} | levelA={} | powerA={} | playerB={} | levelB={} | powerB={} | matchType={} | modeId={} | 期望status={}",
                playerA, levelA, powerA, playerB, levelB, powerB, matchType, modeId, MatchmakingService.STATUS_MATCHED);

        when(playerCachePort.findById(playerA)).thenReturn(buildPlayer(playerA, "pa", levelA, powerA));
        when(playerCachePort.findById(playerB)).thenReturn(buildPlayer(playerB, "pb", levelB, powerB));
        EnqueueMatchCsReq req = EnqueueMatchCsReq.newBuilder().setMatchType(matchType).setModeId(modeId).build();

        EnqueueMatchScRsp rspA = EnqueueMatchScRsp.parseFrom(
                matchmakingService.handleEnqueueMatch(playerA, req).payload());
        EnqueueMatchScRsp rspB = EnqueueMatchScRsp.parseFrom(
                matchmakingService.handleEnqueueMatch(playerB, req).payload());

        MatchmakingService.MatchStatus statusA = matchmakingService.getMatchStatus(rspA.getQueueId());
        MatchmakingService.MatchStatus statusB = matchmakingService.getMatchStatus(rspB.getQueueId());
        int expectedSceneId = 9000 + modeId;
        log.info("[测试断言] 场景=双人带宽内配对成功 | queueIdA={} | statusA={} | queueIdB={} | statusB={} | sceneId={} | lineId={} | teammates={}",
                rspA.getQueueId(), statusA.status, rspB.getQueueId(), statusB.status,
                statusA.sceneId, statusA.lineId, statusA.teammateIds);

        assertThat(rspA.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(rspB.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(statusA.status).isEqualTo(MatchmakingService.STATUS_MATCHED);
        assertThat(statusB.status).isEqualTo(MatchmakingService.STATUS_MATCHED);
        assertThat(statusA.sceneId).isEqualTo(expectedSceneId);
        assertThat(statusA.lineId).isEqualTo(1);
        assertThat(statusA.teammateIds).containsExactly(playerA, playerB);
        assertThat(statusB.teammateIds).containsExactly(playerA, playerB);
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleEnqueueMatch_crossDungeonFourPlayersMatched() throws Exception {
        long p1 = 61L;
        long p2 = 62L;
        long p3 = 63L;
        long p4 = 64L;
        int matchType = MatchmakingService.MATCH_TYPE_CROSS_DUNGEON;
        int modeId = 5;
        when(playerCachePort.findById(p1)).thenReturn(buildPlayer(p1, "c1", 12, 800));
        when(playerCachePort.findById(p2)).thenReturn(buildPlayer(p2, "c2", 13, 820));
        when(playerCachePort.findById(p3)).thenReturn(buildPlayer(p3, "c3", 11, 790));
        when(playerCachePort.findById(p4)).thenReturn(buildPlayer(p4, "c4", 14, 850));
        EnqueueMatchCsReq req = EnqueueMatchCsReq.newBuilder().setMatchType(matchType).setModeId(modeId).build();

        EnqueueMatchScRsp r1 = EnqueueMatchScRsp.parseFrom(
                matchmakingService.handleEnqueueMatch(p1, req).payload());
        EnqueueMatchScRsp r2 = EnqueueMatchScRsp.parseFrom(
                matchmakingService.handleEnqueueMatch(p2, req).payload());
        EnqueueMatchScRsp r3 = EnqueueMatchScRsp.parseFrom(
                matchmakingService.handleEnqueueMatch(p3, req).payload());
        assertThat(matchmakingService.getMatchStatus(r1.getQueueId()).status)
                .isEqualTo(MatchmakingService.STATUS_QUEUED);

        EnqueueMatchScRsp r4 = EnqueueMatchScRsp.parseFrom(
                matchmakingService.handleEnqueueMatch(p4, req).payload());

        MatchmakingService.MatchStatus s1 = matchmakingService.getMatchStatus(r1.getQueueId());
        MatchmakingService.MatchStatus s4 = matchmakingService.getMatchStatus(r4.getQueueId());
        log.info("[测试断言] 场景=跨服四人副本配对 | status1={} | sceneId={} | crossServer={} | teammates={}",
                s1.status, s1.sceneId, s1.crossServer, s1.teammateIds);

        assertThat(r1.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(r2.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(r3.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(r4.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(s1.status).isEqualTo(MatchmakingService.STATUS_MATCHED);
        assertThat(s4.status).isEqualTo(MatchmakingService.STATUS_MATCHED);
        assertThat(s1.sceneId).isEqualTo(9100 + modeId);
        assertThat(s1.crossServer).isTrue();
        assertThat(s1.teammateIds).containsExactlyInAnyOrder(p1, p2, p3, p4);
        assertThat(s4.teammateIds).containsExactlyInAnyOrder(p1, p2, p3, p4);
        // 跨服秒切：后台预创建 READY + 全员独立票据
        assertThat(s1.preloadReady).isTrue();
        assertThat(s4.preloadReady).isTrue();
        assertThat(s1.prepId).isNotBlank().startsWith("prep-");
        assertThat(s1.instanceId).isNotBlank();
        assertThat(s1.host).isNotBlank();
        assertThat(s1.port).isGreaterThan(0);
        assertThat(s1.sessionTicket).isNotBlank();
        assertThat(s4.sessionTicket).isNotBlank();
        assertThat(s1.sessionTicket).isNotEqualTo(s4.sessionTicket);
        assertThat(s1.prepId).isEqualTo(s4.prepId);
        log.info("[测试断言] 场景=跨服秒切预创建 | prepId={} | instanceId={} | host={}:{} | preloadReady={}",
                s1.prepId, s1.instanceId, s1.host, s1.port, s1.preloadReady);
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleEnqueueMatch_crossDungeonLevelNotEnough() throws Exception {
        long playerId = 70L;
        when(playerCachePort.findById(playerId)).thenReturn(buildPlayer(playerId, "low", 8, 400));
        EnqueueMatchCsReq req = EnqueueMatchCsReq.newBuilder()
                .setMatchType(MatchmakingService.MATCH_TYPE_CROSS_DUNGEON).setModeId(1).build();
        EnqueueMatchScRsp rsp = EnqueueMatchScRsp.parseFrom(
                matchmakingService.handleEnqueueMatch(playerId, req).payload());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.MATCH_LEVEL_NOT_ENOUGH);
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleEnqueueMatch_twoPlayersNotMatchedOutOfBand() throws Exception {
        long playerA = 40L;
        long playerB = 41L;
        int matchType = MatchmakingService.MATCH_TYPE_PVP;
        int modeId = 2;
        int levelA = 10;
        int powerA = 500;
        int levelB = 20;
        int powerB = 900;
        log.info("[测试开始] 场景=双人带宽外不配对 | playerA={} | levelA={} | powerA={} | playerB={} | levelB={} | powerB={} | levelDiff={} | powerDiff={} | 期望status={}",
                playerA, levelA, powerA, playerB, levelB, powerB,
                Math.abs(levelA - levelB), Math.abs(powerA - powerB), MatchmakingService.STATUS_QUEUED);

        when(playerCachePort.findById(playerA)).thenReturn(buildPlayer(playerA, "pa", levelA, powerA));
        when(playerCachePort.findById(playerB)).thenReturn(buildPlayer(playerB, "pb", levelB, powerB));
        EnqueueMatchCsReq req = EnqueueMatchCsReq.newBuilder().setMatchType(matchType).setModeId(modeId).build();

        EnqueueMatchScRsp rspA = EnqueueMatchScRsp.parseFrom(
                matchmakingService.handleEnqueueMatch(playerA, req).payload());
        EnqueueMatchScRsp rspB = EnqueueMatchScRsp.parseFrom(
                matchmakingService.handleEnqueueMatch(playerB, req).payload());

        MatchmakingService.MatchStatus statusA = matchmakingService.getMatchStatus(rspA.getQueueId());
        MatchmakingService.MatchStatus statusB = matchmakingService.getMatchStatus(rspB.getQueueId());
        log.info("[测试断言] 场景=双人带宽外不配对 | queueIdA={} | statusA={} | queueIdB={} | statusB={}",
                rspA.getQueueId(), statusA.status, rspB.getQueueId(), statusB.status);

        assertThat(statusA.status).isEqualTo(MatchmakingService.STATUS_QUEUED);
        assertThat(statusB.status).isEqualTo(MatchmakingService.STATUS_QUEUED);
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleEnqueueMatch_crossShardMatched() throws Exception {
        matchmakingService.setQueueShards(2);
        matchmakingService.setCrossShardEnabled(true);
        // 30 % 2 == 0, 31 % 2 == 1 → 不同分片
        long playerA = 30L;
        long playerB = 31L;
        when(playerCachePort.findById(playerA)).thenReturn(buildPlayer(playerA, "pa", 10, 500));
        when(playerCachePort.findById(playerB)).thenReturn(buildPlayer(playerB, "pb", 11, 520));
        EnqueueMatchCsReq req = EnqueueMatchCsReq.newBuilder()
                .setMatchType(MatchmakingService.MATCH_TYPE_DUNGEON).setModeId(3).build();

        EnqueueMatchScRsp rspA = EnqueueMatchScRsp.parseFrom(
                matchmakingService.handleEnqueueMatch(playerA, req).payload());
        EnqueueMatchScRsp rspB = EnqueueMatchScRsp.parseFrom(
                matchmakingService.handleEnqueueMatch(playerB, req).payload());

        assertThat(rspA.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(rspB.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(matchmakingService.getMatchStatus(rspA.getQueueId()).status)
                .isEqualTo(MatchmakingService.STATUS_MATCHED);
        assertThat(matchmakingService.getMatchStatus(rspB.getQueueId()).status)
                .isEqualTo(MatchmakingService.STATUS_MATCHED);
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleCancelMatch_invalidPlayer() throws Exception {
        long playerId = -1L;
        String queueId = "q-demo";
        log.info("[测试开始] 场景=取消匹配未选角色 | playerId={} | queueId={} | 期望retcode={}",
                playerId, queueId, RetCode.PLAYER_NOT_SELECTED);

        CancelMatchCsReq req = CancelMatchCsReq.newBuilder().setQueueId(queueId).build();
        ProtocolMessage msg = matchmakingService.handleCancelMatch(playerId, req);
        CancelMatchScRsp rsp = CancelMatchScRsp.parseFrom(msg.payload());

        log.info("[测试断言] 场景=取消匹配未选角色 | msgId={} | retcode={}", msg.msgId(), rsp.getRetcode());
        assertThat(msg.msgId()).isEqualTo(MessageId.CANCEL_MATCH_SC_RSP);
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.PLAYER_NOT_SELECTED);
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleCancelMatch_notInQueue() throws Exception {
        long playerId = 50L;
        String queueId = "not-exist";
        log.info("[测试开始] 场景=取消匹配不在队列 | playerId={} | queueId={} | 期望retcode={}",
                playerId, queueId, RetCode.MATCH_NOT_IN_QUEUE);

        CancelMatchCsReq req = CancelMatchCsReq.newBuilder().setQueueId(queueId).build();
        CancelMatchScRsp rsp = CancelMatchScRsp.parseFrom(
                matchmakingService.handleCancelMatch(playerId, req).payload());

        log.info("[测试断言] 场景=取消匹配不在队列 | retcode={}", rsp.getRetcode());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.MATCH_NOT_IN_QUEUE);
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleCancelMatch_wrongQueueId() throws Exception {
        long playerId = 60L;
        int matchType = MatchmakingService.MATCH_TYPE_DUNGEON;
        int modeId = 5;
        log.info("[测试开始] 场景=取消匹配queueId不匹配 | playerId={} | matchType={} | modeId={} | 期望retcode={}",
                playerId, matchType, modeId, RetCode.MATCH_NOT_IN_QUEUE);

        when(playerCachePort.findById(playerId)).thenReturn(buildPlayer(playerId, "p60", 9, 300));
        EnqueueMatchCsReq enqueueReq = EnqueueMatchCsReq.newBuilder().setMatchType(matchType).setModeId(modeId).build();
        EnqueueMatchScRsp enqueueRsp = EnqueueMatchScRsp.parseFrom(
                matchmakingService.handleEnqueueMatch(playerId, enqueueReq).payload());
        String realQueueId = enqueueRsp.getQueueId();
        String wrongQueueId = "wrong-" + realQueueId;
        log.info("[测试准备] 场景=取消匹配queueId不匹配 | playerId={} | realQueueId={} | wrongQueueId={}",
                playerId, realQueueId, wrongQueueId);

        CancelMatchCsReq cancelReq = CancelMatchCsReq.newBuilder().setQueueId(wrongQueueId).build();
        CancelMatchScRsp cancelRsp = CancelMatchScRsp.parseFrom(
                matchmakingService.handleCancelMatch(playerId, cancelReq).payload());

        log.info("[测试断言] 场景=取消匹配queueId不匹配 | retcode={} | status仍为={}",
                cancelRsp.getRetcode(), matchmakingService.getMatchStatus(realQueueId).status);
        assertThat(cancelRsp.getRetcode()).isEqualTo(RetCode.MATCH_NOT_IN_QUEUE);
        assertThat(matchmakingService.getMatchStatus(realQueueId).status)
                .isEqualTo(MatchmakingService.STATUS_QUEUED);
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleCancelMatch_success() throws Exception {
        long playerId = 70L;
        int matchType = MatchmakingService.MATCH_TYPE_DUNGEON;
        int modeId = 8;
        log.info("[测试开始] 场景=取消匹配成功 | playerId={} | matchType={} | modeId={} | 期望retcode={}",
                playerId, matchType, modeId, RetCode.OK);

        when(playerCachePort.findById(playerId)).thenReturn(buildPlayer(playerId, "p70", 15, 800));
        EnqueueMatchCsReq enqueueReq = EnqueueMatchCsReq.newBuilder().setMatchType(matchType).setModeId(modeId).build();
        EnqueueMatchScRsp enqueueRsp = EnqueueMatchScRsp.parseFrom(
                matchmakingService.handleEnqueueMatch(playerId, enqueueReq).payload());
        String queueId = enqueueRsp.getQueueId();
        log.info("[测试准备] 场景=取消匹配成功 | playerId={} | queueId={} | enqueueRetcode={}",
                playerId, queueId, enqueueRsp.getRetcode());

        CancelMatchCsReq cancelReq = CancelMatchCsReq.newBuilder().setQueueId(queueId).build();
        CancelMatchScRsp cancelRsp = CancelMatchScRsp.parseFrom(
                matchmakingService.handleCancelMatch(playerId, cancelReq).payload());

        log.info("[测试断言] 场景=取消匹配成功 | retcode={} | status={} | 可再次入队验证retcode={}",
                cancelRsp.getRetcode(),
                matchmakingService.getMatchStatus(queueId).status,
                RetCode.OK);
        assertThat(cancelRsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(matchmakingService.getMatchStatus(queueId).status)
                .isEqualTo(MatchmakingService.STATUS_CANCELLED);

        EnqueueMatchScRsp reEnqueue = EnqueueMatchScRsp.parseFrom(
                matchmakingService.handleEnqueueMatch(playerId, enqueueReq).payload());
        log.info("[测试断言] 场景=取消后再次入队 | retcode={} | newQueueId={}",
                reEnqueue.getRetcode(), reEnqueue.getQueueId());
        assertThat(reEnqueue.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(reEnqueue.getQueueId()).isNotEqualTo(queueId);
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleEnqueueMatch_estimatedWaitDropsWhenPeerJoins() throws Exception {
        long solo = 201L;
        long peer = 202L;
        int matchType = MatchmakingService.MATCH_TYPE_DUNGEON;
        int modeId = 33;
        when(playerCachePort.findById(solo)).thenReturn(buildPlayer(solo, "solo", 10, 500));
        when(playerCachePort.findById(peer)).thenReturn(buildPlayer(peer, "peer", 11, 520));

        EnqueueMatchScRsp alone = EnqueueMatchScRsp.parseFrom(
                matchmakingService.handleEnqueueMatch(solo, EnqueueMatchCsReq.newBuilder()
                        .setMatchType(matchType).setModeId(modeId).build()).payload());
        assertThat(alone.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(alone.getEstimatedWaitSec()).isEqualTo(20);

        // 取消后再以双人互补画像入队：第二人入队前等待应更低或立刻匹配
        matchmakingService.handleCancelMatch(solo, CancelMatchCsReq.newBuilder()
                .setQueueId(alone.getQueueId()).build());

        EnqueueMatchScRsp a = EnqueueMatchScRsp.parseFrom(
                matchmakingService.handleEnqueueMatch(solo, EnqueueMatchCsReq.newBuilder()
                        .setMatchType(matchType).setModeId(modeId)
                        .setPreferredRole("tank").setWinRate(0.55).build()).payload());
        EnqueueMatchScRsp b = EnqueueMatchScRsp.parseFrom(
                matchmakingService.handleEnqueueMatch(peer, EnqueueMatchCsReq.newBuilder()
                        .setMatchType(matchType).setModeId(modeId)
                        .setPreferredRole("heal").setWinRate(0.52).build()).payload());

        assertThat(a.getEstimatedWaitSec()).isEqualTo(20);
        assertThat(b.getEstimatedWaitSec()).isLessThanOrEqualTo(8);
        assertThat(matchmakingService.getMatchStatus(a.getQueueId()).status)
                .isEqualTo(MatchmakingService.STATUS_MATCHED);
        assertThat(matchmakingService.getMatchStatus(b.getQueueId()).status)
                .isEqualTo(MatchmakingService.STATUS_MATCHED);
    }

    @Test
    @org.junit.jupiter.api.Test
    public void handleEnqueueMatch_roleComplementTankHealMatched() throws Exception {
        long tank = 301L;
        long heal = 302L;
        int matchType = MatchmakingService.MATCH_TYPE_DUNGEON;
        int modeId = 44;
        when(playerCachePort.findById(tank)).thenReturn(buildPlayer(tank, "tank", 15, 700));
        when(playerCachePort.findById(heal)).thenReturn(buildPlayer(heal, "heal", 15, 710));

        EnqueueMatchScRsp rTank = EnqueueMatchScRsp.parseFrom(
                matchmakingService.handleEnqueueMatch(tank, EnqueueMatchCsReq.newBuilder()
                        .setMatchType(matchType).setModeId(modeId)
                        .setPreferredRole("tank").setWinRate(0.55)
                        .setStylePreference("defensive").build()).payload());
        assertThat(matchmakingService.getMatchStatus(rTank.getQueueId()).status)
                .isEqualTo(MatchmakingService.STATUS_QUEUED);

        EnqueueMatchScRsp rHeal = EnqueueMatchScRsp.parseFrom(
                matchmakingService.handleEnqueueMatch(heal, EnqueueMatchCsReq.newBuilder()
                        .setMatchType(matchType).setModeId(modeId)
                        .setPreferredRole("heal").setWinRate(0.52)
                        .setStylePreference("defensive").build()).payload());

        MatchmakingService.MatchStatus st = matchmakingService.getMatchStatus(rTank.getQueueId());
        MatchmakingService.MatchStatus sh = matchmakingService.getMatchStatus(rHeal.getQueueId());
        assertThat(rHeal.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(st.status).isEqualTo(MatchmakingService.STATUS_MATCHED);
        assertThat(sh.status).isEqualTo(MatchmakingService.STATUS_MATCHED);
        assertThat(st.teammateIds).containsExactlyInAnyOrder(tank, heal);
    }

    private static Player buildPlayer(long id, String name, int level, int power) {
        Player player = new Player();
        player.setId(id);
        player.setName(name);
        player.setLevel(level);
        player.setPowerScore(power);
        return player;
    }
}
