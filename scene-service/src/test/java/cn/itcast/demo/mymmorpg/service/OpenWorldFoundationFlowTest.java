package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.anticheat.AntiCheatService;
import cn.itcast.demo.mymmorpg.center.MigrationTicketService;
import cn.itcast.demo.mymmorpg.center.SceneMigrationPlan;
import cn.itcast.demo.mymmorpg.config.SceneRuntimeProperties;
import cn.itcast.demo.mymmorpg.entity.MapConfig;
import cn.itcast.demo.mymmorpg.entity.Player;
import cn.itcast.demo.mymmorpg.model.MonsterWaveSimpleFactory;
import cn.itcast.demo.mymmorpg.port.PlayerCachePort;
import cn.itcast.demo.mymmorpg.port.PlayerNotificationPort;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.protocol.protobuf.EnterSceneCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.EnterSceneScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.MoveCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.MoveScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.TransferSceneCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.TransferSceneScRsp;
import cn.itcast.demo.mymmorpg.support.ScenePolicy;
import cn.itcast.demo.mymmorpg.world.WorldZoneManager;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
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
 * 大世界底座端到端流程：无缝票据进场、反作弊瞬移、动态域统计、AOI 压测。
 */
public class OpenWorldFoundationFlowTest {

    @Mock private ConfigQueryService configQueryService;
    @Mock private PlayerCachePort playerCachePort;
    @Mock private ScenePolicy scenePolicy;
    @Mock private PlayerNotificationPort playerNotificationPort;
    @Mock private SceneEventPublisher sceneEventPublisher;
    @Mock private CenterSceneRouter centerSceneRouter;

    private AutoCloseable mocks;
    private SceneActorService sceneActorService;
    private MigrationTicketService migrationTicketService;
    private AntiCheatService antiCheatService;
    private WorldZoneManager worldZoneManager;
    private AoiStressBenchmark aoiStressBenchmark;

    @BeforeMethod
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        migrationTicketService = new MigrationTicketService();
        antiCheatService = new AntiCheatService();
        antiCheatService.configure(120f, 80f, 3, 60_000L, 3.0);
        worldZoneManager = new WorldZoneManager();
        worldZoneManager.configure(10, 2, 8);
        SceneRuntimeProperties runtime = new SceneRuntimeProperties();
        SceneReconnectStore reconnectStore = new SceneReconnectStore(runtime);
        when(centerSceneRouter.isSceneReachable(anyInt())).thenReturn(true);
        when(centerSceneRouter.planMigration(anyInt())).thenAnswer(inv ->
                SceneMigrationPlan.localPlan(inv.getArgument(0), "local", "127.0.0.1", 8089));
        sceneActorService = new SceneActorService(
                configQueryService, playerCachePort, scenePolicy, playerNotificationPort,
                sceneEventPublisher, new MonsterWaveSimpleFactory(), centerSceneRouter,
                migrationTicketService, reconnectStore, runtime,
                antiCheatService, worldZoneManager);
        aoiStressBenchmark = new AoiStressBenchmark();
    }

    @AfterMethod
    public void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    public void seamlessTicket_enterScene_spawnsAtTicketCoords() throws Exception {
        long playerId = 9101L;
        int sceneId = 11;
        stubMap(sceneId, playerId, "无缝玩家");

        String ticket = migrationTicketService.issueSeamless(
                playerId, sceneId, 1, 0, 150f, 0f, 220f, 5f, -2f, 45f, 7);
        EnterSceneScRsp rsp = EnterSceneScRsp.parseFrom(
                sceneActorService.handleEnterScene(playerId, EnterSceneCsReq.newBuilder()
                        .setSceneId(1).setLineId(9).setSessionTicket(ticket).build()).payload());

        assertThat(rsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(rsp.getSceneId()).isEqualTo(sceneId);
        assertThat(rsp.getPosX()).isEqualTo(150f);
        assertThat(rsp.getPosZ()).isEqualTo(220f);
        assertThat(sceneActorService.isPlayerInScene(playerId)).isTrue();
    }

    @Test
    public void remoteTransfer_issuesSeamlessTicketWithZone() throws Exception {
        long playerId = 9102L;
        enter(playerId, 12);
        when(centerSceneRouter.planMigration(88)).thenReturn(
                SceneMigrationPlan.remotePlan(88, 55, "node-b", "10.0.0.8", 9010));

        TransferSceneScRsp rsp = TransferSceneScRsp.parseFrom(
                sceneActorService.handleTransferScene(playerId, TransferSceneCsReq.newBuilder()
                        .setTargetSceneId(88).setTargetLineId(2).build()).payload());

        assertThat(rsp.getRetcode()).isEqualTo(RetCode.SCENE_TRANSFER_REDIRECT);
        assertThat(rsp.getSessionTicket()).isNotBlank();
        MigrationTicketService.TicketPayload payload =
                migrationTicketService.consume(rsp.getSessionTicket());
        assertThat(payload).isNotNull();
        assertThat(payload.seamless()).isTrue();
        assertThat(payload.zoneId()).isEqualTo(55);
        assertThat(payload.sceneId()).isEqualTo(88);
        assertThat(payload.lineId()).isEqualTo(2);
        assertThat(sceneActorService.isPlayerInScene(playerId)).isFalse();
    }

    @Test
    public void move_teleportRejectedByAntiCheat_andStrikeAccumulates() throws Exception {
        long playerId = 9103L;
        enter(playerId, 13);

        MoveScRsp rejected = MoveScRsp.parseFrom(
                sceneActorService.handleMove(playerId, MoveCsReq.newBuilder()
                        .setTargetX(700f).setTargetY(0f).setTargetZ(500f)
                        .setSpeed(100f).setTimestamp(System.currentTimeMillis()).build()).payload());
        assertThat(rejected.getRetcode()).isEqualTo(RetCode.MOVE_REJECTED);
        assertThat(antiCheatService.strikeCount(playerId)).isGreaterThanOrEqualTo(1);

        MoveScRsp ok = MoveScRsp.parseFrom(
                sceneActorService.handleMove(playerId, MoveCsReq.newBuilder()
                        .setTargetX(420f).setTargetY(0f).setTargetZ(310f)
                        .setSpeed(60f).setTimestamp(System.currentTimeMillis()).build()).payload());
        assertThat(ok.getRetcode()).isEqualTo(RetCode.OK);
    }

    @Test
    public void move_updatesWorldZoneDensityStats() throws Exception {
        long playerId = 9104L;
        enter(playerId, 14);
        sceneActorService.handleMove(playerId, MoveCsReq.newBuilder()
                .setTargetX(410f).setTargetY(0f).setTargetZ(305f)
                .setSpeed(50f).setTimestamp(System.currentTimeMillis()).build());

        Map<String, Object> stats = sceneActorService.worldZoneStats(14);
        assertThat(stats.get("worldId")).isEqualTo(14);
        assertThat((Integer) stats.get("zoneCount")).isGreaterThanOrEqualTo(1);
        assertThat(sceneActorService.worldZoneManager().listZones(14)).isNotEmpty();
    }

    @Test
    public void aoiStressBenchmark_returnsPositiveQps() {
        Map<String, Object> result = aoiStressBenchmark.run(1500, 2000, 100, 300f);
        assertThat(result.get("ok")).isEqualTo(true);
        assertThat((Integer) result.get("entityCount")).isEqualTo(1500);
        assertThat((Long) result.get("approxQps")).isGreaterThan(0L);
        assertThat((Integer) result.get("cellCount")).isGreaterThan(0);
    }

    @Test
    public void zoneSplit_thenHandoffDetectedAcrossCells() {
        WorldZoneManager.ZoneShard root = worldZoneManager.ensureWorld(77, "node-a");
        worldZoneManager.reportPlayerCount(77, root.zoneId(), 15);
        assertThat(worldZoneManager.listZones(77).size()).isGreaterThanOrEqualTo(2);

        WorldZoneManager.ZoneShard a = worldZoneManager.listZones(77).get(0);
        WorldZoneManager.ZoneShard b = worldZoneManager.listZones(77).stream()
                .filter(z -> z.zoneId() != a.zoneId()).findFirst().orElseThrow();
        float fromX = a.cellMinX() * 100f + 5f;
        float fromZ = a.cellMinZ() * 100f + 5f;
        float toX = b.cellMinX() * 100f + 5f;
        float toZ = b.cellMinZ() * 100f + 5f;
        WorldZoneManager.BorderHandoff handoff =
                worldZoneManager.detectHandoff(77, fromX, fromZ, toX, toZ, 100);
        assertThat(handoff.required()).isTrue();
    }

    private void enter(long playerId, int sceneId) throws Exception {
        stubMap(sceneId, playerId, "flow-" + playerId);
        sceneActorService.handleEnterScene(playerId, EnterSceneCsReq.newBuilder()
                .setSceneId(sceneId).setLineId(1).build());
    }

    private void stubMap(int sceneId, long playerId, String name) {
        MapConfig map = new MapConfig();
        map.setId(sceneId);
        map.setWidth(800);
        map.setHeight(600);
        map.setDefaultLines(2);
        map.setAoiRadius(300);
        map.setGridSize(100);
        when(configQueryService.findMapById(sceneId)).thenReturn(map);
        when(scenePolicy.allowEnterScene(anyInt(), anyLong())).thenReturn(true);
        Player p = new Player();
        p.setId(playerId);
        p.setName(name);
        p.setLevel(1);
        when(playerCachePort.findById(playerId)).thenReturn(p);
        when(configQueryService.listMonstersForMap(anyInt())).thenReturn(Collections.emptyList());
        lenient().when(configQueryService.listAllMonsters()).thenReturn(Collections.emptyList());
    }
}
