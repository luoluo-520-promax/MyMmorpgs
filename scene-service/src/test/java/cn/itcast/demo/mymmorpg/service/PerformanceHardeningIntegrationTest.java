package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.world.loot.LootOwnershipPolicy;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P12 性能加固 — Scene 运行时集成：Tick 分片 / 写流水线 / 世界等级 Lua / 性能指标。
 */
public class PerformanceHardeningIntegrationTest {

    @Test
    public void runtimeStatusExposesPerformanceMetrics() {
        OpenWorldRuntimeService runtime = new OpenWorldRuntimeService();
        @SuppressWarnings("unchecked")
        Map<String, Object> perf = (Map<String, Object>) runtime.status().get("performance");
        assertThat(perf).isNotNull();
        assertThat(perf).containsKeys("concurrency", "gcPooling", "aoi", "tick", "persistence");
    }

    @Test
    public void worldTickUsesGameplaySlicer() {
        OpenWorldRuntimeService runtime = new OpenWorldRuntimeService();
        runtime.onWorldTick();
        Map<String, Object> tick = runtime.gameplay().tickGameplay(System.currentTimeMillis());
        assertThat(tick.get("ok")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> slice = (Map<String, Object>) tick.get("slice");
        assertThat(slice).containsKeys("index", "processed", "elapsedMs", "withinBudget");
    }

    @Test
    public void flushWritePipelineDrainsWithoutError() {
        OpenWorldRuntimeService runtime = new OpenWorldRuntimeService();
        runtime.flushWritePipeline();
        @SuppressWarnings("unchecked")
        Map<String, Object> perf = (Map<String, Object>) runtime.status().get("performance");
        @SuppressWarnings("unchecked")
        Map<String, Object> persistence = (Map<String, Object>) perf.get("persistence");
        assertThat(persistence).containsKey("writePipeline");
    }

    @Test
    public void worldLevelAtomicUpgradeFlow() {
        OpenWorldRuntimeService runtime = new OpenWorldRuntimeService();
        Map<String, Object> upgrade = runtime.worldLevel().tryUpgradeWorldLevel(1, 0L, 50, 4);
        assertThat(upgrade.get("ok")).isEqualTo(true);
        assertThat(runtime.worldLevel().getWorldLevel(1, 0L)).isGreaterThanOrEqualTo(3);
    }

    @Test
    public void collectWithOwnershipStillWorksAfterHardening() {
        OpenWorldRuntimeService runtime = new OpenWorldRuntimeService();
        Map<String, Object> collect = runtime.collectWithOwnership(
                "gather-mondstadt-1", 10001L, 10001L, Set.of(10001L),
                LootOwnershipPolicy.SyncMode.WORLD_SHARED);
        assertThat(collect.get("ok")).isEqualTo(true);
    }

    @Test
    public void mixedLoadBenchmarkWithinLimits() {
        OpenWorldRuntimeService runtime = new OpenWorldRuntimeService();
        Map<String, Object> bench = runtime.mixedLoadBenchmark(100, 50, 50, 500);
        assertThat(bench.get("ok")).isEqualTo(true);
        assertThat((Long) bench.get("totalMs")).isLessThan(30_000L);
    }

    @Test
    public void gameplayHandbookBloomIntegrated() {
        OpenWorldRuntimeService runtime = new OpenWorldRuntimeService();
        long now = System.currentTimeMillis();
        assertThat(runtime.gameplay().handbook().mightHaveDiscovered(
                77L, cn.itcast.demo.mymmorpg.world.sideplay.HandbookService.EntryKind.CREATURE, "wolf")).isFalse();
        Map<String, Object> discover = runtime.gameplay().handbook().discover(
                77L, cn.itcast.demo.mymmorpg.world.sideplay.HandbookService.EntryKind.CREATURE, "wolf", now, false);
        assertThat(discover.get("ok")).isEqualTo(true);
        assertThat(runtime.gameplay().handbook().mightHaveDiscovered(
                77L, cn.itcast.demo.mymmorpg.world.sideplay.HandbookService.EntryKind.CREATURE, "wolf")).isTrue();
    }

    @Test
    public void battleReplayArchiveThroughGameplay() {
        OpenWorldRuntimeService runtime = new OpenWorldRuntimeService();
        long now = System.currentTimeMillis();
        var start = runtime.gameplay().replays().start("integration-replay", 99L, now);
        String id = String.valueOf(start.get("replayId"));
        assertThat(runtime.gameplay().replays().append(id, 1, now, "SKILL", Map.of("skillId", 101)).get("ok"))
                .isEqualTo(true);
        long cold = now + cn.itcast.demo.mymmorpg.persistence.HistoricalDataRetention.COLD_ARCHIVE_MS
                + cn.itcast.demo.mymmorpg.persistence.HistoricalDataRetention.HOT_REPLAY_MS + 1;
        assertThat(runtime.gameplay().replays().archiveIfExpired(id, cold).get("ok")).isEqualTo(true);
    }
}
