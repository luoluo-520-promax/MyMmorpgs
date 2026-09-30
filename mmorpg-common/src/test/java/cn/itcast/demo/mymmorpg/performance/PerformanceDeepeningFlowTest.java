package cn.itcast.demo.mymmorpg.performance;

import cn.itcast.demo.mymmorpg.cache.LocalPositionCache;
import cn.itcast.demo.mymmorpg.ecs.SceneComponentStore;
import cn.itcast.demo.mymmorpg.ecs.SceneTickEngine;
import cn.itcast.demo.mymmorpg.metrics.PerformanceHardeningMetrics;
import cn.itcast.demo.mymmorpg.persistence.ColdHotDataRouter;
import cn.itcast.demo.mymmorpg.persistence.DeduplicateFilter;
import cn.itcast.demo.mymmorpg.persistence.PlayerWritePipeline;
import cn.itcast.demo.mymmorpg.physics.AsyncPhysicsThreadPool;
import cn.itcast.demo.mymmorpg.physics.LitePhysicsEngine;
import cn.itcast.demo.mymmorpg.pool.MoveCmdPool;
import cn.itcast.demo.mymmorpg.sync.DynamicFrequencyService;
import cn.itcast.demo.mymmorpg.sync.MergedMoveAckService;
import cn.itcast.demo.mymmorpg.sync.MoveDeltaEncoder;
import cn.itcast.demo.mymmorpg.sync.SceneMoveCmd;
import cn.itcast.demo.mymmorpg.world.puzzle.PhysicsAuthorityService;
import cn.itcast.demo.mymmorpg.world.traverse.GrapplePhysicsService;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P15 性能深化全链路回归：ECS Tick → 动态同步 LOD → 位级增量 → L1 缓存 → 写去重 →
 * 冷热路由 → LitePhysics 钩锁 → 异步物理审计。
 */
public class PerformanceDeepeningFlowTest {

  @Test
  public void fullMoveSyncPipeline_reducesBandwidthAndAllocations() {
    SceneComponentStore store = new SceneComponentStore();
    SceneTickEngine tickEngine = new SceneTickEngine();
    DynamicFrequencyService freq = new DynamicFrequencyService();
    MoveDeltaEncoder deltaEncoder = new MoveDeltaEncoder();
    MergedMoveAckService mergedAck = new MergedMoveAckService();
    LocalPositionCache l1 = new LocalPositionCache();
    MoveCmdPool pool = new MoveCmdPool();
    long now = System.currentTimeMillis();

    long playerId = 88001L;
    store.allocate(playerId, 100f, 0f, 200f, 1000);
    store.setVelocity(playerId, 10f, 0f, 5f);

    SceneMoveCmd cmd1 = SceneMoveCmd.walk(101f, 0f, 201f, 12f, now);
    var delta1 = deltaEncoder.encode(playerId, cmd1);
    assertThat(delta1.changedMask()).isGreaterThan(0);

    SceneMoveCmd cmd2 = SceneMoveCmd.walk(102f, 0f, 202f, 12f, now + 16);
    var delta2 = deltaEncoder.encode(playerId, cmd2);
    assertThat(delta2.changedMask()).isGreaterThan(0);
    assertThat(deltaEncoder.estimateBytesSaved(delta2)).isGreaterThan(0);

    l1.put(playerId, 102f, 0f, 202f, now);
    assertThat(l1.get(playerId).x()).isEqualTo(102f);

    MoveCmdPool.Slot slot = pool.acquireWalk(102f, 0f, 202f, 12f, now);
    pool.release(slot);

    assertThat(freq.shouldSync(9001L, 8f, 12f, now)).isTrue();
    assertThat(freq.shouldSync(9001L, 8f, 12f, now + 20)).isFalse();
    assertThat(freq.shouldSync(9001L, 120f, 0f, now + 500)).isTrue();

    mergedAck.receiveAck(playerId, 1L, 102f, 0f, 202f, now);
        mergedAck.receiveAck(playerId, 2L, 103f, 0f, 203f, now + 16);
        Map<String, Object> ack = mergedAck.receiveAck(playerId, 3L, 104f, 0f, 204f, now + 32);
        assertThat(ack.get("merged")).isEqualTo(true);

        store.setVelocity(playerId, 10f, 0f, 5f);
        Map<String, Object> tick = tickEngine.tick(store, now);
        assertThat(tick.get("processed")).isEqualTo(1L);
        assertThat(store.getX(playerId)).isGreaterThan(100f);
  }

  @Test
  public void persistenceColdHotAndDedupe_pipeline() {
    PlayerWritePipeline pipeline = new PlayerWritePipeline();
    pipeline.configure(10, 500L);
    DeduplicateFilter dedupe = new DeduplicateFilter();
    ColdHotDataRouter router = new ColdHotDataRouter();
    long now = System.currentTimeMillis();

    List<String> redisWrites = new ArrayList<>();
    List<String> mysqlWrites = new ArrayList<>();

    router.write(ColdHotDataRouter.DataKind.SCENE_POSITION, 1L, "pos",
            now, redisWrites::add, mysqlWrites::add);
    router.write(ColdHotDataRouter.DataKind.ABYSS_STARS, 1L, "stars",
            now, redisWrites::add, mysqlWrites::add);

    assertThat(redisWrites).containsExactly("pos");
    assertThat(mysqlWrites).containsExactly("stars");

    pipeline.enqueue(1L, "HP", Map.of("hp", 100));
    if (dedupe.shouldWrite(1L, "hp", 100, now + 10)) {
      pipeline.enqueue(1L, "HP", Map.of("hp", 100));
    }
    pipeline.enqueue(1L, "HP", Map.of("hp", 90));

    Map<String, Object> flushed = pipeline.flush(ops -> assertThat(ops).hasSize(2));
    assertThat(flushed.get("flushed")).isEqualTo(2);
    assertThat(pipeline.stats().get("dedupeSkipped")).isNotNull();
  }

  @Test
  public void litePhysicsAndAsyncAuthority_endToEnd() throws Exception {
    LitePhysicsEngine lite = new LitePhysicsEngine();
    lite.loadHeightMap(64, 64, 1f, 0f, 0f, new float[64 * 64]);

    GrapplePhysicsService grapple = new GrapplePhysicsService();
    Map<String, Object> landing = grapple.validateGrappleLanding(
            1L, 5f, 0f, 5f, 5f, 0f, 5f, 8f);
    assertThat(landing.get("ok")).isEqualTo(true);
    assertThat(landing.get("clientLocalSim")).isEqualTo(true);

    Map<String, Object> far = grapple.validateGrappleLanding(
            1L, 50f, 0f, 50f, 5f, 0f, 5f, 5f);
    assertThat(far.get("ok")).isEqualTo(false);

    PhysicsAuthorityService auth = new PhysicsAuthorityService();
    long now = System.currentTimeMillis();
    auth.recordExpected(42L, new PhysicsAuthorityService.ExpectedPhysics(
            3f, 0f, 1f, 1f, 0f, 1f, 0f, now));
    String hash = PhysicsAuthorityService.computeHash(3f, 0f, 1f, 1f, 0f, 1f, 0f);

    CompletableFuture<Map<String, Object>> future = auth.validateHashAsyncFuture(
            42L, hash, 3f, 0f, 1f, 1f, 0f, 1f, 0f, false);
    Map<String, Object> asyncResult = future.get(3, TimeUnit.SECONDS);
    assertThat(asyncResult.get("async")).isEqualTo(true);
    assertThat(asyncResult.get("ok")).isEqualTo(true);

    AsyncPhysicsThreadPool pool = new AsyncPhysicsThreadPool();
    assertThat(pool.stats().get("submitted")).isNotNull();
  }

  @Test
  public void performanceMetricsSnapshot_containsP15Sections() {
    PerformanceHardeningMetrics metrics = new PerformanceHardeningMetrics(
            null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null);
    Map<String, Object> snap = metrics.snapshot();
    assertThat(snap).containsKeys("p15Sync", "p15Persistence", "p15Physics");
    assertThat(snap.get("tick")).isNotNull();
  }
}
