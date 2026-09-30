package cn.itcast.demo.mymmorpg.world.battle;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class BattleReplayBaselineTest {

    private BattleReplayService replayService;
    private HistoricalConfigSnapshotStore store;

    @BeforeMethod
    public void setUp() {
        store = new HistoricalConfigSnapshotStore();
        replayService = new BattleReplayService(store);
    }

    @Test
    public void playback_usesHistoricalBaseline() {
        PatchBaselineContext baseline = new PatchBaselineContext("cfg-v1", "sha-abc", 42L, null);
        replayService.start("battle-1", 123L, System.currentTimeMillis(), baseline);
        replayService.append("battle-1", 1, 100L, "DAMAGE", Map.of("damage", 500));

        Map<String, Object> playback = replayService.playback("battle-1", 1.0, null, null);
        assertThat(playback.get("ok")).isEqualTo(true);
        assertThat(playback.get("usingHistoricalBaseline")).isEqualTo(true);
        assertThat(playback.get("configVersion")).isEqualTo("cfg-v1");
    }

    @Test
    public void playback_rejectsWhenBaselineExpired() {
        PatchBaselineContext baseline = new PatchBaselineContext("cfg-expired", "sha-old", 1L, null);
        replayService.start("battle-2", 99L, System.currentTimeMillis(), baseline);
        store.remove("cfg-expired");
        Map<String, Object> playback = replayService.playback("battle-2", 1.0, null, null);
        assertThat(playback.get("error")).isEqualTo("baseline_expired");
    }
}
