package cn.itcast.demo.mymmorpg.world.gameplay;

import cn.itcast.demo.mymmorpg.world.battle.CombatAssistService;
import cn.itcast.demo.mymmorpg.world.explore.ExplorationCompassService;
import cn.itcast.demo.mymmorpg.world.explore.MapMarkerService;
import cn.itcast.demo.mymmorpg.world.progression.FlexibleDailyQuestService;
import cn.itcast.demo.mymmorpg.world.traverse.TraverseModeService;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** P13 边界与异常路径。 */
public class OpenWorldP13EdgeCaseTest {

    @Test
    public void compassRejectsProbeWhenBelowThreshold() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> probe = g.explorationCompass().probe(
                1L, "wolf-camp-valley", 0f, 0f, 0f, 100f, 1_000L);
        assertThat(probe.get("ok")).isEqualTo(false);
        assertThat(probe.get("error")).isEqualTo("compass_locked");
    }

    @Test
    public void compassEnforcesProbeCooldown() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long playerId = 2L;
        boostToUnlock(g, playerId);
        g.explorationCompass().probe(playerId, "wolf-camp-valley", 100f, 0f, 100f, 120f, 5_000L);
        Map<String, Object> again = g.explorationCompass().probe(
                playerId, "wolf-camp-valley", 100f, 0f, 100f, 120f, 5_100L);
        assertThat(again.get("ok")).isEqualTo(false);
        assertThat(again.get("error")).isEqualTo("probe_cooldown");
    }

    @Test
    public void mapMarkerChallengeBlocksRepeatReward() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.mapMarkers().startChallenge(3L, "challenge-timed-1");
        Map<String, Object> done = g.mapMarkers().completeChallenge(3L, "challenge-timed-1");
        assertThat(done.get("repeatRewardBlocked")).isEqualTo(true);
    }

    @Test
    public void regionalTraverseRejectsOutOfRangeEntry() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> use = g.regionalTraverse().use(
                4L, "zipline-valley-1", 0f, 0f, 0f, 1_000L);
        assertThat(use.get("ok")).isEqualTo(false);
        assertThat(use.get("error")).isEqualTo("out_of_entry_range");
    }

    @Test
    public void universalTraversalDoesNotDuplicateGrant() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.universalTraversal().grantUniversal(5L, TraverseModeService.Mode.HOOK);
        g.universalTraversal().grantUniversal(5L, TraverseModeService.Mode.HOOK);
        @SuppressWarnings("unchecked")
        List<String> modes = (List<String>) g.universalTraversal().snapshot(5L).get("universalModes");
        assertThat(modes.stream().filter("HOOK"::equals).count()).isEqualTo(1);
    }

    @Test
    public void flexibleDailyRejectsDoubleClaim() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.flexibleDaily().reportProgress(6L, FlexibleDailyQuestService.ProgressKind.ANY_BATTLE, 3);
        assertThat(g.flexibleDaily().claim(6L, "daily-battle").get("ok")).isEqualTo(true);
        assertThat(g.flexibleDaily().claim(6L, "daily-battle").get("error")).isEqualTo("already_claimed");
    }

    @Test
    public void resourceAutomationRejectsClaimWithoutElapsedHour() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long now = 10_000L;
        g.resourceAutomation().build(7L, "auto-mine-1", now);
        Map<String, Object> claim = g.resourceAutomation().claimAll(7L, now + 1_000L);
        assertThat(claim.get("ok")).isEqualTo(true);
        assertThat((Long) claim.get("totalItems")).isEqualTo(0L);
    }

    @Test
    public void combatAssistClassicModeNoAutoCombo() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.combatAssist().setMode(8L, CombatAssistService.AssistMode.CLASSIC);
        Map<String, Object> resolve = g.combatAssist().resolveAttack(
                8L, 8L, 99L, 200d, 20d, false, 0.5f, 0.8f);
        assertThat(resolve.get("autoComboEnabled")).isNull();
        assertThat(resolve.get("timeSlow")).isNull();
    }

    @Test
    public void explorationImpactIdempotentOnReevaluate() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        boostToUnlock(g, 9L);
        Map<String, Object> first = g.explorationImpacts().evaluate(9L, "wolf-camp-valley");
        Map<String, Object> second = g.explorationImpacts().evaluate(9L, "wolf-camp-valley");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> newly1 = (List<Map<String, Object>>) first.get("newlyApplied");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> newly2 = (List<Map<String, Object>>) second.get("newlyApplied");
        assertThat(newly1.stream().filter(r -> Boolean.TRUE.equals(r.get("justApplied"))).count())
                .isGreaterThan(0);
        assertThat(newly2.stream().filter(r -> Boolean.TRUE.equals(r.get("justApplied"))).count())
                .isEqualTo(0);
    }

    @Test
    public void environmentalStoryUnknownPropFails() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        assertThat(g.environmentalStory().inspect(10L, "missing-prop").get("ok")).isEqualTo(false);
    }

    private static void boostToUnlock(OpenWorldGameplayFacade g, long playerId) {
        String region = "wolf-camp-valley";
        for (int i = 0; i < 5; i++) {
            g.regionProgress().markWaypoint(playerId, region, "wp-" + playerId + "-" + i);
            g.regionProgress().markCollectible(playerId, region, "col-" + playerId + "-" + i);
            g.regionProgress().markPuzzle(playerId, region, "pz-" + playerId + "-" + i);
            g.regionProgress().markWorldQuest(playerId, region, "wq-" + playerId + "-" + i);
        }
        Map<String, Object> st = g.explorationCompass().unlockStatus(playerId, region);
        assertThat(st.get("compassUnlocked")).isEqualTo(true);
    }
}
