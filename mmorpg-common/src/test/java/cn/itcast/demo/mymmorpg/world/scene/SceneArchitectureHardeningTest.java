package cn.itcast.demo.mymmorpg.world.scene;

import cn.itcast.demo.mymmorpg.aoi.AoiUpdateBatcher;
import cn.itcast.demo.mymmorpg.sync.NavMeshPathValidator;
import cn.itcast.demo.mymmorpg.world.boss.BossRespawnTimer;
import cn.itcast.demo.mymmorpg.world.line.LineShardScheduler;
import cn.itcast.demo.mymmorpg.world.loot.LootOwnershipBitmapStore;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 场景弹性 / AOI 批处理 / 分线软硬顶 / Boss 选主 / 采集 Bitmap / 后台预创建。
 */
public class SceneArchitectureHardeningTest {

    @Test
    public void k8sAllocator_picksLeastLoadedNode() {
        K8sSceneAllocator allocator = new K8sSceneAllocator();
        long now = 1_000_000L;
        allocator.registerOrHeartbeat(new K8sSceneAllocator.SceneNode(
                "n1", "10.0.0.1", 8082, 800, 1024, 90, 100, true, now), now);
        allocator.registerOrHeartbeat(new K8sSceneAllocator.SceneNode(
                "n2", "10.0.0.2", 8082, 100, 1024, 10, 100, true, now), now);
        Map<String, Object> alloc = allocator.allocateOnBestNode(9101, 4, 30_000L, now);
        assertThat(alloc.get("ok")).isEqualTo(true);
        assertThat(alloc.get("nodeId")).isEqualTo("n2");
        assertThat(alloc.get("host")).isEqualTo("10.0.0.2");
    }

    @Test
    public void hotMigrate_issuesSeamlessTickets() {
        SceneHotMigrateService svc = new SceneHotMigrateService();
        var plan = svc.beginZoneMigrate(
                1, 7, 1, "scene-a", "scene-b", "10.0.0.9", 9010,
                List.of(11L, 12L),
                Map.of(11L, new float[]{1f, 0f, 2f}, 12L, new float[]{3f, 0f, 4f}),
                List.of(), System.currentTimeMillis());
        assertThat(plan.status()).isEqualTo(SceneHotMigrateService.MigrateStatus.TICKETS_ISSUED);
        assertThat(plan.playerTickets()).containsKeys(11L, 12L);
        assertThat(svc.markDraining(plan.planId()).status())
                .isEqualTo(SceneHotMigrateService.MigrateStatus.DRAINING);
        assertThat(svc.complete(plan.planId()).status())
                .isEqualTo(SceneHotMigrateService.MigrateStatus.COMPLETED);
    }

    @Test
    public void bossLeaderElection_onlyLeaderRespawns() {
        BossRespawnTimer a = new BossRespawnTimer();
        a.configureNode("node-a", 10_000L);
        long now = System.currentTimeMillis();
        assertThat(a.tryBecomeLeader("world_boss_1", now)).isTrue();
        assertThat(a.isLeader("world_boss_1")).isTrue();
        assertThat(a.tryRespawnAsLeader("world_boss_1", now)).isTrue();
        // 无 Redis 时选主仅进程内有效；同节点二次 tryBecomeLeader 应续租成功
        assertThat(a.tryBecomeLeader("world_boss_1", now + 1)).isTrue();
        assertThat(a.currentLeader("world_boss_1")).isEqualTo("node-a");
    }

    @Test
    public void aoiBatcher_coalescesAndTiersByDistance() {
        AoiUpdateBatcher batcher = new AoiUpdateBatcher();
        batcher.configure(100L, 25f, 80f);
        batcher.enqueue(1L, 0f, 0f, 0f, 100, 3, 1_000L);
        batcher.enqueue(1L, 1f, 0f, 1f, 100, 3, 1_050L);
        assertThat(batcher.pendingSize()).isEqualTo(1);
        assertThat(batcher.stats().get("coalescedUpdates")).isEqualTo(1L);
        assertThat(batcher.tierOf(10f)).isEqualTo(AoiUpdateBatcher.SyncTier.NEAR_FULL);
        assertThat(batcher.tierOf(50f)).isEqualTo(AoiUpdateBatcher.SyncTier.FAR_REDUCED);
        assertThat(batcher.tierOf(120f)).isEqualTo(AoiUpdateBatcher.SyncTier.OUT_OF_VIEW_STATE);
    }

    @Test
    public void navMesh_rejectsBlockedPath() {
        NavMeshPathValidator nav = new NavMeshPathValidator();
        nav.ensureMesh(1, 20, 20, 1f);
        nav.setBlocked(1, 5, 0, true);
        assertThat(nav.validate(1, 0f, 0f, 10f, 0f).reachable()).isFalse();
        assertThat(nav.validate(1, 0f, 0f, 2f, 2f).reachable()).isTrue();
    }

    @Test
    public void lineSoftHardCap_andPartyPull() {
        LineShardScheduler lines = new LineShardScheduler();
        lines.configure(2, 3, "buff_drop_5");
        lines.join(1, 1, 101L);
        lines.join(1, 1, 102L);
        assertThat(lines.admit(1, 1, 5).result()).isEqualTo(LineShardScheduler.AdmitResult.SOFT_CAP_BUFF);
        lines.join(1, 1, 103L);
        assertThat(lines.admit(1, 1, 5).result()).isEqualTo(LineShardScheduler.AdmitResult.HARD_CAP_REJECT);
        var tickets = lines.pullPartyToLeaderLine(
                101L, 1, 1, 10f, 0f, 20f, List.of(201L, 202L), Map.of(201L, 2, 202L, 1));
        assertThat(tickets).hasSize(1);
        assertThat(tickets.get(0).memberId()).isEqualTo(201L);
        assertThat(tickets.get(0).sessionTicket()).isNotBlank();
    }

    @Test
    public void lootBitmap_claimThenDirtyFlush() {
        LootOwnershipBitmapStore store = new LootOwnershipBitmapStore();
        String scope = LootOwnershipBitmapStore.scopeKey(1, 1, "ore");
        assertThat(store.tryClaim(scope, 7, 9L, 1_000L).get("ok")).isEqualTo(true);
        assertThat(store.tryClaim(scope, 7, 10L, 1_001L).get("ok")).isEqualTo(false);
        assertThat(store.drainDirty(10)).hasSize(1);
        assertThat(store.dirtySize()).isZero();
    }

    @Test
    public void backgroundPrecreator_readyUnderBudget() {
        SceneBackgroundPrecreator prep = new SceneBackgroundPrecreator();
        prep.configure(30_000L, 500L);
        var handle = prep.prepareCrossDungeon(9101, 1, 4, List.of(1L, 2L, 3L, 4L), 5_000L);
        assertThat(handle.status()).isEqualTo(SceneBackgroundPrecreator.PrepStatus.READY);
        Map<String, Object> handoff = prep.toClientHandoff(handle);
        assertThat(handoff.get("preloadReady")).isEqualTo(true);
        assertThat((Long) handoff.get("readyLatencyMs")).isLessThanOrEqualTo(500L);
        assertThat(handle.tickets()).hasSize(4);
    }
}
