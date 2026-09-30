package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.anticheat.AntiCheatService;
import cn.itcast.demo.mymmorpg.aoi.AoiBroadcastStrategy;
import cn.itcast.demo.mymmorpg.aoi.AoiUpdateBatcher;
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
import cn.itcast.demo.mymmorpg.protocol.protobuf.SwitchLineCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.SwitchLineScRsp;
import cn.itcast.demo.mymmorpg.support.ScenePolicy;
import cn.itcast.demo.mymmorpg.sync.MovementPredictionValidator;
import cn.itcast.demo.mymmorpg.sync.NavMeshPathValidator;
import cn.itcast.demo.mymmorpg.world.WorldZoneManager;
import cn.itcast.demo.mymmorpg.world.battle.LightweightBattleRoom;
import cn.itcast.demo.mymmorpg.world.boss.BossRespawnTimer;
import cn.itcast.demo.mymmorpg.world.debug.EntityDebugTrace;
import cn.itcast.demo.mymmorpg.world.line.LineShardScheduler;
import cn.itcast.demo.mymmorpg.world.loot.LootOwnershipPolicy;
import cn.itcast.demo.mymmorpg.world.resource.RespawnPoint;
import cn.itcast.demo.mymmorpg.world.scene.K8sSceneAllocator;
import cn.itcast.demo.mymmorpg.world.scene.SceneBackgroundPrecreator;
import cn.itcast.demo.mymmorpg.world.scene.SceneHotMigrateService;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 新架构加固端到端业务流程：
 * 进场 Trace/AOI 热力图 → NavMesh 拒移 → 分线软硬顶 → 同线组队拉入 →
 * Zone 热迁移 → 采集 Bitmap → Boss 选主 → 跨服预创建秒切 → 轻量战斗房。
 */
public class SceneArchitectureBusinessFlowTest {

    @Mock private ConfigQueryService configQueryService;
    @Mock private PlayerCachePort playerCachePort;
    @Mock private ScenePolicy scenePolicy;
    @Mock private PlayerNotificationPort playerNotificationPort;
    @Mock private SceneEventPublisher sceneEventPublisher;
    @Mock private CenterSceneRouter centerSceneRouter;

    private AutoCloseable mocks;
    private SceneActorService scene;
    private OpenWorldRuntimeService openWorld;
    private SceneRuntimeProperties runtime;
    private MigrationTicketService tickets;
    private AntiCheatService antiCheat;
    private NavMeshPathValidator navMesh;
    private EntityDebugTrace traces;
    private LineShardScheduler lines;
    private SceneHotMigrateService hotMigrate;
    private SceneBackgroundPrecreator precreator;
    private LightweightBattleRoom battleRooms;

    @BeforeMethod
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        tickets = new MigrationTicketService();
        antiCheat = new AntiCheatService();
        antiCheat.configure(120f, 80f, 5, 60_000L, 3.0);
        runtime = new SceneRuntimeProperties();
        runtime.setMaxPlayersPerLine(3);
        runtime.setSoftPlayersPerLine(2);
        runtime.setSoftCapBuffId("line_overflow_drop_bonus_5");
        runtime.setMaxLinesPerMap(5);
        runtime.setAoiBatchWindowMs(100L);
        SceneReconnectStore reconnectStore = new SceneReconnectStore(runtime);
        WorldZoneManager zones = new WorldZoneManager();
        zones.configure(10, 2, 8);

        navMesh = new NavMeshPathValidator();
        traces = new EntityDebugTrace();
        lines = new LineShardScheduler();
        lines.configure(2, 3, "line_overflow_drop_bonus_5");
        hotMigrate = new SceneHotMigrateService(tickets);
        battleRooms = new LightweightBattleRoom();
        K8sSceneAllocator allocator = new K8sSceneAllocator();
        precreator = new SceneBackgroundPrecreator(allocator, battleRooms, tickets);
        precreator.configure(30_000L, 500L);

        when(centerSceneRouter.isSceneReachable(anyInt())).thenReturn(true);
        when(centerSceneRouter.planMigration(anyInt())).thenAnswer(inv ->
                SceneMigrationPlan.localPlan(inv.getArgument(0), "local", "127.0.0.1", 8089));
        lenient().when(scenePolicy.allowEnterScene(anyInt(), anyLong())).thenReturn(true);

        scene = new SceneActorService(
                configQueryService, playerCachePort, scenePolicy, playerNotificationPort,
                sceneEventPublisher, new MonsterWaveSimpleFactory(), centerSceneRouter,
                tickets, reconnectStore, runtime, antiCheat, zones);
        scene.setNavMeshPathValidator(navMesh);
        scene.setMovementValidator(new MovementPredictionValidator());
        scene.setAoiBroadcastStrategy(new AoiBroadcastStrategy());
        scene.setAoiUpdateBatcher(new AoiUpdateBatcher());
        scene.setLineShardScheduler(lines);
        scene.setEntityDebugTrace(traces);
        scene.setSceneHotMigrateService(hotMigrate);

        openWorld = new OpenWorldRuntimeService();
        openWorld.setLocalNodeId("scene-flow-a");
    }

    @AfterMethod
    public void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    public void enter_debugGridAndTrace_thenNavMeshReject() throws Exception {
        int sceneId = 501;
        long playerId = 50101L;
        stubMap(sceneId, 800, 600, 3);
        stubPlayer(playerId, "探针");

        EnterSceneScRsp enter = EnterSceneScRsp.parseFrom(scene.handleEnterScene(playerId,
                EnterSceneCsReq.newBuilder().setSceneId(sceneId).setLineId(1)
                        .setPosX(50).setPosY(0).setPosZ(50).build()).payload());
        assertThat(enter.getRetcode()).isEqualTo(RetCode.OK);

        Map<String, Object> grid = scene.debugAoiGrid(sceneId, 1);
        assertThat(grid.get("ok")).isEqualTo(true);
        assertThat((Integer) grid.get("playerCount")).isEqualTo(1);
        assertThat(grid.get("cells")).isInstanceOf(List.class);
        assertThat(grid.get("aoiBroadcast")).isInstanceOf(Map.class);

        Map<String, Object> trace = scene.debugEntityTrace(playerId);
        assertThat(String.valueOf(trace.get("traceId"))).startsWith("tr-");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events = (List<Map<String, Object>>) trace.get("events");
        assertThat(events.stream().anyMatch(e -> "ENTER".equals(e.get("phase")))).isTrue();

        navMesh.ensureMesh(sceneId, 100, 100, 1f);
        for (int x = 55; x <= 70; x++) {
            navMesh.setBlocked(sceneId, x, 50, true);
        }
        int strikesBefore = antiCheat.strikeCount(playerId);
        MoveScRsp rejected = MoveScRsp.parseFrom(scene.handleMove(playerId, MoveCsReq.newBuilder()
                .setTargetX(80f).setTargetY(0f).setTargetZ(50f)
                .setSpeed(40f).setTimestamp(System.currentTimeMillis()).build()).payload());
        assertThat(rejected.getRetcode()).isEqualTo(RetCode.MOVE_REJECTED);
        assertThat(antiCheat.strikeCount(playerId)).isGreaterThan(strikesBefore);

        MoveScRsp ok = MoveScRsp.parseFrom(scene.handleMove(playerId, MoveCsReq.newBuilder()
                .setTargetX(52f).setTargetY(0f).setTargetZ(55f)
                .setSpeed(40f).setTimestamp(System.currentTimeMillis()).build()).payload());
        assertThat(ok.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(ok.getPosX()).isEqualTo(52f);
    }

    @Test
    public void lineSoftHardCap_andPartyPullTickets() throws Exception {
        int sceneId = 502;
        stubMap(sceneId, 400, 400, 5);
        stubPlayer(50201L, "队长");
        stubPlayer(50202L, "队员A");
        stubPlayer(50203L, "队员B");
        stubPlayer(50204L, "挤线者");

        enter(50201L, sceneId, 1, 10, 10);
        enter(50202L, sceneId, 1, 12, 12);
        // 软顶=2：第三人进 1 线仍可进（未达硬顶 3），调度器记 SOFT_CAP_BUFF
        var soft = lines.admit(sceneId, 1, 5);
        assertThat(soft.result()).isEqualTo(LineShardScheduler.AdmitResult.SOFT_CAP_BUFF);
        assertThat(soft.buffId()).isEqualTo("line_overflow_drop_bonus_5");

        enter(50203L, sceneId, 1, 14, 14);
        var hard = lines.admit(sceneId, 1, 5);
        assertThat(hard.result()).isEqualTo(LineShardScheduler.AdmitResult.HARD_CAP_REJECT);

        // 硬顶后新玩家应被分到其他线
        EnterSceneScRsp overflow = EnterSceneScRsp.parseFrom(scene.handleEnterScene(50204L,
                EnterSceneCsReq.newBuilder().setSceneId(sceneId).setLineId(1)
                        .setPosX(20).setPosY(0).setPosZ(20).build()).payload());
        assertThat(overflow.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(overflow.getLineId()).isNotEqualTo(1);

        // 队员在其他线：强制拉入队长线
        enter(50202L, sceneId, 2, 30, 30);
        Map<String, Object> pull = scene.pullPartyToLeaderLine(50201L, List.of(50202L, 50203L));
        assertThat(pull.get("ok")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pullTickets = (List<Map<String, Object>>) pull.get("tickets");
        assertThat(pullTickets).isNotEmpty();
        assertThat(String.valueOf(pullTickets.get(0).get("sessionTicket"))).isNotBlank();
        assertThat(((Number) pullTickets.get(0).get("targetLineId")).intValue()).isEqualTo(1);
    }

    @Test
    public void switchLine_hardCapRejected() throws Exception {
        int sceneId = 503;
        stubMap(sceneId, 300, 300, 3);
        stubPlayer(50301L, "A");
        stubPlayer(50302L, "B");
        stubPlayer(50303L, "C");
        stubPlayer(50304L, "D");
        enter(50301L, sceneId, 2, 10, 10);
        enter(50302L, sceneId, 2, 11, 11);
        enter(50303L, sceneId, 2, 12, 12);
        enter(50304L, sceneId, 1, 20, 20);

        SwitchLineScRsp rsp = SwitchLineScRsp.parseFrom(scene.handleSwitchLine(50304L,
                SwitchLineCsReq.newBuilder().setTargetLineId(2).build()).payload());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.SCENE_LINE_FULL);
    }

    @Test
    public void zoneHotMigrate_issuesTicketsAndDrains() {
        long now = System.currentTimeMillis();
        var plan = hotMigrate.beginZoneMigrate(
                504, 9, 1, "scene-a", "scene-b", "10.0.0.8", 9010,
                List.of(50401L, 50402L),
                Map.of(50401L, new float[]{1f, 0f, 2f}, 50402L, new float[]{3f, 0f, 4f}),
                List.of(new SceneHotMigrateService.EntitySnapshot(9_000_001L, 2, 5f, 0f, 5f, "怪", 10)),
                now);
        assertThat(plan.playerTickets()).hasSize(2);
        assertThat(hotMigrate.markDraining(plan.planId()).status())
                .isEqualTo(SceneHotMigrateService.MigrateStatus.DRAINING);
        assertThat(hotMigrate.complete(plan.planId()).status())
                .isEqualTo(SceneHotMigrateService.MigrateStatus.COMPLETED);
        Map<String, Object> view = hotMigrate.toView(plan);
        assertThat(view.get("monsterCount")).isEqualTo(1);
    }

    @Test
    public void lootBitmap_worldSharedClaimOnce_thenFlush() {
        openWorld.resources().registerPoint(new RespawnPoint(
                "bitmap-ore", 1, 1, RespawnPoint.RespawnKind.GATHER,
                0, 0, 0, 1, 300, false, RespawnPoint.SyncMode.WORLD_SHARED));
        Map<String, Object> first = openWorld.collectWithOwnership(
                "bitmap-ore", 11L, 0L, Set.of(), LootOwnershipPolicy.SyncMode.WORLD_SHARED);
        assertThat(first.get("ok")).isEqualTo(true);
        assertThat(first.get("bitmapScope")).isNotNull();

        Map<String, Object> second = openWorld.collectWithOwnership(
                "bitmap-ore", 12L, 0L, Set.of(), LootOwnershipPolicy.SyncMode.WORLD_SHARED);
        assertThat(second.get("ok")).isEqualTo(false);
        assertThat(String.valueOf(second.get("error"))).isEqualTo("already_claimed");

        openWorld.flushLootBitmap();
        assertThat(openWorld.lootBitmap().dirtySize()).isZero();
        assertThat(openWorld.status().get("lootBitmap")).isInstanceOf(Map.class);
    }

    @Test
    public void bossLeader_onlyLeaderCanRespawn() {
        BossRespawnTimer boss = openWorld.bossRespawn();
        long now = System.currentTimeMillis();
        assertThat(boss.markKilled("boss-flow", 1L, 1, 1, now).get("ok")).isEqualTo(true);
        // CD 结束后 READY
        long afterCd = now + 2_000L;
        assertThat(boss.status("boss-flow", afterCd)).isEqualTo(BossRespawnTimer.BossStatus.READY);
        assertThat(boss.tryRespawnAsLeader("boss-flow", afterCd)).isTrue();
        assertThat(boss.isLeader("boss-flow")).isTrue();
        assertThat(boss.currentLeader("boss-flow")).isEqualTo("scene-flow-a");
    }

    @Test
    public void crossDungeonPrep_readyUnder500ms_andLightweightRoomPeers() {
        long now = 8_000L;
        var handle = precreator.prepareCrossDungeon(
                9107, 1, 4, List.of(71L, 72L, 73L, 74L), now);
        assertThat(handle.status()).isEqualTo(SceneBackgroundPrecreator.PrepStatus.READY);
        Map<String, Object> handoff = precreator.toClientHandoff(handle);
        assertThat(handoff.get("preloadReady")).isEqualTo(true);
        assertThat((Long) handoff.get("readyLatencyMs")).isLessThanOrEqualTo(500L);
        assertThat(handle.tickets()).hasSize(4);

        var consumed = precreator.consume(handle.prepId(), now + 100L);
        assertThat(consumed.status()).isEqualTo(SceneBackgroundPrecreator.PrepStatus.CONSUMED);

        LightweightBattleRoom.Room room = battleRooms.get(handle.roomId());
        assertThat(room).isNotNull();
        assertThat(room.status()).isEqualTo(LightweightBattleRoom.RoomStatus.ACTIVE);
        List<Long> peers = battleRooms.peerBroadcastTargets(handle.roomId(), 71L);
        assertThat(peers).containsExactlyInAnyOrder(72L, 73L, 74L);
    }

    @Test
    public void k8sAllocator_scaleHint_andPoolHeartbeat() {
        K8sSceneAllocator allocator = precreator.allocator();
        long now = System.currentTimeMillis();
        allocator.registerOrHeartbeat(new K8sSceneAllocator.SceneNode(
                "busy", "10.0.0.1", 8082, 900, 1024, 95, 100, true, now), now);
        allocator.registerOrHeartbeat(new K8sSceneAllocator.SceneNode(
                "idle", "10.0.0.2", 8082, 100, 1024, 5, 100, true, now), now);
        Map<String, Object> alloc = allocator.allocateOnBestNode(9201, 8, 20_000L, now);
        assertThat(alloc.get("ok")).isEqualTo(true);
        assertThat(alloc.get("nodeId")).isEqualTo("idle");
        String instanceId = String.valueOf(alloc.get("instanceId"));
        assertThat(allocator.pool().heartbeat(instanceId)).isNotNull();
        assertThat(allocator.shouldHintScaleOut(allocator.listNodes().stream()
                .filter(n -> "busy".equals(n.nodeId())).findFirst().orElseThrow())).isTrue();
    }

    @Test
    public void aoiFrequencyTier_andBatchCoalesceIntegrated() throws Exception {
        int sceneId = 505;
        stubMap(sceneId, 500, 500, 2);
        stubPlayer(50501L, "近距");
        stubPlayer(50502L, "远距");
        enter(50501L, sceneId, 1, 100, 100);
        enter(50502L, sceneId, 1, 160, 100); // 距离约 60 → FAR_2HZ（三级频次）

        AoiBroadcastStrategy strategy = new AoiBroadcastStrategy();
        strategy.configureDistanceTiers(25f, 80f);
        assertThat(strategy.frequencyTier(10f)).isEqualTo(AoiBroadcastStrategy.FrequencyTier.NEAR_20HZ);
        assertThat(strategy.frequencyTier(60f)).isEqualTo(AoiBroadcastStrategy.FrequencyTier.FAR_2HZ);
        assertThat(strategy.allowAtTick(AoiBroadcastStrategy.FrequencyTier.FAR_2HZ, 1L)).isFalse();
        assertThat(strategy.allowAtTick(AoiBroadcastStrategy.FrequencyTier.FAR_2HZ, 10L)).isTrue();

        MoveScRsp move = MoveScRsp.parseFrom(scene.handleMove(50501L, MoveCsReq.newBuilder()
                .setTargetX(105f).setTargetY(0f).setTargetZ(100f)
                .setSpeed(30f).setTimestamp(System.currentTimeMillis()).build()).payload());
        assertThat(move.getRetcode()).isEqualTo(RetCode.OK);

        Map<String, Object> grid = scene.debugAoiGrid(sceneId, 1);
        assertThat((Integer) grid.get("playerCount")).isEqualTo(2);
        @SuppressWarnings("unchecked")
        Map<String, Object> batcher = (Map<String, Object>) grid.get("aoiBatcher");
        assertThat(batcher).containsKeys("batchWindowMs", "flushedTicks");
    }

    private void enter(long playerId, int sceneId, int lineId, float x, float z) throws Exception {
        EnterSceneScRsp rsp = EnterSceneScRsp.parseFrom(scene.handleEnterScene(playerId,
                EnterSceneCsReq.newBuilder().setSceneId(sceneId).setLineId(lineId)
                        .setPosX(x).setPosY(0).setPosZ(z).build()).payload());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.OK);
    }

    private void stubMap(int sceneId, int width, int height, int defaultLines) {
        MapConfig map = new MapConfig();
        map.setId(sceneId);
        map.setWidth(width);
        map.setHeight(height);
        map.setDefaultLines(defaultLines);
        map.setRecommendLevel(10);
        when(configQueryService.findMapById(sceneId)).thenReturn(map);
        when(configQueryService.listMonstersForMap(sceneId)).thenReturn(Collections.emptyList());
    }

    private void stubPlayer(long playerId, String name) {
        Player p = new Player();
        p.setId(playerId);
        p.setName(name);
        p.setLevel(20);
        when(playerCachePort.findById(playerId)).thenReturn(p);
    }
}
