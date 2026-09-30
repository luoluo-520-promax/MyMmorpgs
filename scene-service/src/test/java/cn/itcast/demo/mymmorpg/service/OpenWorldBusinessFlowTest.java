package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.anticheat.AntiCheatService;
import cn.itcast.demo.mymmorpg.center.MigrationTicketService;
import cn.itcast.demo.mymmorpg.center.SceneMigrationPlan;
import cn.itcast.demo.mymmorpg.config.SceneRuntimeProperties;
import cn.itcast.demo.mymmorpg.entity.MapConfig;
import cn.itcast.demo.mymmorpg.entity.MonsterConfig;
import cn.itcast.demo.mymmorpg.entity.Player;
import cn.itcast.demo.mymmorpg.gm.GmCommandDispatcher;
import cn.itcast.demo.mymmorpg.model.MonsterWaveSimpleFactory;
import cn.itcast.demo.mymmorpg.port.PlayerCachePort;
import cn.itcast.demo.mymmorpg.port.PlayerNotificationPort;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.protocol.protobuf.EnterSceneCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.EnterSceneScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetNearbyEntitiesCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetNearbyEntitiesScRsp;
import cn.itcast.demo.mymmorpg.support.ScenePolicy;
import cn.itcast.demo.mymmorpg.world.lock.DistributedEntityLock;
import cn.itcast.demo.mymmorpg.world.resource.RespawnPoint;
import cn.itcast.demo.mymmorpg.world.time.WorldTimeService;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 大世界新增业务流：资源采集/机关、跨区握手、GM、天气、抢怪归属锁、悬崖 AOI。
 */
public class OpenWorldBusinessFlowTest {

    @Mock private ConfigQueryService configQueryService;
    @Mock private PlayerCachePort playerCachePort;
    @Mock private ScenePolicy scenePolicy;
    @Mock private PlayerNotificationPort playerNotificationPort;
    @Mock private SceneEventPublisher sceneEventPublisher;
    @Mock private CenterSceneRouter centerSceneRouter;

    private AutoCloseable mocks;
    private SceneActorService scene;
    private OpenWorldRuntimeService openWorld;

    @BeforeMethod
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        DistributedEntityLock entityLock = new DistributedEntityLock();
        MigrationTicketService tickets = new MigrationTicketService();
        SceneRuntimeProperties runtime = new SceneRuntimeProperties();
        SceneReconnectStore reconnectStore = new SceneReconnectStore(runtime);
        when(centerSceneRouter.isSceneReachable(anyInt())).thenReturn(true);
        when(centerSceneRouter.planMigration(anyInt())).thenAnswer(inv ->
                SceneMigrationPlan.localPlan(inv.getArgument(0), "local", "127.0.0.1", 8089));
        lenient().when(scenePolicy.allowEnterScene(anyInt(), anyLong())).thenReturn(true);
        scene = new SceneActorService(
                configQueryService, playerCachePort, scenePolicy, playerNotificationPort,
                sceneEventPublisher, new MonsterWaveSimpleFactory(), centerSceneRouter,
                tickets, reconnectStore, runtime, new AntiCheatService(), null);
        scene.setEntityLock(entityLock);
        openWorld = new OpenWorldRuntimeService();
    }

    @AfterMethod
    public void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    public void resourceCollect_mechanism_weather_gm_migrate_mixedLoad() {
        openWorld.resources().registerPoint(new RespawnPoint(
                "flow-ore-1", 1, 1, RespawnPoint.RespawnKind.GATHER, 10, 0, 10, 2001, 60, false));
        long now = System.currentTimeMillis();
        assertThat(openWorld.resources().collect("flow-ore-1", 1L, now).get("ok")).isEqualTo(true);
        assertThat(openWorld.resources().collect("flow-ore-1", 2L, now + 1).get("ok")).isEqualTo(false);

        var act = openWorld.resources().activateMechanism("puzzle-1", false, 1000, now);
        assertThat(act.ok()).isTrue();
        var done = openWorld.resources().completeMechanism("puzzle-1", 1000, now);
        assertThat(done.ok()).isTrue();

        openWorld.time().forceWeather(WorldTimeService.Weather.THUNDER);
        assertThat(openWorld.time().toView(now).get("weather")).isEqualTo("THUNDER");

        Map<String, Object> gm = openWorld.gm().dispatch(
                "spawn_monster", GmCommandDispatcher.Level.ADMIN,
                Map.of("templateId", 101, "x", 1, "z", 2));
        assertThat(gm.get("ok")).isEqualTo(true);

        var lookup = openWorld.routingTable().lookup(1, 900f, 50f, 100, "scene-local");
        Map<String, Object> prep = openWorld.handshake().prepare(
                88L, "scene-local", lookup, 1, 1, 900f, 0f, 50f, 1f, 0f, 90f);
        assertThat(prep.get("ok")).isEqualTo(true);
        String migrationId = String.valueOf(prep.get("migrationId"));
        assertThat(openWorld.handshake().commit(migrationId).get("phase")).isEqualTo("COMMITTED");

        Map<String, Object> load = openWorld.mixedLoadBenchmark(30, 20, 20, 50);
        assertThat(load.get("ok")).isEqualTo(true);
        assertThat(((Number) load.get("gatherOk")).longValue()).isGreaterThan(0L);
        assertThat(openWorld.status().get("ok")).isEqualTo(true);
    }

    @Test
    public void monsterOwnershipLock_secondPlayerCannotAcquire() throws Exception {
        int sceneId = 21;
        stubMapWithMonster(sceneId, 1001L, 1002L, 501);

        enter(1001L, sceneId, 50f, 0f, 50f);
        enter(1002L, sceneId, 55f, 0f, 55f);

        long monsterId = findAnyMonsterId(1001L);
        assertThat(monsterId).isGreaterThan(0L);

        var first = scene.markMonsterInCombat(1001L, monsterId);
        assertThat(first).isPresent();

        var second = scene.markMonsterInCombat(1002L, monsterId);
        assertThat(second).isEmpty();

        scene.releaseMonsterFromCombat(1001L, monsterId);
        var third = scene.markMonsterInCombat(1002L, monsterId);
        assertThat(third).isPresent();
    }

    @Test
    public void cliffAoi_playersAtLargeHeightDelta_notVisible() throws Exception {
        int sceneId = 22;
        stubMap(sceneId, 2001L, "悬崖下");
        stubPlayer(2002L, "悬崖上");
        when(configQueryService.listMonstersForMap(sceneId)).thenReturn(Collections.emptyList());

        EnterSceneScRsp low = EnterSceneScRsp.parseFrom(scene.handleEnterScene(2001L,
                EnterSceneCsReq.newBuilder().setSceneId(sceneId).setLineId(1)
                        .setPosX(50).setPosY(0).setPosZ(50).build()).payload());
        assertThat(low.getRetcode()).isEqualTo(RetCode.OK);

        EnterSceneScRsp high = EnterSceneScRsp.parseFrom(scene.handleEnterScene(2002L,
                EnterSceneCsReq.newBuilder().setSceneId(sceneId).setLineId(1)
                        .setPosX(52).setPosY(40).setPosZ(52).build()).payload());
        assertThat(high.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(high.getPosY()).isEqualTo(40f);

        GetNearbyEntitiesScRsp nearby = GetNearbyEntitiesScRsp.parseFrom(
                scene.handleGetNearby(2001L, GetNearbyEntitiesCsReq.newBuilder()
                        .setCenterX(50).setCenterY(0).setCenterZ(50).setRadius(100).build()).payload());
        assertThat(nearby.getRetcode()).isEqualTo(RetCode.OK);
        boolean seesHigh = nearby.getEntityListList().stream().anyMatch(e -> e.getEntityId() == 2002L);
        assertThat(seesHigh).as("cliff filter should hide ΔY=40 player").isFalse();

        // 同高度应可见
        stubPlayer(2003L, "同层");
        EnterSceneScRsp sameLevel = EnterSceneScRsp.parseFrom(scene.handleEnterScene(2003L,
                EnterSceneCsReq.newBuilder().setSceneId(sceneId).setLineId(1)
                        .setPosX(54).setPosY(1).setPosZ(54).build()).payload());
        assertThat(sameLevel.getRetcode()).isEqualTo(RetCode.OK);
        GetNearbyEntitiesScRsp nearby2 = GetNearbyEntitiesScRsp.parseFrom(
                scene.handleGetNearby(2001L, GetNearbyEntitiesCsReq.newBuilder()
                        .setCenterX(50).setCenterY(0).setCenterZ(50).setRadius(100).build()).payload());
        boolean seesSame = nearby2.getEntityListList().stream().anyMatch(e -> e.getEntityId() == 2003L);
        assertThat(seesSame).isTrue();
    }

    private long findAnyMonsterId(long playerId) throws Exception {
        GetNearbyEntitiesScRsp nearby = GetNearbyEntitiesScRsp.parseFrom(
                scene.handleGetNearby(playerId, GetNearbyEntitiesCsReq.newBuilder()
                        .setCenterX(500).setCenterY(0).setCenterZ(500).setRadius(2000).build())
                        .payload());
        return nearby.getEntityListList().stream()
                .filter(e -> e.getEntityType() == 2)
                .map(e -> e.getEntityId())
                .findFirst()
                .orElse(0L);
    }

    private void enter(long playerId, int sceneId, float x, float y, float z) throws Exception {
        EnterSceneScRsp rsp = EnterSceneScRsp.parseFrom(scene.handleEnterScene(playerId,
                EnterSceneCsReq.newBuilder().setSceneId(sceneId).setLineId(1)
                        .setPosX(x).setPosY(y).setPosZ(z).build()).payload());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.OK);
    }

    private void stubMap(int sceneId, long playerId, String name) {
        MapConfig map = new MapConfig();
        map.setId(sceneId);
        map.setWidth(1000);
        map.setHeight(1000);
        map.setDefaultLines(2);
        map.setAoiRadius(300);
        map.setGridSize(100);
        when(configQueryService.findMapById(sceneId)).thenReturn(map);
        when(configQueryService.listMonstersForMap(sceneId)).thenReturn(Collections.emptyList());
        stubPlayer(playerId, name);
    }

    private void stubMapWithMonster(int sceneId, long p1, long p2, int monsterTemplateId) {
        MapConfig map = new MapConfig();
        map.setId(sceneId);
        map.setWidth(1000);
        map.setHeight(1000);
        map.setDefaultLines(2);
        map.setAoiRadius(300);
        map.setGridSize(100);
        when(configQueryService.findMapById(sceneId)).thenReturn(map);
        MonsterConfig mc = new MonsterConfig();
        mc.setId(monsterTemplateId);
        mc.setName("野怪");
        mc.setLevel(10);
        mc.setModelId(1);
        mc.setRespawnSeconds(0);
        mc.setSpawnX(60f);
        mc.setSpawnZ(60f);
        when(configQueryService.listMonstersForMap(sceneId)).thenReturn(List.of(mc));
        when(configQueryService.findMonsterById(monsterTemplateId)).thenReturn(mc);
        stubPlayer(p1, "P1");
        stubPlayer(p2, "P2");
    }

    private void stubPlayer(long playerId, String name) {
        Player p = new Player();
        p.setId(playerId);
        p.setName(name);
        p.setLevel(10);
        when(playerCachePort.findById(playerId)).thenReturn(p);
    }
}
