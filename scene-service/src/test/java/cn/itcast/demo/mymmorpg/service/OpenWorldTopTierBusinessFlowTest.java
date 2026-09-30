package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.anticheat.AntiCheatService;
import cn.itcast.demo.mymmorpg.center.MigrationTicketService;
import cn.itcast.demo.mymmorpg.center.SceneMigrationPlan;
import cn.itcast.demo.mymmorpg.config.SceneRuntimeProperties;
import cn.itcast.demo.mymmorpg.entity.MapConfig;
import cn.itcast.demo.mymmorpg.entity.MonsterConfig;
import cn.itcast.demo.mymmorpg.entity.Player;
import cn.itcast.demo.mymmorpg.model.MonsterWaveSimpleFactory;
import cn.itcast.demo.mymmorpg.port.PlayerCachePort;
import cn.itcast.demo.mymmorpg.port.PlayerNotificationPort;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.protocol.protobuf.EnterSceneCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.EnterSceneScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ResumeSceneCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ResumeSceneScRsp;
import cn.itcast.demo.mymmorpg.support.ScenePolicy;
import cn.itcast.demo.mymmorpg.world.boss.BossRespawnTimer;
import cn.itcast.demo.mymmorpg.world.lock.PartyEntityOwnership;
import cn.itcast.demo.mymmorpg.world.loot.LootOwnershipPolicy;
import cn.itcast.demo.mymmorpg.world.portal.PortalConfig;
import cn.itcast.demo.mymmorpg.world.resource.RespawnPoint;
import cn.itcast.demo.mymmorpg.world.state.HostWorldContext;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Duration;
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
 * 顶尖标准新业务流程：WorldState/WorldLevel/Boss锁/联机物权/Portal预加载/重连保护。
 */
public class OpenWorldTopTierBusinessFlowTest {

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

    @BeforeMethod
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        MigrationTicketService tickets = new MigrationTicketService();
        runtime = new SceneRuntimeProperties();
        runtime.setReconnectGraceMs(60_000L);
        runtime.setReconnectProtectionMs(30_000L);
        SceneReconnectStore reconnectStore = new SceneReconnectStore(runtime);
        when(centerSceneRouter.isSceneReachable(anyInt())).thenReturn(true);
        when(centerSceneRouter.planMigration(anyInt())).thenAnswer(inv ->
                SceneMigrationPlan.localPlan(inv.getArgument(0), "local", "127.0.0.1", 8089));
        lenient().when(scenePolicy.allowEnterScene(anyInt(), anyLong())).thenReturn(true);
        scene = new SceneActorService(
                configQueryService, playerCachePort, scenePolicy, playerNotificationPort,
                sceneEventPublisher, new MonsterWaveSimpleFactory(), centerSceneRouter,
                tickets, reconnectStore, runtime, new AntiCheatService(), null);
        openWorld = new OpenWorldRuntimeService();
    }

    @AfterMethod
    public void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    public void hostWorld_worldLevel_puzzleBit_andPartyLootFlow() {
        openWorld.worldState().bindHostWorld(new HostWorldContext(
                100L, 1, 4, 30, List.of(100L, 101L, 102L), true));
        assertThat(openWorld.worldState().hostWorldOf(100L).effectiveWorldLevelFor(101L, 9)).isEqualTo(4);

        openWorld.worldLevel().setWorldLevel(1, 100L, 5);
        var scaled = openWorld.worldLevel().scaleForSpawn(1, 100L, 2, 50, 500);
        assertThat(scaled.scaledAtk()).isGreaterThan(50);
        assertThat(scaled.scaledHp()).isGreaterThan(500);

        Map<String, Object> bit = openWorld.worldState().setPuzzleBit(1, 100L, 7, true);
        assertThat(bit.get("ok")).isEqualTo(true);
        assertThat(openWorld.worldState().getPuzzleBit(1, 100L, 7)).isTrue();
        // 公共世界与房主世界隔离
        assertThat(openWorld.worldState().getPuzzleBit(1, 0L, 7)).isFalse();

        openWorld.resources().registerPoint(new RespawnPoint(
                "party-chest-1", 1, 1, RespawnPoint.RespawnKind.CHEST,
                1, 0, 1, 3001, 60, false, RespawnPoint.SyncMode.WORLD_SHARED));
        Map<String, Object> loot = openWorld.collectWithOwnership(
                "party-chest-1", 101L, 100L, Set.of(100L, 101L, 102L),
                LootOwnershipPolicy.SyncMode.PARTY_SHARED);
        assertThat(loot.get("ok")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> decision = (Map<String, Object>) loot.get("loot");
        assertThat(decision.get("mode")).isEqualTo("PARTY_SHARED");
        @SuppressWarnings("unchecked")
        List<Long> eligible = (List<Long>) decision.get("pickupEligibleIds");
        assertThat(eligible).contains(100L, 102L);
    }

    @Test
    public void worldSharedVsPerPlayerCollect_andBossGlobalLock() {
        openWorld.resources().registerPoint(new RespawnPoint(
                "shared-ore", 1, 1, RespawnPoint.RespawnKind.GATHER,
                0, 0, 0, 1, 300, false, RespawnPoint.SyncMode.WORLD_SHARED));
        openWorld.resources().registerPoint(new RespawnPoint(
                "solo-ore", 1, 1, RespawnPoint.RespawnKind.GATHER,
                0, 0, 0, 2, 300, false, RespawnPoint.SyncMode.PER_PLAYER));
        long now = System.currentTimeMillis();
        assertThat(openWorld.resources().collect("shared-ore", 1L, now).get("clientRefresh"))
                .isEqualTo("broadcast_aoi_resource_state");
        assertThat(openWorld.resources().collect("shared-ore", 2L, now).get("ok")).isEqualTo(false);
        assertThat(openWorld.resources().collect("solo-ore", 1L, now).get("ok")).isEqualTo(true);
        assertThat(openWorld.resources().collect("solo-ore", 2L, now).get("ok")).isEqualTo(true);

        Map<String, Object> kill1 = openWorld.bossRespawn().markKilled("wb-flow", 1L, 1, 120, now);
        Map<String, Object> kill2 = openWorld.bossRespawn().markKilled("wb-flow", 2L, 2, 120, now);
        assertThat(kill1.get("ok")).isEqualTo(true);
        assertThat(kill2.get("ok")).isEqualTo(false);
        assertThat(openWorld.bossRespawn().canSpawn("wb-flow", now)).isFalse();
        assertThat(openWorld.bossRespawn().status("wb-flow", now))
                .isEqualTo(BossRespawnTimer.BossStatus.DEAD_COOLDOWN);
    }

    @Test
    public void portalPreload_thenConsume_andPartyThreatOwnership() {
        openWorld.portals().register(new PortalConfig(
                "portal-flow", 1, 2, 1, 100f, 0f, 100f, 8f, 60f, 5f, 0f, 5f, "scene-liyue"));
        Map<String, Object> lease = openWorld.portals().onPlayerMove(
                77L, 1, 100f, 0f, 100f, 2f, 0f, 45f, System.currentTimeMillis());
        assertThat(lease.get("ok")).isEqualTo(true);
        assertThat(lease.get("status")).isEqualTo("READY");
        assertThat(String.valueOf(lease.get("sessionTicket"))).isNotBlank();
        String leaseId = String.valueOf(lease.get("leaseId"));
        Map<String, Object> consumed = openWorld.portals().consume(77L, leaseId, System.currentTimeMillis());
        assertThat(consumed.get("status")).isEqualTo("CONSUMED");

        Map<String, Object> claim = openWorld.partyOwnership().tryClaim(
                1, 9001L, 2L, 1L, Set.of(1L, 2L),
                PartyEntityOwnership.ThreatPriority.HOST, Duration.ofSeconds(30));
        assertThat(claim.get("ok")).isEqualTo(true);
        // 房主仇恨优先
        assertThat(openWorld.partyOwnership().resolveThreatTarget(
                1, 9001L, 2L, Map.of(2L, 999L, 1L, 1L))).isEqualTo(1L);
        // 同队可共享，异队失败
        Map<String, Object> share = openWorld.partyOwnership().tryClaim(
                1, 9001L, 1L, 1L, Set.of(1L, 2L),
                PartyEntityOwnership.ThreatPriority.HOST, Duration.ofSeconds(30));
        assertThat(share.get("ok")).isEqualTo(true);
        Map<String, Object> other = openWorld.partyOwnership().tryClaim(
                1, 9001L, 99L, 1L, Set.of(99L),
                PartyEntityOwnership.ThreatPriority.ATTACKER, Duration.ofSeconds(30));
        assertThat(other.get("ok")).isEqualTo(false);
    }

    @Test
    public void resumeScene_grantsReconnectProtection() throws Exception {
        int sceneId = 31;
        stubMap(sceneId, 3101L, "重连者");
        when(configQueryService.listMonstersForMap(sceneId)).thenReturn(Collections.emptyList());

        EnterSceneScRsp enter = EnterSceneScRsp.parseFrom(scene.handleEnterScene(3101L,
                EnterSceneCsReq.newBuilder().setSceneId(sceneId).setLineId(1)
                        .setPosX(40).setPosY(0).setPosZ(40).build()).payload());
        assertThat(enter.getRetcode()).isEqualTo(RetCode.OK);

        String token = scene.onPlayerDisconnect(3101L);
        assertThat(token).isNotBlank();
        assertThat(scene.isReconnectProtected(3101L)).isFalse();

        ResumeSceneScRsp resume = ResumeSceneScRsp.parseFrom(scene.handleResumeScene(3101L,
                ResumeSceneCsReq.newBuilder().setResumeToken(token).build()).payload());
        assertThat(resume.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(scene.isReconnectProtected(3101L)).isTrue();
        assertThat(scene.reconnectProtection().toView(3101L, System.currentTimeMillis()).get("active"))
                .isEqualTo(true);
    }

    @Test
    public void statusIncludesWorldStateLevelAndBusinessMetrics() {
        Map<String, Object> status = openWorld.status();
        assertThat(status.get("ok")).isEqualTo(true);
        assertThat(status.get("worldState")).isInstanceOf(Map.class);
        assertThat(status.get("worldLevel")).isInstanceOf(Map.class);
        assertThat(status.get("businessMetrics")).isInstanceOf(Map.class);
        assertThat(status.get("lootBitmap")).isInstanceOf(Map.class);
        openWorld.metrics().incPortalPreload();
        assertThat(((Number) openWorld.metrics().snapshot().get("portalPreloads")).longValue())
                .isGreaterThanOrEqualTo(1L);
    }

    private void stubMap(int sceneId, long playerId, String name) {
        MapConfig map = new MapConfig();
        map.setId(sceneId);
        map.setWidth(200);
        map.setHeight(200);
        map.setDefaultLines(2);
        map.setRecommendLevel(3);
        when(configQueryService.findMapById(sceneId)).thenReturn(map);
        stubPlayer(playerId, name);
    }

    private void stubPlayer(long playerId, String name) {
        Player p = new Player();
        p.setId(playerId);
        p.setName(name);
        p.setLevel(20);
        when(playerCachePort.findById(playerId)).thenReturn(p);
    }
}
