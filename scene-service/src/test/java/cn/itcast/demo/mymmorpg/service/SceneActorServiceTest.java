/**
 * 文件说明：场景 Actor 服务单元测试。
 * 职责：验证进场景、移动校验等核心协议处理逻辑。
 */
package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.center.MigrationTicketService;
import cn.itcast.demo.mymmorpg.center.SceneMigrationPlan;
import cn.itcast.demo.mymmorpg.config.SceneRuntimeProperties;
import cn.itcast.demo.mymmorpg.entity.MapConfig;
import cn.itcast.demo.mymmorpg.entity.Player;
import cn.itcast.demo.mymmorpg.model.MonsterWaveSimpleFactory;
import cn.itcast.demo.mymmorpg.port.PlayerCachePort;
import cn.itcast.demo.mymmorpg.port.PlayerNotificationPort;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.protocol.protobuf.EnterSceneCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.EnterSceneScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetNearbyEntitiesCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetNearbyEntitiesScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.MoveCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.MoveScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.SwitchLineCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.SwitchLineScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.TransferSceneCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.TransferSceneScRsp;
import cn.itcast.demo.mymmorpg.support.ScenePolicy;
import cn.itcast.demo.mymmorpg.cache.LocalPositionCache;
import cn.itcast.demo.mymmorpg.ecs.SceneTickEngine;
import cn.itcast.demo.mymmorpg.sync.DynamicFrequencyService;
import cn.itcast.demo.mymmorpg.sync.MoveDeltaEncoder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * SceneActorService 单元测试。
 */
public class SceneActorServiceTest {

    private static final Logger log = LoggerFactory.getLogger(SceneActorServiceTest.class);

    @Mock
    private ConfigQueryService configQueryService;
    @Mock
    private PlayerCachePort playerCachePort;
    @Mock
    private ScenePolicy scenePolicy;
    @Mock
    private PlayerNotificationPort playerNotificationPort;
    @Mock
    private SceneEventPublisher sceneEventPublisher;
    @Mock
    private CenterSceneRouter centerSceneRouter;

    private AutoCloseable mocks;
    private SceneActorService sceneActorService;
    private MigrationTicketService migrationTicketService;
    private SceneRuntimeProperties runtimeProperties;

    @BeforeMethod
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        migrationTicketService = new MigrationTicketService();
        runtimeProperties = new SceneRuntimeProperties();
        SceneReconnectStore reconnectStore = new SceneReconnectStore(runtimeProperties);
        when(centerSceneRouter.isSceneReachable(anyInt())).thenReturn(true);
        when(centerSceneRouter.planMigration(anyInt())).thenAnswer(inv ->
                SceneMigrationPlan.localPlan(inv.getArgument(0), "local", "127.0.0.1", 8089));
        sceneActorService = new SceneActorService(
                configQueryService,
                playerCachePort,
                scenePolicy,
                playerNotificationPort,
                sceneEventPublisher,
                new MonsterWaveSimpleFactory(),
                centerSceneRouter,
                migrationTicketService,
                reconnectStore,
                runtimeProperties);
        log.info("[测试前置] SceneActorService 已初始化");
    }

    @AfterMethod
    public void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    public void handleEnterScene_success() throws Exception {
        long playerId = 1001L;
        int sceneId = 3;
        int lineId = 1;
        EnterSceneCsReq req = EnterSceneCsReq.newBuilder()
                .setSceneId(sceneId)
                .setLineId(lineId)
                .build();
        MapConfig map = mapConfig(sceneId, 800, 600, 2);
        Player player = player(playerId, "测试勇者", 5);
        when(configQueryService.findMapById(sceneId)).thenReturn(map);
        when(scenePolicy.allowEnterScene(sceneId, playerId)).thenReturn(true);
        when(playerCachePort.findById(playerId)).thenReturn(player);
        when(configQueryService.listMonstersForMap(anyInt())).thenReturn(Collections.emptyList());
        lenient().when(configQueryService.listAllMonsters()).thenReturn(Collections.emptyList());
        log.info("[测试开始] 场景=进场景成功 | playerId={} | sceneId={} | lineId={} | mapWidth={} | mapHeight={}",
                playerId, sceneId, lineId, map.getWidth(), map.getHeight());

        ProtocolMessage msg = sceneActorService.handleEnterScene(playerId, req);
        EnterSceneScRsp rsp = EnterSceneScRsp.parseFrom(msg.payload());

        log.info("[测试断言] 场景=进场景成功 | retcode={} | sceneId={} | lineId={} | posX={} | posY={} | posZ={} | 期望retcode=0",
                rsp.getRetcode(), rsp.getSceneId(), rsp.getLineId(), rsp.getPosX(), rsp.getPosY(), rsp.getPosZ());
        assertThat(msg.msgId()).isEqualTo(MessageId.ENTER_SCENE_SC_RSP);
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(rsp.getSceneId()).isEqualTo(sceneId);
        assertThat(rsp.getLineId()).isEqualTo(lineId);
        assertThat(rsp.getPosX()).isEqualTo(400f);
        assertThat(rsp.getPosZ()).isEqualTo(300f);
        assertThat(sceneActorService.isPlayerInScene(playerId)).isTrue();
    }

    @Test
    public void handleEnterScene_playerNotSelected() throws Exception {
        long playerId = 0L;
        int sceneId = 3;
        EnterSceneCsReq req = EnterSceneCsReq.newBuilder().setSceneId(sceneId).build();
        log.info("[测试开始] 场景=未选角进场景 | playerId={} | sceneId={}", playerId, sceneId);

        ProtocolMessage msg = sceneActorService.handleEnterScene(playerId, req);
        EnterSceneScRsp rsp = EnterSceneScRsp.parseFrom(msg.payload());

        log.info("[测试断言] 场景=未选角进场景 | retcode={} | 期望={}", rsp.getRetcode(), RetCode.PLAYER_NOT_SELECTED);
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.PLAYER_NOT_SELECTED);
    }

    @Test
    public void handleEnterScene_sceneNotFound() throws Exception {
        long playerId = 1002L;
        int sceneId = 999;
        EnterSceneCsReq req = EnterSceneCsReq.newBuilder().setSceneId(sceneId).build();
        when(configQueryService.findMapById(sceneId)).thenReturn(null);
        log.info("[测试开始] 场景=地图不存在 | playerId={} | sceneId={}", playerId, sceneId);

        ProtocolMessage msg = sceneActorService.handleEnterScene(playerId, req);
        EnterSceneScRsp rsp = EnterSceneScRsp.parseFrom(msg.payload());

        log.info("[测试断言] 场景=地图不存在 | retcode={} | 期望={}", rsp.getRetcode(), RetCode.SCENE_NOT_FOUND);
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.SCENE_NOT_FOUND);
    }

    @Test
    public void handleEnterScene_policyRejected() throws Exception {
        long playerId = 1003L;
        int sceneId = 5;
        EnterSceneCsReq req = EnterSceneCsReq.newBuilder().setSceneId(sceneId).build();
        MapConfig map = mapConfig(sceneId, 500, 500, 1);
        when(configQueryService.findMapById(sceneId)).thenReturn(map);
        when(scenePolicy.allowEnterScene(sceneId, playerId)).thenReturn(false);
        log.info("[测试开始] 场景=策略拒绝进场景 | playerId={} | sceneId={}", playerId, sceneId);

        ProtocolMessage msg = sceneActorService.handleEnterScene(playerId, req);
        EnterSceneScRsp rsp = EnterSceneScRsp.parseFrom(msg.payload());

        log.info("[测试断言] 场景=策略拒绝进场景 | retcode={} | 期望={}", rsp.getRetcode(), RetCode.INTERNAL_ERROR);
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.INTERNAL_ERROR);
    }

    @Test
    public void handleMove_success_updatesP15PerformanceSnapshot() throws Exception {
        long playerId = 2001L;
        int sceneId = 3;
        sceneActorService.setSceneTickEngine(new SceneTickEngine());
        sceneActorService.setDynamicFrequencyService(new DynamicFrequencyService());
        sceneActorService.setMoveDeltaEncoder(new MoveDeltaEncoder());
        sceneActorService.setLocalPositionCache(new LocalPositionCache());
        enterScene(playerId, sceneId);
        float targetX = 120f;
        float targetZ = 180f;
        MoveCsReq req = MoveCsReq.newBuilder()
                .setTargetX(targetX)
                .setTargetY(0f)
                .setTargetZ(targetZ)
                .setSpeed(60f)
                .setTimestamp(System.currentTimeMillis())
                .setCameraYaw(45f)
                .build();
        ProtocolMessage msg = sceneActorService.handleMove(playerId, req);
        MoveScRsp rsp = MoveScRsp.parseFrom(msg.payload());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(rsp.getPosX()).isEqualTo(targetX);
        assertThat(rsp.getPosZ()).isEqualTo(targetZ);

        Map<String, Object> perf = sceneActorService.performanceSnapshot();
        assertThat(perf.get("ecsEntities")).isEqualTo(2); /* 玩家 + ensureMonsters 向导 NPC */
        assertThat(perf.get("localPositionCache")).isNotNull();
        sceneActorService.sceneComponentTick();
    }

    @Test
    public void handleMove_success() throws Exception {
        long playerId = 2001L;
        int sceneId = 3;
        enterScene(playerId, sceneId);
        float targetX = 120f;
        float targetY = 0f;
        float targetZ = 180f;
        float speed = 60f;
        MoveCsReq req = MoveCsReq.newBuilder()
                .setTargetX(targetX)
                .setTargetY(targetY)
                .setTargetZ(targetZ)
                .setSpeed(speed)
                .setTimestamp(System.currentTimeMillis())
                .build();
        log.info("[测试开始] 场景=移动成功 | playerId={} | targetX={} | targetY={} | targetZ={} | speed={}",
                playerId, targetX, targetY, targetZ, speed);

        ProtocolMessage msg = sceneActorService.handleMove(playerId, req);
        MoveScRsp rsp = MoveScRsp.parseFrom(msg.payload());

        log.info("[测试断言] 场景=移动成功 | retcode={} | posX={} | posY={} | posZ={} | 期望retcode=0",
                rsp.getRetcode(), rsp.getPosX(), rsp.getPosY(), rsp.getPosZ());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(rsp.getPosX()).isEqualTo(targetX);
        assertThat(rsp.getPosY()).isEqualTo(targetY);
        assertThat(rsp.getPosZ()).isEqualTo(targetZ);
    }

    @Test
    public void handleEnterScene_consumesMigrationTicket() throws Exception {
        long playerId = 3001L;
        int sceneId = 5;
        MapConfig map = mapConfig(sceneId, 800, 600, 2);
        Player player = player(playerId, "迁移玩家", 4);
        when(configQueryService.findMapById(sceneId)).thenReturn(map);
        when(scenePolicy.allowEnterScene(sceneId, playerId)).thenReturn(true);
        when(playerCachePort.findById(playerId)).thenReturn(player);
        when(configQueryService.listMonstersForMap(anyInt())).thenReturn(Collections.emptyList());
        String ticket = migrationTicketService.issue(playerId, sceneId, 1, 0, 120f, 0f, 240f);

        EnterSceneCsReq req = EnterSceneCsReq.newBuilder()
                .setSceneId(1)
                .setLineId(9)
                .setSessionTicket(ticket)
                .build();
        ProtocolMessage msg = sceneActorService.handleEnterScene(playerId, req);
        EnterSceneScRsp rsp = EnterSceneScRsp.parseFrom(msg.payload());

        assertThat(rsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(rsp.getSceneId()).isEqualTo(sceneId);
        assertThat(rsp.getPosX()).isEqualTo(120f);
        assertThat(rsp.getPosZ()).isEqualTo(240f);
        assertThat(migrationTicketService.consume(ticket)).isNull();
    }

    @Test
    public void handleResumeScene_afterDisconnect() throws Exception {
        long playerId = 3002L;
        int sceneId = 6;
        enterScene(playerId, sceneId);
        String token = sceneActorService.onPlayerDisconnect(playerId);
        assertThat(token).isNotBlank();

        ProtocolMessage resumeMsg = sceneActorService.handleResumeScene(playerId,
                cn.itcast.demo.mymmorpg.protocol.protobuf.ResumeSceneCsReq.newBuilder()
                        .setResumeToken(token)
                        .build());
        cn.itcast.demo.mymmorpg.protocol.protobuf.ResumeSceneScRsp resumeRsp =
                cn.itcast.demo.mymmorpg.protocol.protobuf.ResumeSceneScRsp.parseFrom(resumeMsg.payload());
        assertThat(resumeRsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(resumeRsp.getSceneId()).isEqualTo(sceneId);
    }

    @Test
    public void handleMove_speedRejected() throws Exception {
        long playerId = 2002L;
        int sceneId = 3;
        enterScene(playerId, sceneId);
        float overSpeed = 150f;
        MoveCsReq req = MoveCsReq.newBuilder()
                .setTargetX(200f)
                .setTargetY(0f)
                .setTargetZ(200f)
                .setSpeed(overSpeed)
                .setTimestamp(System.currentTimeMillis())
                .build();
        log.info("[测试开始] 场景=超速拒绝 | playerId={} | speed={} | maxSpeed=120", playerId, overSpeed);

        ProtocolMessage msg = sceneActorService.handleMove(playerId, req);
        MoveScRsp rsp = MoveScRsp.parseFrom(msg.payload());

        log.info("[测试断言] 场景=超速拒绝 | retcode={} | 期望={}", rsp.getRetcode(), RetCode.MOVE_REJECTED);
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.MOVE_REJECTED);
    }

    @Test
    public void handleTransferScene_local_entersTargetMap() throws Exception {
        long playerId = 4001L;
        int fromScene = 3;
        int toScene = 8;
        enterScene(playerId, fromScene);
        MapConfig target = mapConfig(toScene, 500, 500, 2);
        when(configQueryService.findMapById(toScene)).thenReturn(target);
        when(centerSceneRouter.planMigration(toScene)).thenReturn(
                SceneMigrationPlan.localPlan(toScene, "local", "127.0.0.1", 8089));

        TransferSceneScRsp rsp = TransferSceneScRsp.parseFrom(
                sceneActorService.handleTransferScene(playerId, TransferSceneCsReq.newBuilder()
                        .setTargetSceneId(toScene).setTargetLineId(1).build()).payload());

        assertThat(rsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(rsp.getSceneId()).isEqualTo(toScene);
        assertThat(rsp.getSessionTicket()).isEmpty();
    }

    @Test
    public void handleTransferScene_remote_issuesRedirectTicket() throws Exception {
        long playerId = 4002L;
        enterScene(playerId, 3);
        when(centerSceneRouter.planMigration(99)).thenReturn(
                SceneMigrationPlan.remotePlan(99, 1, "node-b", "10.0.0.2", 9001));

        TransferSceneScRsp rsp = TransferSceneScRsp.parseFrom(
                sceneActorService.handleTransferScene(playerId, TransferSceneCsReq.newBuilder()
                        .setTargetSceneId(99).setTargetLineId(1).build()).payload());

        assertThat(rsp.getRetcode()).isEqualTo(RetCode.SCENE_TRANSFER_REDIRECT);
        assertThat(rsp.getRedirectHost()).isEqualTo("10.0.0.2");
        assertThat(rsp.getRedirectPort()).isEqualTo(9001);
        assertThat(rsp.getSessionTicket()).isNotBlank();
        assertThat(migrationTicketService.consume(rsp.getSessionTicket())).isNotNull();
    }

    @Test
    public void handleSwitchLine_successAndInvalidSameLine() throws Exception {
        long playerId = 4003L;
        enterScene(playerId, 3);

        SwitchLineScRsp ok = SwitchLineScRsp.parseFrom(
                sceneActorService.handleSwitchLine(playerId, SwitchLineCsReq.newBuilder()
                        .setTargetLineId(2).build()).payload());
        assertThat(ok.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(ok.getLineId()).isEqualTo(2);

        SwitchLineScRsp same = SwitchLineScRsp.parseFrom(
                sceneActorService.handleSwitchLine(playerId, SwitchLineCsReq.newBuilder()
                        .setTargetLineId(2).build()).payload());
        assertThat(same.getRetcode()).isEqualTo(RetCode.INVALID_LINE);
    }

    @Test
    public void handleEnterScene_autoOpensNewLineWhenFull() throws Exception {
        runtimeProperties.setMaxPlayersPerLine(1);
        runtimeProperties.setMaxLinesPerMap(3);
        int sceneId = 5;
        MapConfig map = mapConfig(sceneId, 800, 600, 1);
        when(configQueryService.findMapById(sceneId)).thenReturn(map);
        when(scenePolicy.allowEnterScene(anyInt(), anyLong())).thenReturn(true);
        when(configQueryService.listMonstersForMap(anyInt())).thenReturn(Collections.emptyList());
        lenient().when(configQueryService.listAllMonsters()).thenReturn(Collections.emptyList());
        when(playerCachePort.findById(5001L)).thenReturn(player(5001L, "A", 1));
        when(playerCachePort.findById(5002L)).thenReturn(player(5002L, "B", 1));

        EnterSceneScRsp first = EnterSceneScRsp.parseFrom(sceneActorService.handleEnterScene(5001L,
                EnterSceneCsReq.newBuilder().setSceneId(sceneId).setLineId(0).build()).payload());
        EnterSceneScRsp second = EnterSceneScRsp.parseFrom(sceneActorService.handleEnterScene(5002L,
                EnterSceneCsReq.newBuilder().setSceneId(sceneId).setLineId(0).build()).payload());

        assertThat(first.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(first.getLineId()).isEqualTo(1);
        assertThat(second.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(second.getLineId()).isEqualTo(2);
    }

    @Test
    public void handleEnterScene_allLinesFull_returnsSceneLineFull() throws Exception {
        runtimeProperties.setMaxPlayersPerLine(1);
        runtimeProperties.setMaxLinesPerMap(1);
        int sceneId = 6;
        MapConfig map = mapConfig(sceneId, 800, 600, 1);
        when(configQueryService.findMapById(sceneId)).thenReturn(map);
        when(scenePolicy.allowEnterScene(anyInt(), anyLong())).thenReturn(true);
        when(configQueryService.listMonstersForMap(anyInt())).thenReturn(Collections.emptyList());
        lenient().when(configQueryService.listAllMonsters()).thenReturn(Collections.emptyList());
        when(playerCachePort.findById(6001L)).thenReturn(player(6001L, "A", 1));
        when(playerCachePort.findById(6002L)).thenReturn(player(6002L, "B", 1));

        sceneActorService.handleEnterScene(6001L,
                EnterSceneCsReq.newBuilder().setSceneId(sceneId).setLineId(1).build());
        EnterSceneScRsp full = EnterSceneScRsp.parseFrom(sceneActorService.handleEnterScene(6002L,
                EnterSceneCsReq.newBuilder().setSceneId(sceneId).setLineId(0).build()).payload());

        assertThat(full.getRetcode()).isEqualTo(RetCode.SCENE_LINE_FULL);
    }

    @Test
    public void gcEmptyLines_recyclesAfterTtl() throws Exception {
        runtimeProperties.setEmptyLineTtlMs(5_000L);
        long playerId = 7001L;
        enterScene(playerId, 7);
        assertThat(sceneActorService.getSceneLineCount()).isGreaterThan(0);

        sceneActorService.onPlayerLeave(playerId);
        sceneActorService.gcEmptyLines(); // 首次标记 emptySince
        assertThat(sceneActorService.getSceneLineCount()).isGreaterThan(0);

        forceEmptyLinesExpired();
        sceneActorService.gcEmptyLines();
        assertThat(sceneActorService.getSceneLineCount()).isEqualTo(0);
    }

    @Test
    public void handleGetNearby_filtersByRadius() throws Exception {
        long playerA = 4010L;
        long playerB = 4011L;
        int sceneId = 3;
        MapConfig map = mapConfig(sceneId, 800, 600, 2);
        when(configQueryService.findMapById(sceneId)).thenReturn(map);
        when(scenePolicy.allowEnterScene(anyInt(), anyLong())).thenReturn(true);
        when(configQueryService.listMonstersForMap(anyInt())).thenReturn(Collections.emptyList());
        lenient().when(configQueryService.listAllMonsters()).thenReturn(Collections.emptyList());
        when(playerCachePort.findById(playerA)).thenReturn(player(playerA, "A", 1));
        when(playerCachePort.findById(playerB)).thenReturn(player(playerB, "B", 1));

        sceneActorService.handleEnterScene(playerA, EnterSceneCsReq.newBuilder()
                .setSceneId(sceneId).setLineId(1).build());
        sceneActorService.handleEnterScene(playerB, EnterSceneCsReq.newBuilder()
                .setSceneId(sceneId).setLineId(1).build());

        GetNearbyEntitiesScRsp near = GetNearbyEntitiesScRsp.parseFrom(
                sceneActorService.handleGetNearby(playerA, GetNearbyEntitiesCsReq.newBuilder()
                        .setCenterX(400f).setCenterY(0f).setCenterZ(300f).setRadius(50f).build()).payload());
        assertThat(near.getRetcode()).isEqualTo(RetCode.OK);

        GetNearbyEntitiesScRsp far = GetNearbyEntitiesScRsp.parseFrom(
                sceneActorService.handleGetNearby(playerA, GetNearbyEntitiesCsReq.newBuilder()
                        .setCenterX(0f).setCenterY(0f).setCenterZ(0f).setRadius(1f).build()).payload());
        assertThat(far.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(far.getEntityListCount()).isLessThanOrEqualTo(near.getEntityListCount());
    }

    private void forceEmptyLinesExpired() throws Exception {
        var field = SceneActorService.class.getDeclaredField("lineStates");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var lineStates = (java.util.Map<String, ?>) field.get(sceneActorService);
        long past = System.currentTimeMillis() - 60_000L;
        for (Object line : lineStates.values()) {
            var emptyField = line.getClass().getDeclaredField("emptySinceMillis");
            emptyField.setAccessible(true);
            emptyField.setLong(line, past);
        }
    }

    private void enterScene(long playerId, int sceneId) throws Exception {
        MapConfig map = mapConfig(sceneId, 800, 600, 2);
        Player player = player(playerId, "移动测试", 3);
        when(configQueryService.findMapById(sceneId)).thenReturn(map);
        when(scenePolicy.allowEnterScene(anyInt(), anyLong())).thenReturn(true);
        when(playerCachePort.findById(playerId)).thenReturn(player);
        when(configQueryService.listMonstersForMap(anyInt())).thenReturn(Collections.emptyList());
        lenient().when(configQueryService.listAllMonsters()).thenReturn(Collections.emptyList());
        EnterSceneCsReq enterReq = EnterSceneCsReq.newBuilder()
                .setSceneId(sceneId)
                .setLineId(1)
                .build();
        sceneActorService.handleEnterScene(playerId, enterReq);
    }

    private static MapConfig mapConfig(int id, int width, int height, int defaultLines) {
        MapConfig map = new MapConfig();
        map.setId(id);
        map.setWidth(width);
        map.setHeight(height);
        map.setDefaultLines(defaultLines);
        return map;
    }

    private static Player player(long id, String name, int level) {
        Player player = new Player();
        player.setId(id);
        player.setName(name);
        player.setLevel(level);
        return player;
    }
}
