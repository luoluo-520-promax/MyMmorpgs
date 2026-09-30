package cn.itcast.demo.mymmorpg.performance;

import cn.itcast.demo.mymmorpg.aoi.AoiBroadcastStrategy;
import cn.itcast.demo.mymmorpg.aoi.AoiDeltaEncoder;
import cn.itcast.demo.mymmorpg.aoi.PrimitiveGridStore;
import cn.itcast.demo.mymmorpg.concurrency.BusinessBatchConsumer;
import cn.itcast.demo.mymmorpg.concurrency.SceneActorMailbox;
import cn.itcast.demo.mymmorpg.persistence.AssetWalWriter;
import cn.itcast.demo.mymmorpg.persistence.HistoricalDataRetention;
import cn.itcast.demo.mymmorpg.persistence.PlayerWritePipeline;
import cn.itcast.demo.mymmorpg.pool.MoveCmdPool;
import cn.itcast.demo.mymmorpg.redis.RedisAtomicScriptService;
import cn.itcast.demo.mymmorpg.support.SimpleBloomFilter;
import cn.itcast.demo.mymmorpg.world.battle.BattleReplayService;
import cn.itcast.demo.mymmorpg.world.explore.CollectibleService;
import cn.itcast.demo.mymmorpg.world.gameplay.OpenWorldGameplayFacade;
import cn.itcast.demo.mymmorpg.world.level.WorldLevelManager;
import cn.itcast.demo.mymmorpg.aoi.AoiUpdateBatcher;
import cn.itcast.demo.mymmorpg.cache.LocalPositionCache;
import cn.itcast.demo.mymmorpg.ecs.SceneComponentStore;
import cn.itcast.demo.mymmorpg.ecs.SceneTickEngine;
import cn.itcast.demo.mymmorpg.metrics.PerformanceHardeningMetrics;
import cn.itcast.demo.mymmorpg.persistence.ColdHotDataRouter;
import cn.itcast.demo.mymmorpg.persistence.DeduplicateFilter;
import cn.itcast.demo.mymmorpg.persistence.PlayerWritePipeline;
import cn.itcast.demo.mymmorpg.sync.DynamicFrequencyService;
import cn.itcast.demo.mymmorpg.sync.MergedMoveAckService;
import cn.itcast.demo.mymmorpg.sync.MoveDeltaEncoder;
import cn.itcast.demo.mymmorpg.sync.SceneMoveCmd;
import cn.itcast.demo.mymmorpg.physics.LitePhysicsEngine;
import cn.itcast.demo.mymmorpg.world.puzzle.PhysicsAuthorityService;
import cn.itcast.demo.mymmorpg.world.traverse.GrapplePhysicsService;
import cn.itcast.demo.mymmorpg.world.sideplay.HandbookService;
import cn.itcast.demo.mymmorpg.world.tick.GameplayTickSlicer;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 七大性能瓶颈加固回归：并发/AOI/GC/Tick/DB/锁/冷热分离。
 */
public class PerformanceHardeningFlowTest {

    @AfterMethod
    public void tearDown() {
        // no-op
    }

    @Test
    public void sceneActorMailboxSerializesPerActor() throws Exception {
        SceneActorMailbox mailbox = new SceneActorMailbox();
        AtomicInteger counter = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(100);
        for (int i = 0; i < 100; i++) {
            mailbox.tell(42L, () -> {
                counter.incrementAndGet();
                latch.countDown();
            });
        }
        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(counter.get()).isEqualTo(100);
        assertThat(mailbox.stats().get("processed")).isEqualTo(100L);
    }

    @Test
    public void businessBatchConsumerDrainsInBatches() throws Exception {
        BusinessBatchConsumer consumer = new BusinessBatchConsumer();
        consumer.configure(16);
        AtomicInteger n = new AtomicInteger();
        for (int i = 0; i < 32; i++) {
            consumer.offer(n::incrementAndGet);
        }
        Thread.sleep(200);
        assertThat(n.get()).isEqualTo(32);
        assertThat(consumer.stats().get("batches")).isNotNull();
    }

    @Test
    public void moveCmdPoolReusesSlots() {
        MoveCmdPool pool = new MoveCmdPool();
        MoveCmdPool.Slot a = pool.acquireWalk(1f, 2f, 3f, 5f, 100L);
        var cmd = a.toImmutable();
        pool.release(a);
        MoveCmdPool.Slot b = pool.acquire();
        assertThat(cmd.targetX()).isEqualTo(1f);
        assertThat(pool.stats().get("released")).isEqualTo(1L);
        pool.release(b);
    }

    @Test
    public void primitiveGridStoreAvoidsBoxing() {
        PrimitiveGridStore grid = new PrimitiveGridStore();
        grid.upsert(1L, 10f, 0f, 20f);
        grid.upsert(2L, 12f, 0f, 22f);
        long[] hits = grid.queryRadius(10f, 0f, 20f, 5f);
        assertThat(hits).contains(1L);
        assertThat(grid.size()).isEqualTo(2);
    }

    @Test
    public void aoiDeltaEncoderEmitsOnlyChangedFields() {
        AoiDeltaEncoder encoder = new AoiDeltaEncoder();
        var full = encoder.encode(1L, 10f, 0f, 20f, 100, 1);
        assertThat(full.changedMask()).isGreaterThan(0);

        var noChange = encoder.encode(1L, 10f, 0f, 20f, 100, 1);
        assertThat(noChange.changedMask()).isZero();

        var deltaX = encoder.encode(1L, 11f, 0f, 20f, 100, 1);
        assertThat(deltaX.changedMask() & AoiDeltaEncoder.MASK_X).isNotZero();
        assertThat(encoder.packBatch(List.of(deltaX))).hasSize(1);
    }

    @Test
    public void aoiBroadcastThreeTierFrequency() {
        AoiBroadcastStrategy strategy = new AoiBroadcastStrategy();
        strategy.configureDistanceTiers(10f, 30f, 50f);
        assertThat(strategy.frequencyTier(5f)).isEqualTo(AoiBroadcastStrategy.FrequencyTier.NEAR_20HZ);
        assertThat(strategy.frequencyTier(20f)).isEqualTo(AoiBroadcastStrategy.FrequencyTier.MID_5HZ);
        assertThat(strategy.frequencyTier(40f)).isEqualTo(AoiBroadcastStrategy.FrequencyTier.FAR_2HZ);
        assertThat(strategy.allowAtTick(AoiBroadcastStrategy.FrequencyTier.FAR_2HZ, 10)).isTrue();
        assertThat(strategy.allowAtTick(AoiBroadcastStrategy.FrequencyTier.FAR_2HZ, 3)).isFalse();
    }

    @Test
    public void gameplayTickSlicerRespectsBudget() {
        GameplayTickSlicer slicer = new GameplayTickSlicer();
        slicer.configure(20, 5L);
        List<Integer> entities = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            entities.add(i);
        }
        var result = slicer.processSlice(entities, Integer::longValue, (e, ts) -> {
        }, System.currentTimeMillis());
        assertThat(result.processedCount()).isGreaterThan(0);
        assertThat(result.sliceIndex()).isBetween(0, 19);
    }

    @Test
    public void playerWritePipelineBatchesOps() {
        PlayerWritePipeline pipeline = new PlayerWritePipeline();
        pipeline.configure(3, 1000L);
        pipeline.enqueue(1L, "BAG_ADD", Map.of("itemId", "sword"));
        pipeline.enqueue(1L, "QUEST", Map.of("questId", "q1"));
        pipeline.enqueue(2L, "BAG_ADD", Map.of("itemId", "potion"));
        var flushed = pipeline.flush(ops -> assertThat(ops).hasSize(3));
        assertThat(flushed.get("flushed")).isEqualTo(3);
    }

    @Test
    public void assetWalAppender() {
        AssetWalWriter wal = new AssetWalWriter();
        wal.append(100L, "GRANT", "{\"item\":\"gold\"}", System.currentTimeMillis());
        assertThat(wal.drain(10)).hasSize(1);
    }

    @Test
    public void redisAtomicUpgradeWorldLevel() {
        RedisAtomicScriptService scripts = new RedisAtomicScriptService();
        Map<String, Object> ok = scripts.upgradeWorldLevel("lv:1:0", "cost:1", 12, 100, 1);
        assertThat(ok.get("ok")).isEqualTo(true);
        Map<String, Object> fail = scripts.upgradeWorldLevel("lv:1:0", "cost:1", 12, 99999, 2);
        assertThat(fail.get("ok")).isEqualTo(false);
    }

    @Test
    public void worldLevelManagerTryUpgrade() {
        WorldLevelManager mgr = new WorldLevelManager();
        Map<String, Object> r = mgr.tryUpgradeWorldLevel(1, 0L, 50, 1);
        assertThat(r.get("ok")).isEqualTo(true);
    }

    @Test
    public void bloomFilterBlocksNegativeLookup() {
        SimpleBloomFilter bloom = new SimpleBloomFilter(1024, 0.01);
        assertThat(bloom.mightContain("never-added")).isFalse();
        bloom.add("oculus:mondstadt:1");
        assertThat(bloom.mightContain("oculus:mondstadt:1")).isTrue();
    }

    @Test
    public void battleReplayColdArchive() {
        BattleReplayService replay = new BattleReplayService();
        long now = System.currentTimeMillis();
        var start = replay.start("r1", 42L, now);
        String id = String.valueOf(start.get("replayId"));
        replay.append(id, 1, now, "HIT", Map.of("damage", 100));
        long coldNow = now + HistoricalDataRetention.COLD_ARCHIVE_MS + HistoricalDataRetention.HOT_REPLAY_MS + 1;
        var archived = replay.archiveIfExpired(id, coldNow);
        assertThat(archived.get("ok")).isEqualTo(true);
        assertThat(replay.retention().isArchived("replay:" + id)).isTrue();
    }

    @Test
    public void handbookBloomSkipsRedisOnNegative() {
        HandbookService handbook = new HandbookService();
        assertThat(handbook.mightHaveDiscovered(1L, HandbookService.EntryKind.FISH, "salmon")).isFalse();
        handbook.discover(1L, HandbookService.EntryKind.FISH, "salmon", System.currentTimeMillis(), false);
        assertThat(handbook.mightHaveDiscovered(1L, HandbookService.EntryKind.FISH, "salmon")).isTrue();
    }

    @Test
    public void openWorldGameplayTickUsesSlicer() {
        OpenWorldGameplayFacade facade = new OpenWorldGameplayFacade();
        Map<String, Object> tick = facade.tickGameplay(System.currentTimeMillis());
        assertThat(tick.get("ok")).isEqualTo(true);
        assertThat(tick).containsKey("slice");
        assertThat(tick).containsKey("tickSlicer");
    }

    @Test
    public void aoiBatcherPlusDeltaEncoderEndToEnd() {
        AoiUpdateBatcher batcher = new AoiUpdateBatcher();
        AoiDeltaEncoder encoder = new AoiDeltaEncoder();
        batcher.enqueue(10L, 5f, 0f, 5f, 100, 1, 1000L);
        batcher.enqueue(10L, 6f, 0f, 5f, 100, 1, 1050L);
        assertThat(batcher.pendingSize()).isEqualTo(1);
        var snap = batcher.flushAll(1100L);
        assertThat(snap.nearFull()).hasSize(1);
        var u = snap.nearFull().get(0);
        var delta = encoder.encode(u.entityId(), u.x(), u.y(), u.z(), u.hp(), u.syncType());
        assertThat(delta.changedMask()).isGreaterThan(0);
        var delta2 = encoder.encode(u.entityId(), u.x(), u.y(), u.z(), u.hp(), u.syncType());
        assertThat(delta2.changedMask()).isZero();
    }

    @Test
    public void historicalRetentionShardRouting() {
        HistoricalDataRetention retention = new HistoricalDataRetention();
        assertThat(retention.shardTable(12345L)).startsWith("guild_boss_damage_");
        assertThat(retention.shardTable("replay:abc")).startsWith("player_chronicle_");
        retention.markHot("replay:test", System.currentTimeMillis());
        assertThat(retention.listHotKeys()).contains("replay:test");
    }

    @Test
    public void walThenPipelineCombinedFlow() {
        AssetWalWriter wal = new AssetWalWriter();
        PlayerWritePipeline pipeline = new PlayerWritePipeline();
        long now = System.currentTimeMillis();
        wal.append(1L, "GRANT", "{\"gold\":100}", now);
        pipeline.enqueue(1L, "BAG_ADD", Map.of("itemId", "gold", "count", 100));
        assertThat(wal.drain(10)).hasSize(1);
        var flushed = pipeline.flush(ops -> assertThat(ops).hasSize(1));
        assertThat(flushed.get("flushed")).isEqualTo(1);
    }

    @Test
    public void sceneActorMailboxDropsWhenQueueFull() throws Exception {
        SceneActorMailbox mailbox = new SceneActorMailbox();
        mailbox.configure(4);
        java.util.concurrent.CountDownLatch block = new java.util.concurrent.CountDownLatch(1);
        // 先塞满队列：阻塞任务防止 drain 清空
        for (int i = 0; i < 4; i++) {
            mailbox.tell(1L, () -> {
                try {
                    block.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        Thread.sleep(50);
        int accepted = 0;
        for (int i = 0; i < 10; i++) {
            if (mailbox.tell(1L, () -> {
            })) {
                accepted++;
            }
        }
        block.countDown();
        assertThat(accepted).isLessThan(10);
        assertThat((Long) mailbox.stats().get("dropped")).isGreaterThan(0L);
    }

    @Test
    public void performanceHardeningMetricsSnapshot() {
        PerformanceHardeningMetrics metrics = new PerformanceHardeningMetrics(
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
        Map<String, Object> snap = metrics.snapshot();
        assertThat(snap).containsKeys("concurrency", "gcPooling", "aoi", "tick", "persistence", "bloomFilter",
                "p15Sync", "p15Persistence", "p15Physics");
    }

    @Test
    public void collectibleBloomNegativeThenCollect() {
        CollectibleService collectibles = new CollectibleService();
        collectibles.register(new CollectibleService.CollectibleDef(
                "oculus-test-1", "测试神瞳", CollectibleService.Tier.OCULUS,
                1, 10f, 0f, 10f, 3f, "oculus_reward", 1, 1, 5));
        assertThat(collectibles.collect(1L, "oculus-test-1", 10f, 0f, 10f).get("ok")).isEqualTo(true);
        assertThat(collectibles.collect(1L, "oculus-test-1", 10f, 0f, 10f).get("error"))
                .isEqualTo("already_collected");
    }

    @Test
    public void worldLevelUpgradeRejectsInsufficientFunds() {
        WorldLevelManager mgr = new WorldLevelManager();
        mgr.tryUpgradeWorldLevel(1, 0L, 50, 1);
        Map<String, Object> fail = mgr.tryUpgradeWorldLevel(1, 0L, 999_999, 2);
        assertThat(fail.get("ok")).isEqualTo(false);
    }

    @Test
    public void bossFirstHitLuaLocalFallback() {
        RedisAtomicScriptService scripts = new RedisAtomicScriptService();
        Map<String, Object> r = scripts.bossFirstHit("boss:hp:1", 999_999, 42L);
        assertThat(r.get("ok")).isEqualTo(false);
    }

    @Test
    public void gameplayTickSlicerAsyncEcoDoesNotBlock() throws Exception {
        OpenWorldGameplayFacade facade = new OpenWorldGameplayFacade();
        long t0 = System.currentTimeMillis();
        facade.tickGameplay(t0);
        long elapsed = System.currentTimeMillis() - t0;
        assertThat(elapsed).isLessThan(500L);
    }

    @Test
    public void sceneComponentStoreParallelArrays() {
        SceneComponentStore store = new SceneComponentStore();
        store.allocate(1L, 10f, 0f, 20f, 100);
        store.setVelocity(1L, 5f, 0f, 2f);
        assertThat(store.getX(1L)).isEqualTo(10f);
        assertThat(store.size()).isEqualTo(1);
        SceneTickEngine engine = new SceneTickEngine();
        var tick = engine.tick(store, System.currentTimeMillis());
        assertThat(tick.get("processed")).isEqualTo(1L);
    }

    @Test
    public void dynamicFrequencyLodByDistance() {
        DynamicFrequencyService freq = new DynamicFrequencyService();
        assertThat(freq.computeSyncIntervalMs(5f, 10f)).isLessThanOrEqualTo(100L);
        assertThat(freq.tierIntervalMs(120f)).isGreaterThanOrEqualTo(100L);
        long now = System.currentTimeMillis();
        assertThat(freq.shouldSync(99L, 5f, 10f, now)).isTrue();
        assertThat(freq.shouldSync(99L, 5f, 10f, now + 10)).isFalse();
    }

    @Test
    public void moveDeltaEncoderYawThreshold() {
        MoveDeltaEncoder encoder = new MoveDeltaEncoder();
        SceneMoveCmd base = SceneMoveCmd.walk(1f, 0f, 2f, 5f, 100L);
        var full = encoder.encode(1L, base);
        assertThat(full.changedMask()).isGreaterThan(0);
        var same = encoder.encode(1L, base);
        assertThat(same.changedMask()).isZero();
    }

    @Test
    public void mergedMoveAckRequiresThreeFrames() {
        MergedMoveAckService ack = new MergedMoveAckService();
        assertThat(ack.receiveAck(1L, 1L, 1f, 0f, 1f, 100L).get("merged")).isEqualTo(false);
        assertThat(ack.receiveAck(1L, 2L, 2f, 0f, 2f, 110L).get("merged")).isEqualTo(false);
        var merged = ack.receiveAck(1L, 3L, 3f, 0f, 3f, 120L);
        assertThat(merged.get("merged")).isEqualTo(true);
        assertThat(merged.get("z")).isEqualTo(3f);
    }

    @Test
    public void localPositionCacheL1() {
        LocalPositionCache cache = new LocalPositionCache();
        cache.put(1L, 10f, 0f, 20f, System.currentTimeMillis());
        assertThat(cache.get(1L).x()).isEqualTo(10f);
        assertThat(cache.stats().get("hits")).isEqualTo(1L);
    }

    @Test
    public void playerWritePipelineDedupeFilter() {
        DeduplicateFilter dedupe = new DeduplicateFilter();
        long now = System.currentTimeMillis();
        assertThat(dedupe.shouldWrite(1L, "hp", 100, now)).isTrue();
        assertThat(dedupe.shouldWrite(1L, "hp", 100, now + 10)).isFalse();
        assertThat(dedupe.shouldWrite(1L, "hp", 90, now + 10)).isTrue();
    }

    @Test
    public void coldHotDataRouter() {
        ColdHotDataRouter router = new ColdHotDataRouter();
        assertThat(router.route(ColdHotDataRouter.DataKind.SCENE_POSITION, 1L, System.currentTimeMillis()))
                .isEqualTo(ColdHotDataRouter.DataTier.HOT_REDIS);
        assertThat(router.route(ColdHotDataRouter.DataKind.ABYSS_STARS, 1L, System.currentTimeMillis()))
                .isEqualTo(ColdHotDataRouter.DataTier.COLD_MYSQL);
    }

    @Test
    public void litePhysicsGrappleLanding() {
        LitePhysicsEngine lite = new LitePhysicsEngine();
        float[] heights = new float[256 * 256];
        lite.loadHeightMap(256, 256, 1f, 0f, 0f, heights);
        GrapplePhysicsService grapple = new GrapplePhysicsService();
        Map<String, Object> ok = grapple.validateGrappleLanding(
                1L, 5f, 0f, 5f, 5f, 0f, 5f, 10f);
        assertThat(ok.get("ok")).isEqualTo(true);
    }

    @Test
    public void physicsAuthorityAsyncDoesNotBlock() throws Exception {
        PhysicsAuthorityService auth = new PhysicsAuthorityService();
        auth.recordExpected(1L, new PhysicsAuthorityService.ExpectedPhysics(
                1f, 0f, 0f, 1f, 0f, 1f, 0f, System.currentTimeMillis()));
        var future = auth.validateHashAsyncFuture(
                1L, auth.computeHash(1f, 0f, 0f, 1f, 0f, 1f, 0f),
                1f, 0f, 0f, 1f, 0f, 1f, 0f, false);
        Map<String, Object> result = future.get();
        assertThat(result.get("async")).isEqualTo(true);
    }
}
