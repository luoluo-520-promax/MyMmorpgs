package cn.itcast.demo.mymmorpg.world.gameplay;

import cn.itcast.demo.mymmorpg.sync.MovementType;
import cn.itcast.demo.mymmorpg.sync.SceneMoveCmd;
import cn.itcast.demo.mymmorpg.world.battle.CombatAssistService;
import cn.itcast.demo.mymmorpg.world.ecosystem.EcosystemBehaviorService;
import cn.itcast.demo.mymmorpg.world.ecosystem.WorldExplorationFeedbackService;
import cn.itcast.demo.mymmorpg.world.explore.ExplorationCompassService;
import cn.itcast.demo.mymmorpg.world.explore.ExplorationWorldImpactService;
import cn.itcast.demo.mymmorpg.world.explore.MapMarkerService;
import cn.itcast.demo.mymmorpg.world.progression.FlexibleDailyQuestService;
import cn.itcast.demo.mymmorpg.world.traverse.RegionalTraverseService;
import cn.itcast.demo.mymmorpg.world.traverse.TraverseModeService;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** P13：探索便利 / 移动体验 / 生态沉浸 / 战斗辅助 / 长线减负。 */
public class OpenWorldP13FlowTest {

  @Test
  public void compassUnlocksAtSeventyPercentAndProbesRemaining() {
    OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
    long playerId = 101L;
    String region = "wolf-camp-valley";

    Map<String, Object> locked = g.explorationCompass().unlockStatus(playerId, region);
    assertThat(locked.get("compassUnlocked")).isEqualTo(false);

    for (int i = 0; i < 5; i++) {
      g.regionProgress().markWaypoint(playerId, region, "wp-" + i);
    }
    for (int i = 0; i < 6; i++) {
      g.regionProgress().markCollectible(playerId, region, "col-" + i);
    }
    for (int i = 0; i < 3; i++) {
      g.regionProgress().markPuzzle(playerId, region, "pz-" + i);
    }
    g.regionProgress().markWorldQuest(playerId, region, "q-1");
    g.regionProgress().markWorldQuest(playerId, region, "q-2");

    Map<String, Object> unlocked = g.explorationCompass().unlockStatus(playerId, region);
    assertThat(unlocked.get("compassUnlocked")).isEqualTo(true);

    Map<String, Object> probe = g.explorationCompass().probe(
        playerId, region, 100f, 0f, 100f, 200f, 1_000L);
    assertThat(probe.get("ok")).isEqualTo(true);
    assertThat((Integer) probe.get("remainingCount")).isGreaterThan(0);
  }

  @Test
  public void mapMarkersShowCollectedStatus() {
    OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
    long playerId = 102L;
    g.collectibles().collect(playerId, "chest-common-1", 100f, 0f, 100f);

    Map<String, Object> markers = g.mapMarkers().markersForRegion(playerId, "1");
    assertThat(markers.get("ok")).isEqualTo(true);
    @SuppressWarnings("unchecked")
    var list = (java.util.List<Map<String, Object>>) markers.get("markers");
    assertThat(list.stream().anyMatch(m ->
        "chest-common-1".equals(m.get("markerId"))
            && "COLLECTED".equals(m.get("status")))).isTrue();
  }

  @Test
  public void explorationWorldImpactUnlocksPathAndNpcDialogue() {
    OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
    long playerId = 103L;
    String region = "wolf-camp-valley";

    for (int i = 0; i < 5; i++) {
      g.regionProgress().markWaypoint(playerId, region, "wp2-" + i);
      g.regionProgress().markCollectible(playerId, region, "col2-" + i);
      g.regionProgress().markPuzzle(playerId, region, "pz2-" + i);
      g.regionProgress().markWorldQuest(playerId, region, "wq2-" + i);
    }

    Map<String, Object> impact = g.explorationImpacts().evaluate(playerId, region);
    assertThat(impact.get("ok")).isEqualTo(true);
    @SuppressWarnings("unchecked")
    var newly = (java.util.List<Map<String, Object>>) impact.get("newlyApplied");
    assertThat(newly).isNotEmpty();

    String line = g.explorationImpacts().npcDialogueOverride(
        playerId, "npc:rescued-merchant", "欢迎光临。");
    assertThat(line).contains("商路");
  }

  @Test
  public void universalTraversalGrantsCoreKitWithoutCharacter() {
    OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
    long playerId = 104L;
    Map<String, Object> kit = g.universalTraversal().ensureCoreExplorationKit(playerId);
    assertThat(kit.get("coreKitGranted")).isEqualTo(true);
    assertThat(g.universalTraversal().canUseMode(playerId, TraverseModeService.Mode.HOOK)).isTrue();

    SceneMoveCmd cmd = new SceneMoveCmd(
        480f, 60f, 490f, 6f, 2_000L, MovementType.GRAPPLE, "",
        0, "", "grapple-ruin-1", 0f, 0f, 0f);
    Map<String, Object> admit = g.movementAdmission().admit(
        playerId, cmd, 200L, 2_000L, 475f, 58f, 488f);
    assertThat(admit.get("ok")).isEqualTo(true);
  }

  @Test
  public void climbRestPointRecoversStamina() {
    OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
    long playerId = 105L;
    g.universalTraversal().grantUniversal(playerId, TraverseModeService.Mode.CLIMB);
    g.stamina().consume(playerId, MovementType.CLIMB, 5_000L, 1f, 1_000L);

    SceneMoveCmd cmd = new SceneMoveCmd(
        405f, 25f, 202f, 2f, 2_000L, MovementType.CLIMB, "cliff-valley-north",
        0, "", "", 0f, 0f, 0f);
    Map<String, Object> admit = g.movementAdmission().admit(
        playerId, cmd, 200L, 2_000L, 404f, 24f, 201f);
    assertThat(admit.get("onRestPoint")).isEqualTo(true);
    assertThat(((Number) admit.get("recovered")).floatValue()).isGreaterThan(0f);
  }

  @Test
  public void regionalZiplineWorks() {
    OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
    Map<String, Object> use = g.regionalTraverse().use(
        106L, "zipline-valley-1", 200f, 30f, 200f, 3_000L);
    assertThat(use.get("ok")).isEqualTo(true);
    assertThat(use.get("kind")).isEqualTo(RegionalTraverseService.FacilityKind.ZIPLINE.name());
  }

  @Test
  public void ecoNarrativeLinksFeedingToQuest() {
    OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
    g.ecoNarrative().setActiveQuest(107L, "quest-fox-trail");
    g.ecosystem().tick("eco-fox-1", 12, 35_000L, false);
    Map<String, Object> eval = g.ecoNarrative().evaluate(107L, "eco-fox-1");
    assertThat(eval.get("hasNarrative")).isEqualTo(true);
  }

  @Test
  public void environmentalStoryInspectsProp() {
    OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
    Map<String, Object> inspect = g.environmentalStory().inspect(108L, "prop-abandoned-camp");
    assertThat(inspect.get("ok")).isEqualTo(true);
    assertThat(inspect.get("silentNarrative").toString()).contains("脚印");
  }

  @Test
  public void explorationFeedbackGrantsPositiveWorldEffect() {
    OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
    Map<String, Object> fb = g.explorationFeedback().recordAction(
        109L, "wolf-camp-valley", WorldExplorationFeedbackService.ActionKind.CLEAR_CAMP);
    @SuppressWarnings("unchecked")
    var unlocked = (java.util.List<Map<String, Object>>) fb.get("newlyUnlocked");
    assertThat(unlocked).isNotEmpty();
    assertThat(g.explorationFeedback().activeEffects(109L)).contains("fb-clear-camp");
  }

  @Test
  public void combatAssistSimplifiedModeEnablesAutoComboAndWarnings() {
    OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
    g.combatAssist().setMode(110L, CombatAssistService.AssistMode.SIMPLIFIED);
    Map<String, Object> resolve = g.combatAssist().resolveAttack(
        110L, 110L, 9001L, 300d, 30d, false, 0.2f, 0.25f);
    assertThat(resolve.get("autoComboEnabled")).isEqualTo(true);
    assertThat(resolve.get("damageWarning")).isNotNull();
    assertThat(resolve.get("timeSlow")).isNotNull();
    assertThat(((Map<?, ?>) resolve.get("hitFeedback")).get("enhancedVfx")).isEqualTo(true);
  }

  @Test
  public void resourceAutomationAndFlexibleDailyQuests() {
    OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
    long playerId = 111L;
    long now = System.currentTimeMillis();

    assertThat(g.resourceAutomation().build(playerId, "auto-mine-1", now).get("ok")).isEqualTo(true);
    Map<String, Object> claim = g.resourceAutomation().claimAll(playerId, now + 3_600_100L);
    assertThat(claim.get("ok")).isEqualTo(true);
    assertThat((Long) claim.get("totalItems")).isGreaterThan(0L);

    g.flexibleDaily().reportProgress(playerId, FlexibleDailyQuestService.ProgressKind.ANY_BATTLE, 3);
    g.flexibleDaily().reportProgress(playerId, FlexibleDailyQuestService.ProgressKind.STAMINA_SPENT, 100);
    Map<String, Object> claimed = g.flexibleDaily().claimAll(playerId);
    assertThat(((java.util.List<?>) claimed.get("claimed")).size()).isGreaterThanOrEqualTo(2);
  }

  @Test
  public void buildRecommendationAndApplyPreset() {
    OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
    Map<String, Object> rec = g.buildRecommend().recommend(112L, "char_ayaka");
    assertThat(rec.get("topPick")).isNotNull();
    @SuppressWarnings("unchecked")
    String presetId = (String) ((Map<String, Object>) rec.get("topPick")).get("presetId");
    Map<String, Object> applied = g.buildRecommend().applyPreset(112L, presetId);
    assertThat(applied.get("oneClickApplied")).isEqualTo(true);
  }
}
