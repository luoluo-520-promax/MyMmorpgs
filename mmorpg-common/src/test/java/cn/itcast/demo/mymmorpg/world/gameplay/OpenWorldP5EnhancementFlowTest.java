package cn.itcast.demo.mymmorpg.world.gameplay;

import cn.itcast.demo.mymmorpg.world.content.ProceduralPlacementService;
import cn.itcast.demo.mymmorpg.world.explore.CollectibleService;
import cn.itcast.demo.mymmorpg.world.explore.RegionImpactService;
import cn.itcast.demo.mymmorpg.world.puzzle.PhysicsLayerService;
import cn.itcast.demo.mymmorpg.world.puzzle.PuzzleTemplateService;
import cn.itcast.demo.mymmorpg.world.puzzle.RuleTriggerService;
import cn.itcast.demo.mymmorpg.world.sideplay.WorldCookingService;
import cn.itcast.demo.mymmorpg.world.traverse.EnvironmentInteractionService;
import cn.itcast.demo.mymmorpg.world.traverse.TraverseModeService;
import cn.itcast.demo.mymmorpg.world.traverse.WorldSkillService;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P5 大世界六项补齐：解谜引擎 / 分层探索 / 角色生态位 / 区域潮汐 / 异步社交 / 量产管线。
 */
public class OpenWorldP5EnhancementFlowTest {

    @Test
    public void ruleTriggerEcaMatchesCombinedConditions() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> miss = g.rules().fire("INTERACT", Map.of(
                "element", "FIRE", "target", "VINE", "time", "DAY"));
        assertThat(miss.get("matchedCount")).isEqualTo(0);

        Map<String, Object> hit = g.rules().fire("INTERACT", Map.of(
                "element", "FIRE", "target", "VINE", "time", "NIGHT"));
        assertThat(hit.get("matchedCount")).isEqualTo(1);
        assertThat(hit.get("matched").toString()).contains("REVEAL_PATH");
        assertThat(g.rules().gadgetState("vine-barrier-1").get("state")).isEqualTo("ASH");
    }

    @Test
    public void physicsLayerAndPuzzleTemplates() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long now = 1_000_000L;
        assertThat(g.physics().freezeSurface(1, 32f, 32f, 8_000L, now).get("ok")).isEqualTo(true);
        Map<String, Object> aoi = g.physics().queryAoi(1, 32f, 32f, 2, now + 100);
        assertThat(aoi.get("count")).isEqualTo(1);
        assertThat(aoi.get("physicsLayer").toString()).contains("FROZEN");

        assertThat(g.puzzles().listTemplates().size()).isGreaterThanOrEqualTo(10);
        Map<String, Object> step1 = g.puzzles().advance(9L, "puzzle-demo-obelisk", Map.of("element", "FIRE"));
        assertThat(step1.get("solved")).isEqualTo(false);
        g.puzzles().advance(9L, "puzzle-demo-obelisk", Map.of());
        Map<String, Object> done = g.puzzles().advance(9L, "puzzle-demo-obelisk", Map.of());
        assertThat(done.get("solved")).isEqualTo(true);
        assertThat(done.get("grantPlans")).asList().isNotEmpty();
    }

    @Test
    public void collectibleTiersAndRegionProgressUnlockReputation() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        assertThat(g.collectibles().collect(10L, "chest-common-1", 100f, 0f, 100f).get("tier"))
                .isEqualTo("COMMON_CHEST");
        Map<String, Object> oculus = g.collectibles().collect(10L, "oculus-anemo-1", 180f, 20f, 160f);
        assertThat(oculus.get("exploreSkillLevel")).isEqualTo(2);
        assertThat(oculus.get("staminaCapBonus")).isEqualTo(20);

        g.regionProgress().markWaypoint(10L, "wolf-camp-valley", "wp1");
        g.regionProgress().markWaypoint(10L, "wolf-camp-valley", "wp2");
        g.regionProgress().markCollectible(10L, "wolf-camp-valley", "c1");
        g.regionProgress().markCollectible(10L, "wolf-camp-valley", "c2");
        g.regionProgress().markCollectible(10L, "wolf-camp-valley", "c3");
        g.regionProgress().markCollectible(10L, "wolf-camp-valley", "c4");
        g.regionProgress().markPuzzle(10L, "wolf-camp-valley", "p1");
        g.regionProgress().markPuzzle(10L, "wolf-camp-valley", "p2");
        g.regionProgress().markWorldQuest(10L, "wolf-camp-valley", "q1");
        g.regionProgress().markWorldQuest(10L, "wolf-camp-valley", "q2");
        Map<String, Object> status = g.regionProgress().status(10L, "wolf-camp-valley");
        assertThat(status.get("region_progress")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> rp = (Map<String, Object>) status.get("region_progress");
        assertThat((Integer) rp.get("percent")).isGreaterThanOrEqualTo(40);
        assertThat(status.get("reputationUnlocked")).asList().isNotEmpty();
    }

    @Test
    public void wonderEntryConditionAndLodMarkers() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.traverse().unlock(11L, TraverseModeService.Mode.CLIMB);
        assertThat(g.enterLandmark(11L, "sealed-sanctum").get("error"))
                .isEqualTo("entry_condition_puzzles");
        g.landmarks().markPuzzleSolved(11L, "puzzle-demo-obelisk");
        assertThat(g.enterLandmark(11L, "sealed-sanctum").get("ok")).isEqualTo(true);

        Map<String, Object> lod = g.landmarks().lodMarkers(500f, 80f, 500f, 400f);
        assertThat(lod.get("count")).isEqualTo(2);
        assertThat(lod.get("markers").toString()).contains("floating-ruin").contains("highlight");
    }

    @Test
    public void worldSkillBoundUnlockAndCookingChain() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        assertThat(g.worldSkills().unlockModeBound(
                12L, TraverseModeService.Mode.HOOK, "char_zhongli", Set.of(), g.traverse())
                .get("error")).isEqualTo("character_cannot_unlock_mode");

        Map<String, Object> ok = g.worldSkills().unlockModeBound(
                12L, TraverseModeService.Mode.GLIDE, "char_venti", Set.of(), g.traverse());
        assertThat(ok.get("ok")).isEqualTo(true);
        assertThat(g.traverse().has(12L, TraverseModeService.Mode.GLIDE)).isTrue();

        g.environment().setPartyTalents(12L, Set.of(EnvironmentInteractionService.PartyTalent.CHEF));
        assertThat(g.environment().gatherWithTalent(12L, "mint", 2, true).get("count")).isEqualTo(4);

        g.cooking().grantIngredient(12L, "fowl", 2);
        g.cooking().grantIngredient(12L, "sweet_flower", 2);
        Map<String, Object> cook = g.cooking().cook(12L, "camp-valley-1", "recipe-sweet-madame",
                110f, 90f, 2_000L);
        assertThat(cook.get("ok")).isEqualTo(true);
        assertThat(g.cooking().activeBuff(12L, 2_100L).get("active")).isEqualTo(true);
    }

    @Test
    public void regionTideBossLinkAndEventChain() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long t0 = 10_000L;
        g.regions().clearMonsterCamp("wolf-camp-valley", 13L, t0);
        g.regions().clearMonsterCamp("wolf-camp-valley", 13L, t0 + 1);
        Map<String, Object> safe = g.regions().clearMonsterCamp("wolf-camp-valley", 13L, t0 + 2);
        assertThat(safe.get("safety")).isEqualTo("SAFE");
        assertThat(safe.get("bossSummonable")).isEqualTo(true);
        assertThat(g.regions().bossLinkStatus("wolf-camp-valley").get("bossSummonable")).isEqualTo(true);

        Map<String, Object> chain = (Map<String, Object>) safe.get("eventChain");
        assertThat(chain.get("started")).isEqualTo(true);
        g.regions().advanceEventChain("wolf-camp-valley", "escort", t0 + 3);
        g.regions().advanceEventChain("wolf-camp-valley", "fight", t0 + 4);
        Map<String, Object> fin = g.regions().advanceEventChain("wolf-camp-valley", "unlock", t0 + 5);
        assertThat(fin.get("completed")).isEqualTo(true);

        // 无锚点维护 → 潮汐退回 CONTESTED
        Map<String, Object> decay = g.regions().tickDecay("wolf-camp-valley", t0 + 2 + 60_000L);
        assertThat(decay.get("decayed")).isEqualTo(true);
        assertThat(decay.get("after")).isEqualTo("CONTESTED");
    }

    @Test
    public void asyncSocialPhantomAndChannel() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> mark = g.publicMarks().place(
                20L, "wolf-camp-valley", "BOSS_ALERT", "这里有无相", 1f, 0f, 1f, true, 1L);
        assertThat(mark.get("ok")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> markView = (Map<String, Object>) mark.get("mark");
        long markId = ((Number) markView.get("markId")).longValue();
        g.publicMarks().moderate(markId, true);
        assertThat(g.publicMarks().thank(markId, 21L, 5).get("staminaGift")).isEqualTo(5);

        Map<String, Object> phantom = g.encounters().leavePhantom(
                20L, "旅人A", 1, 10f, 50f, 10f, "JUMP_OFF_CLIFF",
                List.of("POSE1", "POSE2"), 3L);
        @SuppressWarnings("unchecked")
        Map<String, Object> ph = (Map<String, Object>) phantom.get("phantom");
        assertThat(g.encounters().playPhantom(String.valueOf(ph.get("phantomId"))).get("ok"))
                .isEqualTo(true);

        g.regionChannel().joinChannel("wolf-camp-valley", 21L, "server-b");
        Map<String, Object> bc = g.regionChannel().broadcastEliteKill(
                "wolf-camp-valley", 20L, "狂风之核", 4L);
        assertThat(bc.get("joinBattleButton")).isEqualTo(true);
        assertThat(g.regionChannel().joinBattle(21L,
                ((Number) bc.get("battleId")).longValue(),
                String.valueOf(bc.get("joinBattleToken"))).get("rewardMode"))
                .isEqualTo("INDEPENDENT");
    }

    @Test
    public void proceduralPlacementAndConfigHotReload() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> fill = g.placement().fillGatherables(
                "wolf-camp-valley", List.of(), 60, 30, 2.0);
        assertThat(fill.get("placedCount")).isEqualTo(60);
        assertThat(fill.get("densityOk")).isEqualTo(true);

        Map<String, Object> dens = g.placement().validateDensity("wolf-camp-valley", 10, 1.0, 30);
        assertThat(dens.get("ok")).isEqualTo(false);

        g.configPatch().upsert("1:5:6", "CHEST", Map.of("x", 1, "z", 2));
        Map<String, Object> reload = g.configPatch().reloadCells(List.of("1:5:6"), 9L);
        assertThat(reload.get("refreshed")).asList().contains("1:5:6");
    }
}
