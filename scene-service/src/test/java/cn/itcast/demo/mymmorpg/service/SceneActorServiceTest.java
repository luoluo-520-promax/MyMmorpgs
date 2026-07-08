/**
 * 文件说明：场景 Actor 服务单元测试。
 * 职责：验证进场景、移动校验等核心协议处理逻辑。
 */
package cn.itcast.demo.mymmorpg.service;

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
import cn.itcast.demo.mymmorpg.protocol.protobuf.MoveCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.MoveScRsp;
import cn.itcast.demo.mymmorpg.support.ScenePolicy;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
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

    private AutoCloseable mocks;
    private SceneActorService sceneActorService;

    @BeforeMethod
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        sceneActorService = new SceneActorService(
                configQueryService,
                playerCachePort,
                scenePolicy,
                playerNotificationPort,
                sceneEventPublisher,
                new MonsterWaveSimpleFactory());
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
        when(configQueryService.listAllMonsters()).thenReturn(Collections.emptyList());
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

    private void enterScene(long playerId, int sceneId) throws Exception {
        MapConfig map = mapConfig(sceneId, 800, 600, 2);
        Player player = player(playerId, "移动测试", 3);
        when(configQueryService.findMapById(sceneId)).thenReturn(map);
        when(scenePolicy.allowEnterScene(anyInt(), anyLong())).thenReturn(true);
        when(playerCachePort.findById(playerId)).thenReturn(player);
        when(configQueryService.listAllMonsters()).thenReturn(Collections.emptyList());
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
