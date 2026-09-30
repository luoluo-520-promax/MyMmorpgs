package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.anticheat.AntiCheatService;
import cn.itcast.demo.mymmorpg.challenge.ChallengeConfigLoader;
import cn.itcast.demo.mymmorpg.element.ElementReactionEngine;
import cn.itcast.demo.mymmorpg.model.BattleSceneFactory;
import cn.itcast.demo.mymmorpg.port.BattleScenePort;
import cn.itcast.demo.mymmorpg.port.PlayerNotificationPort;
import cn.itcast.demo.mymmorpg.port.PlayerProgressPort;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleActionCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleActionScRsp;
import cn.itcast.demo.mymmorpg.repository.PlayerRepository;
import cn.itcast.demo.mymmorpg.support.BattlePolicy;
import cn.itcast.demo.mymmorpg.tlog.TLogEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 战斗反作弊业务流程：封禁拦截 → 行为异常拒绝 → 伤害溢出拒绝 → 正常攻击放行。
 */
public class BattleAntiCheatFlowTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, String> redisKv = new HashMap<>();
    private BattleService battleService;
    private AntiCheatService antiCheat;
    private BattlePolicy battlePolicy;

    @BeforeMethod
    @SuppressWarnings("unchecked")
    public void setUp() {
        redisKv.clear();
        antiCheat = mock(AntiCheatService.class);
        when(antiCheat.isBanned(anyLong())).thenReturn(false);
        when(antiCheat.checkBehaviorAnomaly(anyLong(), anyString(), anyDouble(), anyLong()))
                .thenReturn(AntiCheatService.CheckResult.ok());
        when(antiCheat.checkDamage(anyLong(), anyInt(), anyInt()))
                .thenReturn(AntiCheatService.CheckResult.ok());

        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.opsForSet()).thenReturn(setOps);
        when(valueOps.get(anyString())).thenAnswer(inv -> redisKv.get(inv.getArgument(0)));
        lenient().doAnswer(inv -> {
            redisKv.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(valueOps).set(anyString(), anyString(), any(java.time.Duration.class));
        lenient().doAnswer(inv -> {
            redisKv.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(valueOps).set(anyString(), anyString(), any(long.class), any(TimeUnit.class));

        battlePolicy = mock(BattlePolicy.class);
        ObjectProvider<RogueService> rogue = mock(ObjectProvider.class);
        when(rogue.getIfAvailable()).thenReturn(null);
        ObjectProvider<BattleEndProjectionNotifier> projection = mock(ObjectProvider.class);
        when(projection.getIfAvailable()).thenReturn(null);
        ObjectProvider<TLogEventPublisher> tlog = mock(ObjectProvider.class);
        when(tlog.getIfAvailable()).thenReturn(null);
        ObjectProvider<AntiCheatService> antiCheatProvider = mock(ObjectProvider.class);
        when(antiCheatProvider.getIfAvailable()).thenReturn(antiCheat);

        battleService = new BattleService(
                mock(ConfigQueryService.class),
                mock(PlayerRepository.class),
                mock(BattleScenePort.class),
                redis,
                objectMapper,
                battlePolicy,
                mock(BattleEventPublisher.class),
                mock(PlayerNotificationPort.class),
                mock(PlayerProgressPort.class),
                mock(BattleSceneFactory.class),
                new BattleStatsCollector(),
                mock(ChallengeConfigLoader.class),
                rogue,
                projection,
                new ElementReactionEngine(),
                tlog,
                antiCheatProvider);
    }

    @Test
    public void bannedPlayer_actionRejected() throws Exception {
        long playerId = 501L;
        long battleId = 50101L;
        seedState(battleId, playerId, 9001L);
        when(antiCheat.isBanned(playerId)).thenReturn(true);

        BattleActionScRsp rsp = act(playerId, battleId, 9001L, 1);
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.BATTLE_CHEAT_REJECTED);
        verify(antiCheat).isBanned(playerId);
    }

    @Test
    public void behaviorAnomaly_actionRejected() throws Exception {
        long playerId = 502L;
        long battleId = 50201L;
        seedState(battleId, playerId, 9002L);
        when(antiCheat.checkBehaviorAnomaly(eq(playerId), eq("click"), eq(1.0), anyLong()))
                .thenReturn(new AntiCheatService.CheckResult(
                        AntiCheatService.Verdict.STRIKE, "behavior_anomaly:apm", 1));

        BattleActionScRsp rsp = act(playerId, battleId, 9002L, 1);
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.BATTLE_CHEAT_REJECTED);
    }

    @Test
    public void damageOverflow_actionRejected() throws Exception {
        long playerId = 503L;
        long battleId = 50301L;
        long enemyId = 9003L;
        seedState(battleId, playerId, enemyId);
        when(battlePolicy.computeDamage(80, 10, 1, 0)).thenReturn(70);
        when(antiCheat.checkDamage(eq(playerId), eq(70), anyInt()))
                .thenReturn(new AntiCheatService.CheckResult(
                        AntiCheatService.Verdict.STRIKE, "damage_overflow", 2));

        BattleActionScRsp rsp = act(playerId, battleId, enemyId, 1);
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.BATTLE_CHEAT_REJECTED);
        verify(antiCheat).checkDamage(eq(playerId), eq(70), anyInt());
    }

    @Test
    public void normalAttack_passesAntiCheatAndDealsDamage() throws Exception {
        long playerId = 504L;
        long battleId = 50401L;
        long enemyId = 9004L;
        seedState(battleId, playerId, enemyId);
        when(battlePolicy.computeDamage(80, 10, 1, 0)).thenReturn(70);
        when(battlePolicy.computeDamage(20, 30, 1, 0)).thenReturn(5);

        BattleActionScRsp rsp = act(playerId, battleId, enemyId, 1);
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(rsp.getDamage()).isEqualTo(70);
        verify(antiCheat).checkBehaviorAnomaly(eq(playerId), eq("click"), eq(1.0), anyLong());
        verify(antiCheat).checkDamage(playerId, 70, 70);
    }

    private void seedState(long battleId, long playerId, long enemyId) throws Exception {
        BattleService.BattleRuntimeState state = new BattleService.BattleRuntimeState();
        state.battleId = battleId;
        state.playerId = playerId;
        state.enemyEntityId = enemyId;
        state.playerHp = 200;
        state.playerHpMax = 200;
        state.playerMp = 100;
        state.playerAttack = 80;
        state.playerDefense = 30;
        state.enemyHp = 400;
        state.enemyHpMax = 400;
        state.enemyMp = 50;
        state.enemyAttack = 20;
        state.enemyDefense = 10;
        state.nextActionId = 1L;
        state.turnNumber = 1;
        state.ended = false;
        redisKv.put("battle:state:" + battleId, objectMapper.writeValueAsString(state));
    }

    private BattleActionScRsp act(long playerId, long battleId, long targetId, int actionType) throws Exception {
        return BattleActionScRsp.parseFrom(battleService.handleBattleAction(playerId,
                BattleActionCsReq.newBuilder()
                        .setBattleId(battleId)
                        .setTimestamp(System.currentTimeMillis())
                        .setActionType(actionType)
                        .setTargetEntityId(targetId)
                        .build()).payload());
    }
}
