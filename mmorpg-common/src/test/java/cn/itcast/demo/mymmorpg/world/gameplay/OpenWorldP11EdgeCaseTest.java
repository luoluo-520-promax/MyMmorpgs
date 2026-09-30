package cn.itcast.demo.mymmorpg.world.gameplay;

import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.sync.MovementType;
import cn.itcast.demo.mymmorpg.sync.SceneMoveCmd;
import cn.itcast.demo.mymmorpg.world.ai.SquadCommanderService;
import cn.itcast.demo.mymmorpg.world.endgame.SiegeWarService;
import cn.itcast.demo.mymmorpg.world.explore.RegionImpactService;
import cn.itcast.demo.mymmorpg.world.narrative.StoryStateMachine;
import cn.itcast.demo.mymmorpg.world.sideplay.HandbookService;
import cn.itcast.demo.mymmorpg.world.team.TeamCompositionService;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** P11 边界与负路径：非法参数、权限、状态机跳转、冷却过期、阈值门槛。 */
public class OpenWorldP11EdgeCaseTest {

    @Test
    public void resonanceNeedsTwoSameElement_emptyTeamOk() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> empty = g.teamComposition().refresh(1L, List.of(), 1L);
        assertThat(empty.get("ok")).isEqualTo(true);
        assertThat(empty.get("resonance_id")).isEqualTo("");
        assertThat(g.teamComposition().battleBuffs(1L, 1L)).isEmpty();

        Map<String, Object> single = g.teamComposition().refresh(
                1L, List.of("PYRO", "HYDRO", "ANEMO", "GEO"), 1L);
        assertThat(single.get("resonance_id")).isEqualTo("");
    }

    @Test
    public void fourPyroDominatesTwoPyroRule() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> r = g.teamComposition().refresh(
                2L, List.of("FIRE", "PYRO", "FIRE", "PYRO"), 1L);
        assertThat(r.get("resonance_id")).isEqualTo("resonance_pyro_4");
        assertThat(((Map<?, ?>) r.get("attribute_modifiers")).get("atkPct")).isEqualTo(0.40);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) r.get("resonances");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("count")).isEqualTo(4);
    }

    @Test
    public void resonanceExpiresWithDuration() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.teamComposition().refresh(3L, List.of("CRYO", "CRYO", "ANEMO", "GEO"), 1_000L, 500L);
        assertThat(g.teamComposition().activeOf(3L, 1_200L)).isNotEmpty();
        assertThat(g.teamComposition().activeOf(3L, 1_600L)).isEmpty();
        assertThat(g.teamComposition().battleBuffs(3L, 1_600L)).isEmpty();
    }

    @Test
    public void storyRejectsUnknownAndInvalidTransition() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        assertThat(g.storyStateMachine().startInstance(1L, "no-such", 1L).get("error"))
                .isEqualTo("story_not_found");
        assertThat(g.storyStateMachine().chooseDialogue(1L, "legend-ayaka-1", "accept", 1L)
                .get("error")).isEqualTo("story_not_active");

        g.storyStateMachine().startInstance(1L, "legend-ayaka-1", 1L);
        Map<String, Object> badJump = g.storyStateMachine()
                .advance(1L, "legend-ayaka-1", StoryStateMachine.State.REWARD);
        assertThat(badJump.get("ok")).isEqualTo(false);
        assertThat(badJump.get("error")).isEqualTo("invalid_transition");

        Map<String, Object> badOpt = g.storyStateMachine()
                .chooseDialogue(1L, "legend-ayaka-1", "nope", 2L);
        assertThat(badOpt.get("error")).isEqualTo("invalid_option");

        Map<String, Object> dup = g.storyStateMachine().startInstance(1L, "legend-ayaka-1", 3L);
        assertThat(dup.get("error")).isEqualTo("story_already_active");
    }

    @Test
    public void declineLowersAffinityAndUnlocksDialogueTiers() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.storyStateMachine().startInstance(8L, "legend-ayaka-1", 1L);
        g.storyStateMachine().chooseDialogue(8L, "legend-ayaka-1", "decline", 2L);
        assertThat(g.storyStateMachine().npcAffinityOf(8L, "char_ayaka")).isEqualTo(-2);
        assertThat(g.storyStateMachine().dialogueLibrary(8L, "char_ayaka")).hasSize(1);

        // 累计到友好档
        g.storyStateMachine().complete(8L, "legend-ayaka-1", 3L);
        g.storyStateMachine().register(new StoryStateMachine.StoryDef(
                "legend-ayaka-2", "char_ayaka", "wolf-camp-valley", "再邀",
                List.of(
                        new StoryStateMachine.DialogueOption("yes", "再帮一次", 20, ""),
                        new StoryStateMachine.DialogueOption("no", "下次", 0, "")),
                21));
        g.storyStateMachine().startInstance(8L, "legend-ayaka-2", 4L);
        g.storyStateMachine().chooseDialogue(8L, "legend-ayaka-2", "yes", 5L);
        assertThat(g.storyStateMachine().npcAffinityOf(8L, "char_ayaka")).isEqualTo(18);
        assertThat(g.storyStateMachine().dialogueLibrary(8L, "char_ayaka").size())
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    public void handbookRejectsBlankAndIdempotentMail() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        assertThat(g.handbook().discover(1L, HandbookService.EntryKind.CREATURE, "", 1L, false)
                .get("error")).isEqualTo("entry_required");

        g.handbook().discover(1L, HandbookService.EntryKind.CREATURE, "crystal_fox", 1L, false);
        Map<String, Object> again = g.handbook().discover(
                1L, HandbookService.EntryKind.CREATURE, "crystal_fox", 2L, true);
        assertThat(again.get("firstDiscover")).isEqualTo(false);
        assertThat(again.get("perfectCookCount")).isEqualTo(1);

        Map<String, Object> hitThreshold = null;
        for (String id : List.of("anemo_slime", "boar", "crystal_butterfly")) {
            hitThreshold = g.handbook().discover(1L, HandbookService.EntryKind.CREATURE, id, 3L, false);
        }
        assertThat(((Map<?, ?>) hitThreshold.get("progress")).get("collectorReady")).isEqualTo(true);
        assertThat(((Map<?, ?>) hitThreshold.get("mailGrant")).get("granted")).isEqualTo(true);
        Map<String, Object> mail2 = g.handbook().tryGrantCollectorNamecard(1L, 11L);
        assertThat(mail2.get("alreadyGranted")).isEqualTo(true);
        assertThat(mail2.get("granted")).isEqualTo(false);
    }

    @Test
    public void overGatherOnlyAfterDailyLimit() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> mid = null;
        for (int i = 0; i < HandbookService.DAILY_GATHER_LIMIT; i++) {
            mid = g.handbook().recordGather(2L, "wolf-camp-valley", "ore", 50L);
        }
        assertThat(mid.get("overGather")).isEqualTo(false);
        assertThat(g.regions().gatherYieldMul("wolf-camp-valley", 50L)).isEqualTo(1.0);

        Map<String, Object> over = g.handbook().recordGather(2L, "wolf-camp-valley", "ore", 50L);
        assertThat(over.get("overGather")).isEqualTo(true);
        assertThat(over.get("debuff")).isEqualTo("RESOURCE_BARREN");
        assertThat(g.regions().gatherYieldMul("wolf-camp-valley", 50L)).isEqualTo(0.5);
        // 过期后恢复
        assertThat(g.regions().gatherYieldMul(
                "wolf-camp-valley", 50L + HandbookService.BARREN_DURATION_MS + 1)).isEqualTo(1.0);
    }

    @Test
    public void quickMarkRequiresLeaderAndFocusExpires() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        assertThat(g.squadCommander().quickMark(
                "no-squad", 1L, 0, 0, 0, "CORE", true, "", 1L).get("error"))
                .isEqualTo("squad_not_found");
        assertThat(g.squadCommander().quickMark(
                "squad-archer-1", 1L, 0, 0, 0, "CORE", true, "", 1L).get("error"))
                .isEqualTo("not_squad_leader");

        g.siegeWar().createSiegeRoom("s-edge", 9001L);
        Map<String, Object> mark = g.squadCommander().quickMark(
                "squad-archer-1", 9001L, 1, 2, 3, "CORE", true, "s-edge", 1_000L);
        assertThat(mark.get("ok")).isEqualTo(true);
        assertThat(((Map<?, ?>) mark.get("broadcast")).get("msgId"))
                .isEqualTo(MessageId.MARK_TARGET_SC_NOTIFY);

        assertThat(g.squadCommander().focusDamageMul("squad-archer-1", "CORE", 1_100L))
                .isEqualTo(1.0 + SquadCommanderService.FOCUS_DAMAGE_BONUS);
        assertThat(g.squadCommander().focusDamageMul("squad-archer-1", "LEFT_LEG", 1_100L))
                .isEqualTo(1.0);
        assertThat(g.squadCommander().focusDamageMul(
                "squad-archer-1", "CORE", 1_000L + SquadCommanderService.FOCUS_DURATION_MS + 1))
                .isEqualTo(1.0);

        Map<String, Object> expired = g.siegeWar().breakPart(
                "s-edge", SiegeWarService.BossPart.CORE, 100,
                1_000L + SquadCommanderService.FOCUS_DURATION_MS + 1);
        assertThat(((Number) expired.get("damageMul")).doubleValue()).isEqualTo(1.0);
        assertThat(expired.get("effectiveDamage")).isEqualTo(100);
    }

    @Test
    public void terrainCooldownIndependentCellsAndTtlExpiry() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        SceneMoveCmd bounce = new SceneMoveCmd(
                0, 0, 0, 8f, 1L, MovementType.BOUNCE, "", 0, "", "", 0, 0, 0);
        Map<String, Object> a = g.movementAdmission().admit(
                1L, bounce, 100L, 1_000L, 0, 0, 0, "wolf-camp-valley", 1, 1, 30_000L);
        assertThat(a.get("ok")).isEqualTo(true);
        Map<String, Object> otherCell = g.movementAdmission().admit(
                1L, bounce, 100L, 1_100L, 0, 0, 0, "wolf-camp-valley", 2, 2, 30_000L);
        assertThat(otherCell.get("ok")).isEqualTo(true);

        Map<String, Object> same = g.movementAdmission().admit(
                1L, bounce, 100L, 1_200L, 0, 0, 0, "wolf-camp-valley", 1, 1, 30_000L);
        assertThat(same.get("retcode")).isEqualTo(RetCode.TERRAIN_EXHAUSTED);

        Map<String, Object> afterTtl = g.movementAdmission().admit(
                1L, bounce, 100L, 1_000L + 30_000L + 1, 0, 0, 0, "wolf-camp-valley", 1, 1, 30_000L);
        assertThat(afterTtl.get("ok")).isEqualTo(true);
    }

    @Test
    public void rogueRequiresRegionAndPurifyConvertsChaos() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        assertThat(g.rogueFate().startWithRegion(1L, 1, "", 1L).get("error"))
                .isEqualTo("region_id_required");
        assertThat(g.rogueFate().settleClear(1L, true, 1L).get("error"))
                .isEqualTo("no_fate_session");

        // 低探索：无探索卡，仍可开局
        Map<String, Object> low = g.rogueFate().startWithRegion(5L, 1, "wolf-camp-valley", 1L);
        assertThat(low.get("ok")).isEqualTo(true);
        assertThat(((List<?>) low.get("fateCards"))).isEmpty();
        g.rogueFate().settleClear(5L, false, 2L);

        g.regions().enterChaos("wolf-camp-valley", 10L);
        g.rogueFate().startWithRegion(6L, 2, "wolf-camp-valley", 11L);
        // 累计净化到阈值
        for (int i = 0; i < 4; i++) {
            g.rogueFate().startWithRegion(6L, 2 + i, "wolf-camp-valley", 12L + i);
            g.rogueFate().settleClear(6L, true, 20L + i);
        }
        assertThat(g.regions().snapshot("wolf-camp-valley").get("safety"))
                .isEqualTo(RegionImpactService.RegionSafety.SAFE.name());
        assertThat(TeamCompositionService.Element.parse("ice")).isEqualTo(TeamCompositionService.Element.CRYO);
        assertThat(MessageId.QUICK_MARK_CS_REQ).isEqualTo(2230);
        assertThat(MessageId.STORY_INSTANCE_START_SC_NOTIFY).isEqualTo(2450);
    }
}
