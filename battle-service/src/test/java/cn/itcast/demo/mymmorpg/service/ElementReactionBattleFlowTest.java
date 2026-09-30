package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.challenge.ChallengeConfigLoader;
import cn.itcast.demo.mymmorpg.element.ElementReactionEngine;
import cn.itcast.demo.mymmorpg.element.ReactionType;
import cn.itcast.demo.mymmorpg.model.BattleSceneFactory;
import cn.itcast.demo.mymmorpg.port.BattleScenePort;
import cn.itcast.demo.mymmorpg.port.PlayerNotificationPort;
import cn.itcast.demo.mymmorpg.port.PlayerProgressPort;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleActionCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleActionScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleEndCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleEndScRsp;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 元素反应战斗流程：附着火 → 触发雷超载（含预测校验）→ 击杀结算 → TLog 埋点 → Aura 清理。
 */
public class ElementReactionBattleFlowTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, String> redisKv = new HashMap<>();
    private final List<Map<String, Object>> tlogEvents = new ArrayList<>();
    private ElementReactionEngine reactionEngine;
    private BattleService battleService;
    private ValueOperations<String, String> valueOps;
    private StringRedisTemplate redis;
    private BattlePolicy battlePolicy;
    private BattleEventPublisher battleEventPublisher;

    @BeforeMethod
    @SuppressWarnings("unchecked")
    public void setUp() {
        redisKv.clear();
        tlogEvents.clear();
        reactionEngine = new ElementReactionEngine();

        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
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
        lenient().when(redis.delete(anyString())).thenAnswer(inv -> {
            redisKv.remove(inv.getArgument(0));
            return Boolean.TRUE;
        });

        battlePolicy = mock(BattlePolicy.class);
        battleEventPublisher = mock(BattleEventPublisher.class);
        ObjectProvider<RogueService> rogue = mock(ObjectProvider.class);
        when(rogue.getIfAvailable()).thenReturn(null);
        ObjectProvider<BattleEndProjectionNotifier> projection = mock(ObjectProvider.class);
        when(projection.getIfAvailable()).thenReturn(null);
        ObjectProvider<TLogEventPublisher> tlog = mock(ObjectProvider.class);
        when(tlog.getIfAvailable()).thenReturn((eventType, playerId, fields) -> {
            Map<String, Object> row = new HashMap<>(fields);
            row.put("eventType", eventType);
            row.put("playerId", playerId);
            tlogEvents.add(row);
        });
        @SuppressWarnings("unchecked")
        ObjectProvider<cn.itcast.demo.mymmorpg.anticheat.AntiCheatService> antiCheat = mock(ObjectProvider.class);
        when(antiCheat.getIfAvailable()).thenReturn(null);

        battleService = new BattleService(
                mock(ConfigQueryService.class),
                mock(PlayerRepository.class),
                mock(BattleScenePort.class),
                redis,
                objectMapper,
                battlePolicy,
                battleEventPublisher,
                mock(PlayerNotificationPort.class),
                mock(PlayerProgressPort.class),
                mock(BattleSceneFactory.class),
                new BattleStatsCollector(),
                mock(ChallengeConfigLoader.class),
                rogue,
                projection,
                reactionEngine,
                tlog,
                antiCheat);
    }

    @Test
    public void pyroAttach_thenElectroOverload_withPredictionAndSettle() throws Exception {
        long playerId = 88L;
        long battleId = 88001L;
        long enemyId = 9001L;
        BattleService.BattleRuntimeState state = newState(battleId, playerId, enemyId, 200, 400);
        redisKv.put("battle:state:" + battleId, objectMapper.writeValueAsString(state));

        when(battlePolicy.computeDamage(80, 10, 1, 0)).thenReturn(70);
        when(battlePolicy.computeDamage(20, 30, 1, 0)).thenReturn(5);

        // 1) 火附着：无反应，伤害=基础
        BattleActionScRsp attach = BattleActionScRsp.parseFrom(battleService.handleBattleAction(playerId,
                BattleActionCsReq.newBuilder()
                        .setBattleId(battleId)
                        .setTimestamp(System.currentTimeMillis())
                        .setActionType(1)
                        .setTargetEntityId(enemyId)
                        .setElementType(1) // PYRO
                        .build()).payload());
        assertThat(attach.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(attach.getDamage()).isEqualTo(70);
        assertThat(attach.getReactionType()).isEqualTo(0);
        assertThat(attach.getAuraElement()).isEqualTo(1);
        assertThat(attach.getRollback()).isFalse();
        assertThat(reactionEngine.currentAura(battleId, enemyId).name()).isEqualTo("PYRO");

        // 同步 Redis 状态到最新（mock set 已写入）
        BattleService.BattleRuntimeState afterAttach = objectMapper.readValue(
                redisKv.get("battle:state:" + battleId), BattleService.BattleRuntimeState.class);
        afterAttach.enemyHp = 300; // 保证未结束，继续打第二段
        afterAttach.ended = false;
        afterAttach.playerHp = 180;
        redisKv.put("battle:state:" + battleId, objectMapper.writeValueAsString(afterAttach));

        // 2) 雷触发超载：预测正确 → 无回滚；倍率 2.0 → 140
        BattleActionScRsp overload = BattleActionScRsp.parseFrom(battleService.handleBattleAction(playerId,
                BattleActionCsReq.newBuilder()
                        .setBattleId(battleId)
                        .setTimestamp(System.currentTimeMillis())
                        .setActionType(1)
                        .setTargetEntityId(enemyId)
                        .setElementType(3) // ELECTRO
                        .setClientPredictedDamage(140)
                        .setClientPredictedReaction(ReactionType.OVERLOAD.getCode())
                        .build()).payload());
        assertThat(overload.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(overload.getDamage()).isEqualTo(140);
        assertThat(overload.getFinalDamage()).isEqualTo(140);
        assertThat(overload.getReactionType()).isEqualTo(ReactionType.OVERLOAD.getCode());
        assertThat(overload.getRollback()).isFalse();

        // 3) 错误预测 → rollback=true
        BattleService.BattleRuntimeState mid = objectMapper.readValue(
                redisKv.get("battle:state:" + battleId), BattleService.BattleRuntimeState.class);
        mid.enemyHp = 250;
        mid.ended = false;
        mid.playerHp = 160;
        redisKv.put("battle:state:" + battleId, objectMapper.writeValueAsString(mid));
        reactionEngine.resolve(battleId, enemyId,
                cn.itcast.demo.mymmorpg.element.ElementType.CRYO, 1, 0, 0);

        BattleActionScRsp mismatch = BattleActionScRsp.parseFrom(battleService.handleBattleAction(playerId,
                BattleActionCsReq.newBuilder()
                        .setBattleId(battleId)
                        .setTimestamp(System.currentTimeMillis())
                        .setActionType(1)
                        .setTargetEntityId(enemyId)
                        .setElementType(2) // HYDRO → FREEZE
                        .setClientPredictedDamage(999)
                        .setClientPredictedReaction(ReactionType.FREEZE.getCode())
                        .build()).payload());
        assertThat(mismatch.getReactionType()).isEqualTo(ReactionType.FREEZE.getCode());
        assertThat(mismatch.getRollback()).isTrue();

        // 4) 击杀结算 + TLog
        BattleService.BattleRuntimeState dead = objectMapper.readValue(
                redisKv.get("battle:state:" + battleId), BattleService.BattleRuntimeState.class);
        dead.enemyHp = 0;
        dead.ended = true;
        dead.expReward = 15;
        redisKv.put("battle:state:" + battleId, objectMapper.writeValueAsString(dead));

        BattleEndScRsp end = BattleEndScRsp.parseFrom(battleService.handleBattleEnd(playerId,
                BattleEndCsReq.newBuilder().setBattleId(battleId).setResult(1).setDuration(42).build()).payload());
        assertThat(end.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(reactionEngine.currentAura(battleId, enemyId).name()).isEqualTo("NONE");
        assertThat(tlogEvents).anySatisfy(e -> {
            assertThat(e.get("eventType")).isEqualTo("battle_end");
            assertThat(e.get("playerId")).isEqualTo(playerId);
            assertThat(e.get("result")).isEqualTo(1);
        });
        verify(battleEventPublisher).publishBattleEnded(playerId, battleId, 1, 15, 42);
        verify(redis).delete("battle:state:" + battleId);
    }

    private static BattleService.BattleRuntimeState newState(
            long battleId, long playerId, long enemyId, int playerHp, int enemyHp) {
        BattleService.BattleRuntimeState state = new BattleService.BattleRuntimeState();
        state.battleId = battleId;
        state.playerId = playerId;
        state.sceneId = 1;
        state.enemyEntityId = enemyId;
        state.playerHp = playerHp;
        state.playerHpMax = 250;
        state.playerMp = 50;
        state.playerMpMax = 50;
        state.playerAttack = 80;
        state.playerDefense = 30;
        state.enemyHp = enemyHp;
        state.enemyHpMax = enemyHp;
        state.enemyMp = 0;
        state.enemyAttack = 20;
        state.enemyDefense = 10;
        state.turnNumber = 1;
        state.nextActionId = 1L;
        state.currentActorId = playerId;
        state.ended = false;
        state.expReward = 10;
        state.monsterTemplateId = 1;
        state.playerLevel = 10;
        return state;
    }
}
