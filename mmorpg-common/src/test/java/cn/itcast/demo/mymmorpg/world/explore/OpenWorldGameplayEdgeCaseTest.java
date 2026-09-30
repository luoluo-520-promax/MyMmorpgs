package cn.itcast.demo.mymmorpg.world.explore;

import cn.itcast.demo.mymmorpg.world.gameplay.OpenWorldGameplayFacade;
import cn.itcast.demo.mymmorpg.world.narrative.ExplorationSkillService;
import cn.itcast.demo.mymmorpg.world.narrative.StoryBranchService;
import cn.itcast.demo.mymmorpg.world.narrative.WorldEncounterService;
import cn.itcast.demo.mymmorpg.world.sideplay.CreatureCatchService;
import cn.itcast.demo.mymmorpg.world.sideplay.ExtractionMissionService;
import cn.itcast.demo.mymmorpg.world.sideplay.LeisureActivityService;
import cn.itcast.demo.mymmorpg.world.sideplay.OpenWorldHomesteadService;
import cn.itcast.demo.mymmorpg.world.traverse.EnvironmentInteractionService;
import cn.itcast.demo.mymmorpg.world.traverse.TraverseModeService;
import cn.itcast.demo.mymmorpg.world.traverse.WorldSurpriseService;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 各子系统边界与幂等：重复发现、冷却、失败撤离、制作材料不足等。
 */
public class OpenWorldGameplayEdgeCaseTest {

    @Test
    public void oneShotExplorationRejectsReplay() {
        ExplorationRewardLoopService svc = new ExplorationRewardLoopService();
        svc.register(new ExplorationPoint(
                "p1", 1, 1, ExplorationContentKind.COLLECTIBLE,
                0, 0, 0, 5f, List.of(Map.of("itemId", "x", "count", 1)), null, true));
        assertThat(svc.discover(1L, "p1", 0, 0, 0, 1L).get("ok")).isEqualTo(true);
        assertThat(svc.discover(1L, "p1", 0, 0, 0, 2L).get("error")).isEqualTo("already_discovered");
    }

    @Test
    public void regionSafetyTransitionsAndIdempotentZeroCamps() {
        RegionImpactService regions = new RegionImpactService();
        regions.register(new RegionImpactService.RegionProfile(
                "r1", "测试谷", 1, 2, List.of("portal:r1")));
        assertThat(regions.clearMonsterCamp("r1", 1L).get("safety")).isEqualTo("CONTESTED");
        assertThat(regions.clearMonsterCamp("r1", 1L).get("safety")).isEqualTo("SAFE");
        Map<String, Object> extra = regions.clearMonsterCamp("r1", 1L);
        assertThat(extra.get("campsRemaining")).isEqualTo(0);
        assertThat(extra.get("safety")).isEqualTo("SAFE");
    }

    @Test
    public void landmarkCannotAdvanceWithoutEnter() {
        LandmarkWonderService land = new LandmarkWonderService();
        land.register(new LandmarkWonder(
                "l1", "塔", 1, 1, 0, 0, 0, 2, List.of(), 1, List.of(), List.of()));
        assertThat(land.advanceLayer(1L, "l1").get("error")).isEqualTo("not_entered");
        land.enter(1L, "l1", Set.of());
        land.advanceLayer(1L, "l1");
        assertThat(land.advanceLayer(1L, "l1").get("cleared")).isEqualTo(true);
        assertThat(land.advanceLayer(1L, "l1").get("error")).isEqualTo("already_cleared");
    }

    @Test
    public void storyInvalidChoiceAndEncounterUnknown() {
        StoryBranchService story = new StoryBranchService();
        story.register(new StoryBranchService.StoryNode(
                "n1", "t", "b",
                List.of(new StoryBranchService.StoryNode.Choice("a", "A", "n2", Map.of()))));
        story.register(new StoryBranchService.StoryNode("n2", "t2", "b2", List.of()));
        story.start(1L, "n1");
        assertThat(story.choose(1L, "nope").get("error")).isEqualTo("invalid_choice");

        WorldEncounterService enc = new WorldEncounterService();
        assertThat(enc.forceTrigger(1L, "missing", 1, 1L).get("error"))
                .isEqualTo("encounter_not_found");
    }

    @Test
    public void exploreSkillGatesAndHomesteadCraftCost() {
        ExplorationSkillService skills = new ExplorationSkillService();
        skills.bindCharacterSkills("c1", Set.of(ExplorationSkillService.ExploreSkill.TREASURE_SENSE));
        assertThat(skills.translate(1L, "g", "ab").get("error")).isEqualTo("need_ancient_translate");
        skills.switchCharacter(1L, "c1");
        assertThat(skills.senseHidden(1L, List.of("x")).get("ok")).isEqualTo(true);

        OpenWorldHomesteadService home = new OpenWorldHomesteadService();
        home.registerPlot(new OpenWorldHomesteadService.Plot("plot", 1, 0, 0, 0, 10));
        home.claimPlot(1L, "plot", 0, 0);
        home.gatherMaterial(1L, "wood", 1);
        assertThat(home.craft(1L, "desk", Map.of("wood", 5), "desk").get("error"))
                .isEqualTo("insufficient_material");
    }

    @Test
    public void extractionOutOfZoneAndLeisureUnknownZone() {
        ExtractionMissionService extract = new ExtractionMissionService();
        extract.register(new ExtractionMissionService.MissionDef(
                "m1", "任务", 1, 0, 0, 100, 100, 50, 50, 5f, List.of()));
        extract.start(1L, "m1", 1L);
        assertThat(extract.scout(1L, 99f, 99f).get("error")).isEqualTo("out_of_phase_zone");

        LeisureActivityService leisure = new LeisureActivityService();
        assertThat(leisure.play(1L, "nope", 0, 0, 1).get("error")).isEqualTo("zone_not_found");
    }

    @Test
    public void creatureCatchAndSurpriseDuplicate() {
        CreatureCatchService creatures = new CreatureCatchService();
        creatures.spawn(new CreatureCatchService.WildCreature(
                "w1", "fox", 1, 0, 0, 0, 1, 5, 20));
        assertThat(creatures.forceCatch(1L, "w1", 10L).get("caught")).isEqualTo(true);
        assertThat(creatures.forceCatch(1L, "w1", 11L).get("error")).isEqualTo("creature_not_found");

        WorldSurpriseService surprise = new WorldSurpriseService();
        surprise.register(new WorldSurpriseService.SurpriseDef(
                "s1", "彩蛋", WorldSurpriseService.TriggerType.PROXIMITY, null,
                0, 0, 0, 5f, "gem", 1));
        assertThat(surprise.tryDiscover(1L, 0, 0, 0, null, 0, 1L).get("discovered")).isEqualTo(true);
        assertThat(surprise.tryDiscover(1L, 0, 0, 0, null, 0, 2L).get("discovered")).isEqualTo(false);
    }

    @Test
    public void facadeStatusAndTraverseEnvironmentCatalog() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        assertThat(g.statusOverview().get("ok")).isEqualTo(true);
        g.traverse().unlock(9L, TraverseModeService.Mode.CLIMB);
        assertThat(g.traverse().has(9L, TraverseModeService.Mode.CLIMB)).isTrue();

        EnvironmentInteractionService env = g.environment();
        Map<String, Object> ice = env.interact(
                "vine-barrier-1", EnvironmentInteractionService.SkillElement.CRYO, 9L);
        // 藤蔓非火不燃
        assertThat(ice.get("obstacleCleared")).isEqualTo(false);
    }
}
