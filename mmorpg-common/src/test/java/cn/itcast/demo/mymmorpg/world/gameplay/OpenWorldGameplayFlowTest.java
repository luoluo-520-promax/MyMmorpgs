package cn.itcast.demo.mymmorpg.world.gameplay;

import cn.itcast.demo.mymmorpg.sync.MovementPredictionValidator;
import cn.itcast.demo.mymmorpg.world.sideplay.ExtractionMissionService;
import cn.itcast.demo.mymmorpg.world.traverse.EnvironmentInteractionService;
import cn.itcast.demo.mymmorpg.world.traverse.TraverseModeService;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 二游大世界产品缺口联合流程：探索奖励 / 奇观 / 区域影响 / 叙事偶遇 /
 * 探索技能 / 自由移动 / 环境交互 / 惊喜 / 副玩法 / 搜打撤。
 */
public class OpenWorldGameplayFlowTest {

    @Test
    public void explorationRewardLoopHooksCoreProgression() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> vista = g.discoverExploration(
                1L, "vista-sky-ruin", 200f, 40f, 120f, 1_000L);
        assertThat(vista.get("ok")).isEqualTo(true);
        assertThat(vista.get("grantPlans")).asList().isNotEmpty();
        assertThat(String.valueOf(vista.get("idempotencyKey"))).contains("explore:1:");

        Map<String, Object> blocked = g.discoverExploration(
                1L, "chest-hidden-glyph", 310f, 2f, 88f, 1_001L);
        assertThat(blocked.get("ok")).isEqualTo(false);
        assertThat(blocked.get("error")).isEqualTo("missing_explore_skill");

        g.exploreSkills().switchCharacter(1L, "char_qiqi");
        Map<String, Object> chest = g.discoverExploration(
                1L, "chest-hidden-glyph", 310f, 2f, 88f, 1_002L);
        assertThat(chest.get("ok")).isEqualTo(true);
        assertThat(chest.get("grantPlans").toString()).contains("skin_cape_explorer");
    }

    @Test
    public void landmarkWonderRequiresTraverseModesThenUnlocks() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        assertThat(g.enterLandmark(2L, "floating-ruin").get("error"))
                .isEqualTo("missing_traverse_mode");

        g.traverse().unlock(2L, TraverseModeService.Mode.HOOK);
        g.traverse().unlock(2L, TraverseModeService.Mode.GLIDE);
        g.traverse().unlock(2L, TraverseModeService.Mode.WIND_FIELD);
        assertThat(g.enterLandmark(2L, "floating-ruin").get("ok")).isEqualTo(true);

        assertThat(g.landmarks().advanceLayer(2L, "floating-ruin").get("cleared")).isEqualTo(false);
        assertThat(g.landmarks().advanceLayer(2L, "floating-ruin").get("cleared")).isEqualTo(false);
        Map<String, Object> clear = g.landmarks().advanceLayer(2L, "floating-ruin");
        assertThat(clear.get("cleared")).isEqualTo(true);
        assertThat(clear.get("unlockIds")).asList()
                .contains("portal:ruin-peak", "npc:ruin-scholar");
    }

    @Test
    public void clearingCampsChangesRegionWorldState() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        assertThat(g.regions().snapshot("wolf-camp-valley").get("safety")).isEqualTo("HOSTILE");
        g.regions().clearMonsterCamp("wolf-camp-valley", 3L);
        assertThat(g.regions().clearMonsterCamp("wolf-camp-valley", 3L).get("safety"))
                .isEqualTo("CONTESTED");
        Map<String, Object> safe = g.regions().clearMonsterCamp("wolf-camp-valley", 3L);
        assertThat(safe.get("safety")).isEqualTo("SAFE");
        assertThat(safe.get("worldChanged")).isEqualTo(true);
        assertThat(safe.get("unlocked")).asList().contains("shop:valley-supplies");
    }

    @Test
    public void storyBranchAndWorldEncounterDeepenCharacterBond() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.story().start(4L, "story-open-1");
        Map<String, Object> chose = g.story().choose(4L, "help");
        assertThat(chose.get("nodeId")).isEqualTo("story-help-path");
        assertThat(((Map<?, ?>) chose.get("flags")).get("route")).isEqualTo("kind");

        Map<String, Object> enc = g.encounters()
                .forceTrigger(4L, "meet-ayaka-scenic", 1, System.currentTimeMillis());
        assertThat(enc.get("triggered")).isEqualTo(true);
        assertThat(enc.get("encounter").toString()).contains("神里绫华");
    }

    @Test
    public void exploreSkillsEncourageCharacterSwap() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        assertThat(g.exploreSkills().senseHidden(5L, java.util.List.of("a")).get("error"))
                .isEqualTo("need_treasure_sense");
        g.exploreSkills().switchCharacter(5L, "char_qiqi");
        assertThat(g.exploreSkills().senseHidden(5L, java.util.List.of("a", "b")).get("ok"))
                .isEqualTo(true);
        g.exploreSkills().switchCharacter(5L, "char_ayaka");
        Map<String, Object> text = g.exploreSkills().translate(5L, "glyph-1", "ABC");
        assertThat(text.get("plainText")).isEqualTo("CBA");
    }

    @Test
    public void traverseModesAndEnvironmentInteraction() {
        MovementPredictionValidator v = new MovementPredictionValidator();
        assertThat(v.thresholdOf(MovementPredictionValidator.ActionType.GLIDE)).isNotNull();
        assertThat(v.thresholdOf(MovementPredictionValidator.ActionType.HOOK).positionDelta())
                .isGreaterThan(v.thresholdOf(MovementPredictionValidator.ActionType.CLIMB).positionDelta());

        long ts = 9_000_000L;
        assertThat(v.validate(6L, MovementPredictionValidator.ActionType.HOOK,
                20f, 10f, 0f, 0f, 0f, 0f, ts, ts).accepted()).isTrue();

        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> burn = g.environment().interact(
                "vine-barrier-1", EnvironmentInteractionService.SkillElement.FIRE, 6L);
        assertThat(burn.get("obstacleCleared")).isEqualTo(true);
        assertThat(burn.get("reaction").toString()).contains("BURN");
        assertThat(g.environment().throwStone("throw-stone-1", 200f, 200f, 6L).get("ok"))
                .isEqualTo(true);
    }

    @Test
    public void surpriseMomentsAndSideActivities() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> cave = g.surprises().tryDiscover(
                7L, 90f, 1f, 20f, null, 12, 1_000L);
        assertThat(cave.get("discovered")).isEqualTo(true);

        assertThat(g.homestead().claimPlot(7L, "plot-seaside-1", 50f, 50f).get("ok"))
                .isEqualTo(true);
        g.homestead().gatherMaterial(7L, "wood", 5);
        assertThat(g.homestead().craft(7L, "bench", Map.of("wood", 3), "wood_bench").get("ok"))
                .isEqualTo(true);

        Map<String, Object> pet = g.creatures().forceCatch(7L, "wild-slime-1", 2_000L);
        assertThat(pet.get("caught")).isEqualTo(true);
        String instanceId = String.valueOf(((Map<?, ?>) pet.get("pet")).get("instanceId"));
        assertThat(g.creatures().train(7L, instanceId).get("ok")).isEqualTo(true);

        assertThat(g.leisure().play(7L, "fish-lake-1", 80f, 40f, 30).get("ok")).isEqualTo(true);
    }

    @Test
    public void extractionMissionScoutAssaultExtract() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        assertThat(g.extraction().start(8L, "extract-ruin-cache", 3_000L).get("phase"))
                .isEqualTo(ExtractionMissionService.Phase.SCOUT.name());
        assertThat(g.extraction().scout(8L, 600f, 600f).get("phase"))
                .isEqualTo(ExtractionMissionService.Phase.ASSAULT.name());
        assertThat(g.extraction().assault(8L, 650f, 650f, 120).get("lootValue")).isEqualTo(120);
        Map<String, Object> done = g.extraction().extract(8L, 580f, 580f);
        assertThat(done.get("phase")).isEqualTo(ExtractionMissionService.Phase.COMPLETED.name());
        assertThat(done.get("grantPlans")).asList().isNotEmpty();
    }
}
