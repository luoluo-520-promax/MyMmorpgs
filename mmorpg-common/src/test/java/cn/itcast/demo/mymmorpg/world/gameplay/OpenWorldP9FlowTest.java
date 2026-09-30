package cn.itcast.demo.mymmorpg.world.gameplay;

import cn.itcast.demo.mymmorpg.sync.MovementType;
import cn.itcast.demo.mymmorpg.sync.SceneMoveCmd;
import cn.itcast.demo.mymmorpg.world.battle.InputBufferService;
import cn.itcast.demo.mymmorpg.world.battle.PoiseService;
import cn.itcast.demo.mymmorpg.world.content.RareEliteSpawnService;
import cn.itcast.demo.mymmorpg.world.sideplay.HomelandGuardService;
import cn.itcast.demo.mymmorpg.world.traverse.FallAttackValidator;
import cn.itcast.demo.mymmorpg.world.traverse.UnderwaterPhysicsService;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** P9：长线养成 / 立体战斗 / 稀有精英 / 家园博弈 / 韧性预输入 / 区域觉醒。 */
public class OpenWorldP9FlowTest {

    @Test
    public void constellationOverrideChangesSkillMechanics() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.constellation().creditMaterial(1L, 5);
        Map<String, Object> unlock = g.constellation().unlock(1L, "char-warden", 1);
        assertThat(unlock.get("ok")).isEqualTo(true);
        assertThat(unlock.get("constellationLv")).isEqualTo(1);
        assertThat(((Map<?, ?>) unlock.get("attributeRecalc")).get("msgId"))
                .isEqualTo(cn.itcast.demo.mymmorpg.world.progression.ConstellationService.MSG_ATTRIBUTE_RECALC_SC_NOTIFY);

        Map<String, Object> cast = g.skillCast().resolveCast(1L, "char-warden", "skill-evil-warding", 1000L);
        assertThat(cast.get("source")).isEqualTo("constellation_buff_override");
        assertThat(cast.get("cooldownMs")).isEqualTo(6_000);
        assertThat(cast.get("projectileCount")).isEqualTo(3);
        assertThat(cast.get("hitCount")).isEqualTo(2);
        assertThat(cast.get("effectTag")).isEqualTo("EvilWarding");
    }

    @Test
    public void fallHeavyRequiresHeightAndAppliesLandingLag() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long now = 5_000_000L;
        Map<String, Object> miss = g.fallAttack().validateFallHeavy(
                2L, 3f, -4f, false, 100, 1, 0, 10, 0, now);
        assertThat(miss.get("ok")).isEqualTo(false);
        assertThat(miss.get("error")).isEqualTo("fall_condition_not_met");

        Map<String, Object> hit = g.fallAttack().validateFallHeavy(
                2L, 12f, -5f, false, 100, 1, 10, 0, 10, now);
        assertThat(hit.get("ok")).isEqualTo(true);
        assertThat(hit.get("action")).isEqualTo(FallAttackValidator.ACTION_FALL_HEAVY);
        assertThat(((Number) hit.get("damage")).intValue()).isGreaterThan(100);
        assertThat(((Number) hit.get("landingLagMs")).intValue()).isGreaterThan(0);
        assertThat(g.fallAttack().inLandingLag(2L, now + 50)).isTrue();
    }

    @Test
    public void underwaterDashShrinksAndRewritesOverload() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.underwater().enterUnderwater(3L, 1);
        long now = System.currentTimeMillis();
        SceneMoveCmd dash = new SceneMoveCmd(0, 0, 10, 10f, now, MovementType.DASH, "");
        Map<String, Object> admit = g.movementAdmission().admit(3L, dash, 1000L, now);
        assertThat(admit.get("underwater")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> dist = (Map<String, Object>) admit.get("dashDistance");
        assertThat(((Number) dist.get("factor")).doubleValue())
                .isEqualTo(UnderwaterPhysicsService.DASH_DISTANCE_FACTOR);

        Map<String, Object> rx = g.underwater().filterElementReaction(3L, "OVERLOAD", "PYRO");
        assertThat(rx.get("rewritten")).isEqualTo(true);
        assertThat(rx.get("reaction")).isEqualTo("ELECTRO_CONDUCT_DOT");
    }

    @Test
    public void rareEliteSpawnsAfterKillThreshold() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long now = 6_000_000L;
        Map<String, Object> last = Map.of();
        for (int i = 0; i < RareEliteSpawnService.KILL_THRESHOLD; i++) {
            last = g.rareElites().recordKill("3_4", now + i);
        }
        assertThat(last.get("spawned")).isEqualTo(true);
        assertThat(g.rareElites().gridStateOf("3_4"))
                .isEqualTo(RareEliteSpawnService.STATE_GRID_INFESTATION);
        @SuppressWarnings("unchecked")
        Map<String, Object> aoi = (Map<String, Object>) last.get("aoiBroadcast");
        assertThat(aoi.get("event")).isEqualTo(RareEliteSpawnService.EVENT_RARE_ELITE_APPEAR);
        assertThat(aoi.get("radiusM")).isEqualTo(RareEliteSpawnService.BROADCAST_RADIUS_M);
    }

    @Test
    public void creatureFollowHarvestAutoLootsBonus() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> caught = g.creatures().forceCatch(7L, "wild-fox-1", 7_000_000L);
        @SuppressWarnings("unchecked")
        Map<String, Object> pet = (Map<String, Object>) caught.get("pet");
        g.creatureUtility().enableFollowHarvest(7L, String.valueOf(pet.get("instanceId")));
        Map<String, Object> collect = g.collectWithDynamicLoot(
                7L, "chest-common-1", 100f, 0f, 100f, 3, 0.5f);
        assertThat(collect.get("bonusApplied")).isEqualTo(true);
        assertThat(collect.get("bonusGrantPlans")).isNotNull();
    }

    @Test
    public void homelandGuardBlocksStealWithSpeedDebuff() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long now = 8_000_000L;
        g.homelandGuard().creditCoins(11L, 100);
        g.homelandGuard().markReady("crop-ready-1", 100);
        g.homelandGuard().installGuard(11L, "plot-guard-1", 10);
        Map<String, Object> steal = g.homelandGuard().steal(99L, "plot-guard-1", "crop-ready-1", now);
        assertThat(steal.get("ok")).isEqualTo(false);
        assertThat(steal.get("debuff")).isEqualTo(HomelandGuardService.DEBUFF_SPEED_DOWN);
        assertThat(g.homelandGuard().movementPenalty(99L, now + 1000).get("debuffed")).isEqualTo(true);
    }

    @Test
    public void homelandStealCapsAtTwentyPercentWithoutGuard() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.homelandGuard().markReady("crop-ready-2", 100);
        Map<String, Object> steal = g.homelandGuard().steal(88L, "plot-open", "crop-ready-2", 9_000_000L);
        assertThat(steal.get("ok")).isEqualTo(true);
        assertThat(steal.get("amount")).isEqualTo(20);
    }

    @Test
    public void phantomGuideAfterThreeFails() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> leave = g.encounters().leavePhantom(
                50L, "通关侠", 1, 10, 0, 10, "SOLVE", java.util.List.of("W", "W", "I"), 10_000_000L);
        @SuppressWarnings("unchecked")
        Map<String, Object> ph = (Map<String, Object>) leave.get("phantom");
        String phantomId = String.valueOf(ph.get("phantomId"));
        g.encounters().recordPuzzleClearPhantom("puzzle-hard", phantomId);
        g.encounters().recordPuzzleFail(60L, "puzzle-hard");
        g.encounters().recordPuzzleFail(60L, "puzzle-hard");
        Map<String, Object> third = g.encounters().recordPuzzleFail(60L, "puzzle-hard");
        assertThat(third.get("recommend")).isEqualTo(true);
        assertThat(((Map<?, ?>) third.get("guideMark")).get("event"))
                .isEqualTo("PHANTOM_GUIDE_MARK");
        assertThat(((Map<?, ?>) third.get("guideMark")).get("skipPuzzle")).isEqualTo(false);
    }

    @Test
    public void poiseBreakTriggersExecutionAndUltimateBlocksInterrupt() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.poise().initEntity(9001L, 50, 5);
        Map<String, Object> hit = g.poise().applyPoiseDamage(1L, 9001L, 60, 11_000_000L);
        assertThat(hit.get("event")).isEqualTo(PoiseService.EVENT_HIT_CONFIRM);
        assertThat(hit.get("execution")).isEqualTo(PoiseService.EVENT_EXECUTION_TRIGGER);
        assertThat(hit.get("stagger_level")).isEqualTo(2);

        g.poise().initEntity(1L, 100, 5);
        g.poise().enterUltimate(1L, 11_000_000L);
        Map<String, Object> blocked = g.poise().applyPoiseDamage(9001L, 1L, 999, 11_000_100L);
        assertThat(blocked.get("superArmor")).isEqualTo(true);
        assertThat(blocked.get("stagger_level")).isEqualTo(0);
        assertThat(blocked.get("interrupted")).isEqualTo(false);
    }

    @Test
    public void inputBufferKeepsPostDodgeWindowThenClears() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long now = 12_000_000L;
        g.inputBuffer().armAfterDodgeWindow(4L, "atk-p9", now, 200);
        Map<String, Object> q = g.inputBuffer().enqueue(4L, "HEAVY", now + 250);
        assertThat(q.get("ok")).isEqualTo(true);
        Map<String, Object> consumed = g.inputBuffer().consume(4L, now + 260);
        assertThat(consumed.get("action")).isEqualTo("HEAVY");
        Map<String, Object> expired = g.inputBuffer().enqueue(4L, "SKILL", now + 400);
        assertThat(expired.get("event")).isEqualTo(InputBufferService.EVENT_INPUT_CLEAR);
    }

    @Test
    public void regionMasteryBroadcastsAndStacksWorldBuff() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long pid = 77L;
        String region = "wolf-camp-valley";
        for (int i = 0; i < 5; i++) {
            g.regionProgress().markWaypoint(pid, region, "wp-" + i);
        }
        for (int i = 0; i < 8; i++) {
            g.regionProgress().markCollectible(pid, region, "c-" + i);
        }
        for (int i = 0; i < 4; i++) {
            g.regionProgress().markPuzzle(pid, region, "p-" + i);
        }
        for (int i = 0; i < 3; i++) {
            g.regionProgress().markWorldQuest(pid, region, "q-" + i);
        }
        Map<String, Object> awaken = g.regionAwakening().checkAndAwaken(pid, "探索者", region, 13_000_000L);
        assertThat(awaken.get("awakened")).isEqualTo(true);
        assertThat(awaken.get("event")).isEqualTo("REGION_MASTERY");
        assertThat(g.regionAwakening().totalAttrBonus(pid, 13_000_000L)).isEqualTo(0.05);
    }

    @Test
    public void worldCoreUnlocksAfterAllWonders() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long pid = 88L;
        assertThat(g.worldCore().createCoopRoom(pid, 14_000_000L).get("ok")).isEqualTo(false);
        g.worldCore().markWonderComplete(pid, "floating-ruin");
        g.worldCore().markWonderComplete(pid, "sealed-sanctum");
        Map<String, Object> room = g.worldCore().createCoopRoom(pid, 14_000_100L);
        assertThat(room.get("ok")).isEqualTo(true);
        assertThat(room.get("dungeonId")).isEqualTo("WORLD_CORE");
        assertThat(room.get("maxMembers")).isEqualTo(4);
    }

    @Test
    public void weaponRefineTriggerDecoupledFromConstellation() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.battleTriggers().refineWeapon("wpn-1", 2, "COORDINATED_ATK", 0.3);
        Map<String, Object> fire = g.battleTriggers().onHit("b1", "wpn-1", 1L, 2L, 100, 15_000_000L);
        assertThat(fire.get("triggered")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> effect = (Map<String, Object>) fire.get("effect");
        assertThat(effect.get("decoupledFrom")).isEqualTo("constellation");
        assertThat(effect.get("extraDamage")).isEqualTo(30);
    }
}
