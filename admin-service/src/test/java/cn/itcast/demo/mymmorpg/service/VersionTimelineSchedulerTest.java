package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.activity.ActivitySnapshotManager;
import cn.itcast.demo.mymmorpg.cache.CacheWarmUpService;
import cn.itcast.demo.mymmorpg.entity.VersionTimeline;
import cn.itcast.demo.mymmorpg.repository.VersionTimelineRepository;
import cn.itcast.demo.mymmorpg.version.VersionGateKeeper;
import cn.itcast.demo.mymmorpg.world.content.OpenWorldConfigPatchService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * VersionTimelineScheduler：预热 / 缓存预热 / 快照 / 自动 publish 触发。
 */
public class VersionTimelineSchedulerTest {

    private VersionTimelineRepository repo;
    private OpenWorldConfigPatchService configPatch;
    private VersionGateKeeper gateKeeper;
    private CacheWarmUpService warmUp;
    private ActivitySnapshotManager snapshots;
    private VersionTimelineScheduler scheduler;
    private Method processEntry;

    @BeforeMethod
    public void setUp() throws Exception {
        repo = mock(VersionTimelineRepository.class);
        configPatch = mock(OpenWorldConfigPatchService.class);
        gateKeeper = mock(VersionGateKeeper.class);
        warmUp = mock(CacheWarmUpService.class);
        snapshots = mock(ActivitySnapshotManager.class);
        when(configPatch.publishStaging(anyLong())).thenReturn(Map.of("ok", true));
        when(warmUp.warmUpForVersion(anyString(), anyList())).thenReturn(Map.of("ok", true));
        when(snapshots.snapshotActivePlayers(anyString())).thenReturn(Map.of("ok", true));
        when(repo.save(any(VersionTimeline.class))).thenAnswer(inv -> inv.getArgument(0));
        scheduler = new VersionTimelineScheduler(repo, configPatch, gateKeeper, warmUp, snapshots);
        processEntry = VersionTimelineScheduler.class.getDeclaredMethod(
                "processEntry", VersionTimeline.class, long.class);
        processEntry.setAccessible(true);
    }

    @Test
    public void beforeEffective_triggersPreheatWarmupAndSnapshot() throws Exception {
        long now = System.currentTimeMillis();
        VersionTimeline entry = baseEntry(now);
        entry.setEffectiveAtMs(now + 5 * 60 * 1000);

        processEntry.invoke(scheduler, entry, now);

        verify(gateKeeper).markPreheat("2.6.0");
        verify(warmUp).warmUpForVersion(eq("2.6.0"), anyList());
        verify(snapshots).snapshotActivePlayers("2.6.0");
        verify(configPatch, never()).publishStaging(anyLong());
        assertThat(entry.getExecutedPreheat()).isTrue();
        assertThat(entry.getExecutedWarmup()).isTrue();
        assertThat(entry.getExecutedPublish()).isFalse();
    }

    @Test
    public void atEffective_triggersPublish() throws Exception {
        long now = System.currentTimeMillis();
        VersionTimeline entry = baseEntry(now);
        entry.setExecutedPreheat(true);
        entry.setExecutedWarmup(true);
        entry.setEffectiveAtMs(now - 1);

        processEntry.invoke(scheduler, entry, now);

        verify(configPatch).publishStaging(now);
        assertThat(entry.getExecutedPublish()).isTrue();
    }

    @Test
    public void listMemoryTimeline_emptyAfterReloadWithNoUpcoming() {
        when(repo.findUpcoming(anyLong(), anyLong())).thenReturn(List.of());
        scheduler.reloadMemoryTimeline();
        assertThat(scheduler.listMemoryTimeline()).isEmpty();
    }

    private static VersionTimeline baseEntry(long now) {
        VersionTimeline entry = new VersionTimeline();
        entry.setId(1L);
        entry.setVersionCode("2.6.0");
        entry.setConfigVersion("cfg-260");
        entry.setEnabled(true);
        entry.setPredownloadAtMs(now - 1000);
        entry.setForceUpdateAtMs(now);
        entry.setEffectiveAtMs(now);
        entry.setExecutedPublish(false);
        entry.setExecutedPreheat(false);
        entry.setExecutedWarmup(false);
        return entry;
    }
}
