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
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** P11：队伍共鸣 / 传说状态机 / 图鉴 / 指挥标记 / 地形冷却 / 肉鸽命运卡。 */
public class OpenWorldP11FlowTest {

    @Test
    public void teamResonanceActivatesPyroBuff() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> r = g.teamComposition().refresh(
                1L, List.of("PYRO", "PYRO", "ANEMO", "GEO"), 1_000L);
        assertThat(r.get("ok")).isEqualTo(true);
        assertThat(r.get("resonance_id")).isEqualTo("resonance_pyro_2");
        assertThat(((Map<?, ?>) r.get("attribute_modifiers")).get("atkPct")).isEqualTo(0.25);
        assertThat(r.get("clientHint").toString()).contains("双火");
        assertThat(g.teamComposition().battleBuffs(1L, 1_000L)).isNotEmpty();
    }

    @Test
    public void storyInstanceLocksRegionAndWritesPersonalChronicle() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long now = 10_000L;
        Map<String, Object> start = g.storyStateMachine().startInstance(7L, "legend-ayaka-1", now);
        assertThat(start.get("ok")).isEqualTo(true);
        assertThat(((Map<?, ?>) start.get("notify")).get("msgId"))
                .isEqualTo(MessageId.STORY_INSTANCE_START_SC_NOTIFY);
        assertThat(g.instanceRegionLock().isTideLocked("wolf-camp-valley")).isTrue();
        assertThat(g.regions().tickDecay("wolf-camp-valley", now + 9_999_999L).get("tideLocked"))
                .isEqualTo(true);

        Map<String, Object> choose = g.storyStateMachine()
                .chooseDialogue(7L, "legend-ayaka-1", "accept", now + 1);
        assertThat(choose.get("ok")).isEqualTo(true);
        assertThat(choose.get("chroniclePersonalOnly")).isEqualTo(true);
        assertThat(g.storyStateMachine().npcAffinityOf(7L, "char_ayaka")).isEqualTo(15);

        g.storyStateMachine().advance(7L, "legend-ayaka-1", StoryStateMachine.State.COMBAT);
        Map<String, Object> done = g.storyStateMachine().complete(7L, "legend-ayaka-1", now + 2);
        assertThat(done.get("completed")).isEqualTo(true);
        assertThat(g.instanceRegionLock().isTideLocked("wolf-camp-valley")).isFalse();
    }

    @Test
    public void handbookFirstDiscoverAndOverGatherBarren() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> first = g.handbook().discover(
                3L, HandbookService.EntryKind.CREATURE, "crystal_fox", 1L, false);
        assertThat(first.get("firstDiscover")).isEqualTo(true);
        assertThat(first.get("grantPlans")).isNotNull();

        g.handbook().discover(3L, HandbookService.EntryKind.CREATURE, "anemo_slime", 2L, false);
        g.handbook().discover(3L, HandbookService.EntryKind.CREATURE, "boar", 3L, false);
        Map<String, Object> threshold = g.handbook().discover(
                3L, HandbookService.EntryKind.CREATURE, "crystal_butterfly", 4L, false);
        assertThat(((Map<?, ?>) threshold.get("progress")).get("collectorReady")).isEqualTo(true);
        assertThat(((Map<?, ?>) threshold.get("mailGrant")).get("granted")).isEqualTo(true);
        assertThat(((Map<?, ?>) threshold.get("mailGrant")).get("grantPlans")).isNotNull();
        g.handbook().discover(3L, HandbookService.EntryKind.CREATURE, "snow_fox", 5L, false);

        Map<String, Object> gather = null;
        for (int i = 0; i <= HandbookService.DAILY_GATHER_LIMIT; i++) {
            gather = g.handbook().recordGather(3L, "wolf-camp-valley", "herb", 100L);
        }
        assertThat(gather.get("overGather")).isEqualTo(true);
        assertThat(g.regions().gatherYieldMul("wolf-camp-valley", 100L)).isLessThan(1.0);
    }

    @Test
    public void quickMarkBroadcastsAndBoostsSiegePart() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.siegeWar().createSiegeRoom("siege-1", 9001L);
        Map<String, Object> mark = g.squadCommander().quickMark(
                "squad-archer-1", 9001L, 10f, 2f, 30f, "LEFT_LEG", true, "siege-1", 1_000L);
        assertThat(mark.get("ok")).isEqualTo(true);
        assertThat(((Map<?, ?>) mark.get("broadcast")).get("msgId"))
                .isEqualTo(MessageId.MARK_TARGET_SC_NOTIFY);
        assertThat(mark.get("partDamageBonus")).isEqualTo(SquadCommanderService.FOCUS_DAMAGE_BONUS);
        Map<String, Object> brk = g.siegeWar().breakPart(
                "siege-1", SiegeWarService.BossPart.LEFT_LEG, 100, 1_100L);
        assertThat(((Number) brk.get("damageMul")).doubleValue()).isEqualTo(1.15);
        assertThat(brk.get("effectiveDamage")).isEqualTo(115);
    }

    @Test
    public void bounceMushroomCooldownRejectsWithoutStaminaCost() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        SceneMoveCmd cmd = new SceneMoveCmd(
                1f, 0f, 1f, 8f, 1L, MovementType.BOUNCE, "", 0, "", "", 0f, 0f, 0f);
        Map<String, Object> ok = g.movementAdmission().admit(
                1L, cmd, 200L, 1_000L, 0f, 0f, 0f, "wolf-camp-valley", 3, 4, 60_000L);
        assertThat(ok.get("ok")).isEqualTo(true);
        assertThat(ok.get("consumed")).isEqualTo(0f);

        float staminaBefore = g.stamina().current(1L);
        Map<String, Object> reject = g.movementAdmission().admit(
                1L, cmd, 200L, 1_100L, 0f, 0f, 0f, "wolf-camp-valley", 3, 4, 60_000L);
        assertThat(reject.get("ok")).isEqualTo(false);
        assertThat(reject.get("retcode")).isEqualTo(RetCode.TERRAIN_EXHAUSTED);
        assertThat(reject.get("witherAnim")).isEqualTo(true);
        assertThat(reject.get("consumeStamina")).isEqualTo(false);
        assertThat(g.stamina().current(1L)).isEqualTo(staminaBefore);
    }

    @Test
    public void rogueFateCardsLinkWorldAndPurifyTide() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        for (int i = 0; i < 3; i++) {
            g.regionProgress().markWaypoint(9L, "wolf-camp-valley", "wp-" + i);
            g.regionProgress().markCollectible(9L, "wolf-camp-valley", "c-" + i);
            g.regionProgress().markPuzzle(9L, "wolf-camp-valley", "p-" + i);
            g.regionProgress().markWorldQuest(9L, "wolf-camp-valley", "q-" + i);
        }
        for (int i = 0; i < 20; i++) {
            g.affinity().feed(9L, "eco-fox-1",
                    cn.itcast.demo.mymmorpg.world.ecosystem.AffinityService.FOOD_ITEM, 1);
        }
        g.regions().enterChaos("wolf-camp-valley", 1L);
        Map<String, Object> start = g.rogueFate().startWithRegion(9L, 101, "wolf-camp-valley", 2_000L);
        assertThat(start.get("ok")).isEqualTo(true);
        assertThat(((List<?>) start.get("fateCards")).size()).isGreaterThanOrEqualTo(1);
        assertThat(start.get("mapLinked")).isEqualTo(true);

        Map<String, Object> settle = g.rogueFate().settleClear(9L, true, 3_000L);
        assertThat(settle.get("loopClosed")).isEqualTo(true);
        assertThat(((Map<?, ?>) settle.get("tidePurify")).get("tidePurify")).isEqualTo(25);
        assertThat(g.regions().snapshot("wolf-camp-valley").get("safety"))
                .isEqualTo(RegionImpactService.RegionSafety.CHAOS.name());
    }
}
