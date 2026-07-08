/**
 * 文件说明：BattleService 单元测试类。
 * 职责：使用 Mockito 模拟依赖，验证开战、行动、结算等核心路径与异常路径，并输出中文测试日志。
 */
package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.MonsterConfig;
import cn.itcast.demo.mymmorpg.entity.Player;
import cn.itcast.demo.mymmorpg.model.BattleSceneFactory;
import cn.itcast.demo.mymmorpg.port.BattleScenePort;
import cn.itcast.demo.mymmorpg.port.PlayerNotificationPort;
import cn.itcast.demo.mymmorpg.port.PlayerProgressPort;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleActionCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleActionScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleEndCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleEndScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleStartCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleStartScRsp;
import cn.itcast.demo.mymmorpg.repository.PlayerRepository;
import cn.itcast.demo.mymmorpg.support.BattlePolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BattleService 单元测试：Mock 外部依赖，日志输出具体入参与断言结果。
 */
public class BattleServiceTest {

    private static final Logger log = LoggerFactory.getLogger(BattleServiceTest.class);

    @Mock
    private ConfigQueryService configQueryService;
    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private BattleScenePort battleScenePort;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private BattlePolicy battlePolicy;
    @Mock
    private BattleEventPublisher battleEventPublisher;
    @Mock
    private PlayerNotificationPort playerNotificationPort;
    @Mock
    private PlayerProgressPort playerProgressPort;
    @Mock
    private BattleSceneFactory battleSceneFactory;

    private AutoCloseable mocks;
    private BattleService battleService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BattleSceneFactory realFactory = new BattleSceneFactory();

    @BeforeMethod
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        battleService = new BattleService(
                configQueryService,
                playerRepository,
                battleScenePort,
                stringRedisTemplate,
                objectMapper,
                battlePolicy,
                battleEventPublisher,
                playerNotificationPort,
                playerProgressPort,
                battleSceneFactory);
        log.info("[测试前置] BattleService 已初始化 | redisStatePrefix=battle:state: | redisActivePrefix=battle:active:");
    }

    @AfterMethod
    public void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    public void handleBattleStart_invalidPlayer() throws Exception {
        long playerId = 0L;
        int lineupId = 1;
        int enemyId = 1;
        log.info("[测试开始] 场景=未选角色 | playerId={} | lineupId={} | enemyId={} | 期望retcode={}",
                playerId, lineupId, enemyId, RetCode.PLAYER_NOT_SELECTED);

        BattleStartCsReq req = BattleStartCsReq.newBuilder().setLineupId(lineupId).setEnemyId(enemyId).build();
        ProtocolMessage msg = battleService.handleBattleStart(playerId, req);

        assertThat(msg.msgId()).isEqualTo(MessageId.BATTLE_START_SC_RSP);
        BattleStartScRsp rsp = BattleStartScRsp.parseFrom(msg.payload());
        log.info("[测试断言] 场景=未选角色 | msgId={} | retcode={} | battleId={}",
                msg.msgId(), rsp.getRetcode(), rsp.getBattleId());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.PLAYER_NOT_SELECTED);
    }

    @Test
    public void handleBattleStart_invalidLineup() throws Exception {
        long playerId = 1L;
        int lineupId = 0;
        int enemyId = 1;
        log.info("[测试开始] 场景=阵容无效 | playerId={} | lineupId={} | enemyId={} | 期望retcode={}",
                playerId, lineupId, enemyId, RetCode.BATTLE_LINEUP_INVALID);

        BattleStartCsReq req = BattleStartCsReq.newBuilder().setLineupId(lineupId).setEnemyId(enemyId).build();
        ProtocolMessage msg = battleService.handleBattleStart(playerId, req);
        BattleStartScRsp rsp = BattleStartScRsp.parseFrom(msg.payload());

        log.info("[测试断言] 场景=阵容无效 | retcode={} | battleId={}", rsp.getRetcode(), rsp.getBattleId());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.BATTLE_LINEUP_INVALID);
    }

    @Test
    public void handleBattleStart_enemyNotInScene() throws Exception {
        long playerId = 1L;
        long enemyEntityId = 1L;
        log.info("[测试开始] 场景=敌人不在场景 | playerId={} | enemyEntityId={} | 期望retcode={}",
                playerId, enemyEntityId, RetCode.BATTLE_ENEMY_NOT_FOUND);

        when(battleScenePort.findMonsterForBattle(eq(playerId), anyLong())).thenReturn(Optional.empty());
        BattleStartCsReq req = BattleStartCsReq.newBuilder().setLineupId(1).setEnemyId(1).build();
        ProtocolMessage msg = battleService.handleBattleStart(playerId, req);
        BattleStartScRsp rsp = BattleStartScRsp.parseFrom(msg.payload());

        log.info("[测试断言] 场景=敌人不在场景 | retcode={} | sceneId={}", rsp.getRetcode(), rsp.getSceneId());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.BATTLE_ENEMY_NOT_FOUND);
    }

    @Test
    public void handleBattleStart_playerNotFound() throws Exception {
        long playerId = 5L;
        var ref = new BattleScenePort.MonsterBattleRef(1, 3, 7);
        log.info("[测试开始] 场景=玩家不存在 | playerId={} | sceneId={} | monsterTemplateId={} | 期望retcode={}",
                playerId, ref.sceneId(), ref.monsterTemplateId(), RetCode.PLAYER_NOT_FOUND);

        when(battleScenePort.findMonsterForBattle(playerId, 100L)).thenReturn(Optional.of(ref));
        when(configQueryService.findMonsterById(7)).thenReturn(buildMonsterConfig(7, "slime", 1));
        when(playerRepository.findById(playerId)).thenReturn(Optional.empty());

        BattleStartCsReq req = BattleStartCsReq.newBuilder().setLineupId(1).setEnemyId(100).build();
        BattleStartScRsp rsp = BattleStartScRsp.parseFrom(
                battleService.handleBattleStart(playerId, req).payload());

        log.info("[测试断言] 场景=玩家不存在 | retcode={} | battleId={}", rsp.getRetcode(), rsp.getBattleId());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.PLAYER_NOT_FOUND);
    }

    @Test
    public void handleBattleStart_alreadyActive() throws Exception {
        long playerId = 8L;
        var ref = new BattleScenePort.MonsterBattleRef(2, 5, 9);
        log.info("[测试开始] 场景=已有进行中战斗 | playerId={} | sceneId={} | activeKey=battle:active:{} | 期望retcode={}",
                playerId, ref.sceneId(), playerId, RetCode.BATTLE_ALREADY_ACTIVE);

        when(battleScenePort.findMonsterForBattle(playerId, 200L)).thenReturn(Optional.of(ref));
        when(configQueryService.findMonsterById(9)).thenReturn(buildMonsterConfig(9, "wolf", 2));
        when(playerRepository.findById(playerId)).thenReturn(Optional.of(buildPlayer(playerId, "p8", 4)));
        when(stringRedisTemplate.hasKey("battle:active:" + playerId)).thenReturn(true);

        BattleStartCsReq req = BattleStartCsReq.newBuilder().setLineupId(1).setEnemyId(200).build();
        BattleStartScRsp rsp = BattleStartScRsp.parseFrom(
                battleService.handleBattleStart(playerId, req).payload());

        log.info("[测试断言] 场景=已有进行中战斗 | retcode={} | battleId={}", rsp.getRetcode(), rsp.getBattleId());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.BATTLE_ALREADY_ACTIVE);
    }

    @Test
    public void handleBattleStart_success_persistsStateAndActiveKey() throws Exception {
        long playerId = 10L;
        int sceneId = 5;
        long enemyEntityId = 200L;
        int monsterTemplateId = 7;
        var ref = new BattleScenePort.MonsterBattleRef(sceneId, 5, monsterTemplateId);
        log.info("[测试开始] 场景=成功开战 | playerId={} | lineupId={} | enemyEntityId={} | sceneId={} | battleType={}",
                playerId, 1, enemyEntityId, sceneId, 1);

        when(battleScenePort.findMonsterForBattle(playerId, enemyEntityId)).thenReturn(Optional.of(ref));
        MonsterConfig mc = buildMonsterConfig(monsterTemplateId, "orc", 2);
        mc.setHpMax(50);
        mc.setMpMax(10);
        mc.setAttack(12);
        mc.setDefense(4);
        mc.setExpReward(20);
        when(configQueryService.findMonsterById(monsterTemplateId)).thenReturn(mc);
        when(playerRepository.findById(playerId)).thenReturn(Optional.of(buildPlayer(playerId, "p1", 3)));
        when(stringRedisTemplate.hasKey("battle:active:" + playerId)).thenReturn(false);

        when(battleSceneFactory.createState(anyLong(), anyInt(), anyLong(), anyLong(), anyLong(), any(), any(),
                anyString(), anyInt(), anyInt(), anyInt()))
                .thenAnswer(inv -> realFactory.createState(
                        inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), inv.getArgument(3),
                        inv.getArgument(4), inv.getArgument(5), inv.getArgument(6),
                        inv.getArgument(7), inv.getArgument(8), inv.getArgument(9), inv.getArgument(10)));

        BattleStartCsReq req = BattleStartCsReq.newBuilder()
                .setLineupId(1)
                .setEnemyId(200)
                .setBattleType(1)
                .build();
        ProtocolMessage msg = battleService.handleBattleStart(playerId, req);
        BattleStartScRsp rsp = BattleStartScRsp.parseFrom(msg.payload());

        log.info("战局初始化: battleId={}, sceneId={}, enemyEntityId={}, nextActionId={}",
                rsp.getBattleId(), rsp.getSceneId(), enemyEntityId, 1L);
        log.info("[测试断言] 场景=成功开战 | retcode={} | battleId={} | playerHp={} | enemyHp={} | playerAttack={} | enemyAttack={}",
                rsp.getRetcode(), rsp.getBattleId(),
                rsp.getPlayerInfo().getHp(), rsp.getEnemyInfo().getHp(),
                rsp.getPlayerInfo().getAttack(), rsp.getEnemyInfo().getAttack());

        assertThat(rsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(rsp.getBattleId()).isPositive();
        assertThat(rsp.getPlayerInfo().getHp()).isEqualTo(rsp.getPlayerInfo().getHpMax());
        assertThat(rsp.getEnemyInfo().getHp()).isEqualTo(rsp.getEnemyInfo().getHpMax());

        verify(valueOps, times(2)).set(anyString(), anyString(), any());
        verify(battleEventPublisher).publishBattleStarted(
                eq(playerId), anyLong(), eq(sceneId), eq(enemyEntityId), eq(monsterTemplateId));
    }

    @Test
    public void handleBattleAction_notFound() throws Exception {
        long battleId = 999L;
        long playerId = 1L;
        log.info("[测试开始] 场景=战斗状态不存在 | playerId={} | battleId={} | redisKey=battle:state:{} | 期望retcode={}",
                playerId, battleId, battleId, RetCode.BATTLE_NOT_FOUND);

        when(valueOps.get("battle:state:" + battleId)).thenReturn(null);
        BattleActionCsReq req = BattleActionCsReq.newBuilder()
                .setBattleId(battleId)
                .setTimestamp(System.currentTimeMillis())
                .setActionType(1)
                .setTargetEntityId(1L)
                .build();
        BattleActionScRsp rsp = BattleActionScRsp.parseFrom(
                battleService.handleBattleAction(playerId, req).payload());

        log.info("[测试断言] 场景=战斗状态不存在 | retcode={} | battleId={} | actionId={}",
                rsp.getRetcode(), rsp.getBattleId(), rsp.getActionId());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.BATTLE_NOT_FOUND);
    }

    @Test
    public void handleBattleAction_invalidTimestamp() throws Exception {
        long playerId = 1L;
        long battleId = 66L;
        BattleService.BattleRuntimeState state = buildRuntimeState(battleId, playerId, 2L, 50, 50);
        log.info("[测试开始] 场景=时间戳过期 | playerId={} | battleId={} | playerHp={} | enemyHp={} | 期望retcode={}",
                playerId, battleId, state.playerHp, state.enemyHp, RetCode.BATTLE_INVALID_ACTION);

        when(valueOps.get("battle:state:" + battleId)).thenReturn(objectMapper.writeValueAsString(state));
        BattleActionCsReq req = BattleActionCsReq.newBuilder()
                .setBattleId(battleId)
                .setTimestamp(System.currentTimeMillis() - 600_000L)
                .setActionType(1)
                .setTargetEntityId(state.enemyEntityId)
                .build();
        BattleActionScRsp rsp = BattleActionScRsp.parseFrom(
                battleService.handleBattleAction(playerId, req).payload());

        log.info("[测试断言] 场景=时间戳过期 | retcode={} | battleId={}", rsp.getRetcode(), rsp.getBattleId());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.BATTLE_INVALID_ACTION);
    }

    @Test
    public void handleBattleAction_invalidTarget() throws Exception {
        long playerId = 1L;
        long battleId = 77L;
        BattleService.BattleRuntimeState state = buildRuntimeState(battleId, playerId, 2L, 50, 50);
        log.info("[测试开始] 场景=攻击目标无效 | playerId={} | battleId={} | enemyEntityId={} | targetEntityId={} | 期望retcode={}",
                playerId, battleId, state.enemyEntityId, 999L, RetCode.BATTLE_TARGET_INVALID);

        when(valueOps.get("battle:state:" + battleId)).thenReturn(objectMapper.writeValueAsString(state));
        BattleActionCsReq req = BattleActionCsReq.newBuilder()
                .setBattleId(battleId)
                .setTimestamp(System.currentTimeMillis())
                .setActionType(1)
                .setTargetEntityId(999L)
                .build();
        BattleActionScRsp rsp = BattleActionScRsp.parseFrom(
                battleService.handleBattleAction(playerId, req).payload());

        log.info("[测试断言] 场景=攻击目标无效 | retcode={} | battleId={}", rsp.getRetcode(), rsp.getBattleId());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.BATTLE_TARGET_INVALID);
    }

    @Test
    public void handleBattleAction_normalAttack_success() throws Exception {
        long playerId = 10L;
        long battleId = 10001L;
        BattleService.BattleRuntimeState state = buildRuntimeState(battleId, playerId, 200L, 250, 50);
        state.playerAttack = 65;
        state.playerDefense = 26;
        state.enemyAttack = 12;
        state.enemyDefense = 4;
        state.nextActionId = 3L;
        log.info("[测试开始] 场景=普通攻击 | playerId={} | battleId={} | turn={} | playerHp={} | enemyHp={} | actionType={}",
                playerId, battleId, state.nextActionId, state.playerHp, state.enemyHp, 1);

        when(valueOps.get("battle:state:" + battleId)).thenReturn(objectMapper.writeValueAsString(state));
        when(battlePolicy.computeDamage(65, 4, 1, 0)).thenReturn(61);
        when(battlePolicy.computeDamage(12, 26, 1, 0)).thenReturn(1);

        BattleActionCsReq req = BattleActionCsReq.newBuilder()
                .setBattleId(battleId)
                .setTimestamp(System.currentTimeMillis())
                .setActionType(1)
                .setTargetEntityId(state.enemyEntityId)
                .build();
        BattleActionScRsp rsp = BattleActionScRsp.parseFrom(
                battleService.handleBattleAction(playerId, req).payload());

        ArgumentCaptor<String> stateCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq("battle:state:" + battleId), stateCaptor.capture(), any());
        BattleService.BattleRuntimeState after = objectMapper.readValue(
                stateCaptor.getValue(), BattleService.BattleRuntimeState.class);

        log.info("回合结算: battleId={}, actionId={}, damage={}, playerHpAfter={}, enemyHpAfter={}",
                battleId, rsp.getActionId(), rsp.getDamage(), after.playerHp, after.enemyHp);
        log.info("[测试断言] 场景=普通攻击 | retcode={} | actionId={} | damage={} | ended={} | syncMsgId={}",
                rsp.getRetcode(), rsp.getActionId(), rsp.getDamage(), after.ended, MessageId.BATTLE_SYNC_SC_NOTIFY);

        assertThat(rsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(rsp.getActionId()).isEqualTo(3L);
        assertThat(rsp.getDamage()).isEqualTo(61);
        assertThat(after.enemyHp).isZero();
        assertThat(after.ended).isTrue();
        verify(playerNotificationPort).send(eq(playerId), eq(MessageId.BATTLE_SYNC_SC_NOTIFY), any());
        verify(valueOps, times(2)).set(anyString(), anyString(), any());
    }

    @Test
    public void handleBattleAction_useItem_healSelf() throws Exception {
        long playerId = 10L;
        long battleId = 10002L;
        BattleService.BattleRuntimeState state = buildRuntimeState(battleId, playerId, 200L, 100, 50);
        state.playerHpMax = 250;
        state.playerAttack = 65;
        state.playerDefense = 26;
        state.enemyAttack = 12;
        state.enemyDefense = 4;
        log.info("[测试开始] 场景=使用道具治疗 | playerId={} | battleId={} | playerHp={}/{} | itemId={} | actionType={}",
                playerId, battleId, state.playerHp, state.playerHpMax, 1001, 3);

        when(valueOps.get("battle:state:" + battleId)).thenReturn(objectMapper.writeValueAsString(state));
        when(battlePolicy.computeHeal(3, 1001)).thenReturn(150);
        when(battlePolicy.computeDamage(12, 26, 1, 0)).thenReturn(1);

        BattleActionCsReq req = BattleActionCsReq.newBuilder()
                .setBattleId(battleId)
                .setTimestamp(System.currentTimeMillis())
                .setActionType(3)
                .setTargetEntityId(playerId)
                .setItemId(1001)
                .build();
        BattleActionScRsp rsp = BattleActionScRsp.parseFrom(
                battleService.handleBattleAction(playerId, req).payload());

        ArgumentCaptor<String> stateCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq("battle:state:" + battleId), stateCaptor.capture(), any());
        BattleService.BattleRuntimeState after = objectMapper.readValue(
                stateCaptor.getValue(), BattleService.BattleRuntimeState.class);

        log.info("[测试断言] 场景=使用道具治疗 | retcode={} | heal={} | playerHpAfter={} | enemyHpAfter={} | 怪物反击后玩家Hp={}",
                rsp.getRetcode(), rsp.getHeal(), after.playerHp, after.enemyHp, after.playerHp);
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(rsp.getHeal()).isEqualTo(150);
        assertThat(after.playerHp).isEqualTo(249);
    }

    @Test
    public void handleBattleEnd_resultMismatch() throws Exception {
        long playerId = 1L;
        long battleId = 55L;
        BattleService.BattleRuntimeState state = buildRuntimeState(battleId, playerId, 2L, 10, 10);
        log.info("[测试开始] 场景=结算结果不一致 | playerId={} | battleId={} | playerHp={} | enemyHp={} | clientResult={} | 期望retcode={}",
                playerId, battleId, state.playerHp, state.enemyHp, 1, RetCode.BATTLE_RESULT_MISMATCH);

        when(valueOps.get("battle:state:" + battleId)).thenReturn(objectMapper.writeValueAsString(state));
        BattleEndCsReq req = BattleEndCsReq.newBuilder().setBattleId(battleId).setResult(1).setDuration(1).build();
        BattleEndScRsp rsp = BattleEndScRsp.parseFrom(
                battleService.handleBattleEnd(playerId, req).payload());

        log.info("[测试断言] 场景=结算结果不一致 | retcode={} | battleId={} | playerExp={}",
                rsp.getRetcode(), rsp.getBattleId(), rsp.getPlayerExp());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.BATTLE_RESULT_MISMATCH);
    }

    @Test
    public void handleBattleEnd_victory_success() throws Exception {
        long playerId = 10L;
        long battleId = 10003L;
        BattleService.BattleRuntimeState state = buildRuntimeState(battleId, playerId, 200L, 80, 0);
        state.expReward = 20;
        state.ended = true;
        Player player = buildPlayer(playerId, "hero", 5);
        log.info("[测试开始] 场景=胜利结算 | playerId={} | battleId={} | enemyHp={} | expReward={} | duration={}",
                playerId, battleId, state.enemyHp, state.expReward, 30);

        when(valueOps.get("battle:state:" + battleId)).thenReturn(objectMapper.writeValueAsString(state));
        when(playerRepository.findById(playerId)).thenReturn(Optional.of(player));

        BattleEndCsReq req = BattleEndCsReq.newBuilder()
                .setBattleId(battleId)
                .setResult(1)
                .setDuration(30)
                .build();
        BattleEndScRsp rsp = BattleEndScRsp.parseFrom(
                battleService.handleBattleEnd(playerId, req).payload());

        log.info("[测试断言] 场景=胜利结算 | retcode={} | playerExp={} | currencyRewards={} | itemRewardCount={}",
                rsp.getRetcode(), rsp.getPlayerExp(), rsp.getCurrencyRewardsMap(), rsp.getItemRewardsCount());

        assertThat(rsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(rsp.getPlayerExp()).isEqualTo(20);
        assertThat(rsp.getCurrencyRewardsMap()).containsEntry(1, 500);
        assertThat(rsp.getItemRewardsCount()).isEqualTo(1);

        verify(battleScenePort).removeMonsterFromScene(playerId, state.enemyEntityId);
        verify(playerProgressPort).addExp(player, 20);
        verify(battleEventPublisher).publishBattleEnded(playerId, battleId, 1, 20, 30);
        verify(stringRedisTemplate).delete("battle:state:" + battleId);
        verify(stringRedisTemplate).delete("battle:active:" + playerId);
    }

    @Test
    public void handleBattleEnd_notFound() throws Exception {
        long playerId = 1L;
        long battleId = 404L;
        log.info("[测试开始] 场景=结算战斗不存在 | playerId={} | battleId={} | 期望retcode={}",
                playerId, battleId, RetCode.BATTLE_NOT_FOUND);

        when(valueOps.get("battle:state:" + battleId)).thenReturn(null);
        BattleEndCsReq req = BattleEndCsReq.newBuilder().setBattleId(battleId).setResult(0).setDuration(1).build();
        BattleEndScRsp rsp = BattleEndScRsp.parseFrom(
                battleService.handleBattleEnd(playerId, req).payload());

        log.info("[测试断言] 场景=结算战斗不存在 | retcode={} | battleId={}", rsp.getRetcode(), rsp.getBattleId());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.BATTLE_NOT_FOUND);
        verify(battleEventPublisher, never()).publishBattleEnded(anyLong(), anyLong(), anyInt(), anyInt(), anyInt());
    }

    private static Player buildPlayer(long id, String name, int level) {
        Player player = new Player();
        player.setId(id);
        player.setName(name);
        player.setLevel(level);
        return player;
    }

    private static MonsterConfig buildMonsterConfig(int id, String name, int level) {
        MonsterConfig mc = new MonsterConfig();
        mc.setId(id);
        mc.setName(name);
        mc.setLevel(level);
        mc.setHpMax(100);
        mc.setMpMax(0);
        mc.setAttack(10);
        mc.setDefense(5);
        mc.setExpReward(10);
        return mc;
    }

    private static BattleService.BattleRuntimeState buildRuntimeState(
            long battleId, long playerId, long enemyEntityId, int playerHp, int enemyHp) {
        BattleService.BattleRuntimeState state = new BattleService.BattleRuntimeState();
        state.battleId = battleId;
        state.playerId = playerId;
        state.enemyEntityId = enemyEntityId;
        state.playerHp = playerHp;
        state.enemyHp = enemyHp;
        state.playerHpMax = 250;
        state.enemyHpMax = 50;
        state.playerMp = 30;
        state.playerMpMax = 30;
        state.enemyMp = 0;
        state.enemyMpMax = 0;
        state.nextActionId = 1L;
        state.ended = false;
        return state;
    }
}
