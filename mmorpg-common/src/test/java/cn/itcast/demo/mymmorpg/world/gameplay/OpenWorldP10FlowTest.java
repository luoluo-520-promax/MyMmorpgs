package cn.itcast.demo.mymmorpg.world.gameplay;

import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.world.ai.SquadCommanderService;
import cn.itcast.demo.mymmorpg.world.economy.AuctionHouseService;
import cn.itcast.demo.mymmorpg.world.economy.CraftingTreeService;
import cn.itcast.demo.mymmorpg.world.ecosystem.AffinityService;
import cn.itcast.demo.mymmorpg.world.ecosystem.EcoCarryingCapacity;
import cn.itcast.demo.mymmorpg.world.ecosystem.EcosystemBehaviorService;
import cn.itcast.demo.mymmorpg.world.endgame.SiegeWarService;
import cn.itcast.demo.mymmorpg.world.explore.CollectibleService;
import cn.itcast.demo.mymmorpg.world.narrative.RegionTugOfWarService;
import cn.itcast.demo.mymmorpg.world.puzzle.TerrainMutationService;
import cn.itcast.demo.mymmorpg.world.puzzle.WorldMutabilityService;
import cn.itcast.demo.mymmorpg.world.traverse.ClimbAttackService;
import cn.itcast.demo.mymmorpg.world.traverse.GrapplePhysicsService;
import cn.itcast.demo.mymmorpg.world.traverse.VehicleCombatService;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** P10：生态亲密度 / 钩锁载具 / 地貌元素 / 纪元拉锯 / 攻城非对称 / 制造交易 / 命中反馈 / 集群 AI。 */
public class OpenWorldP10FlowTest {

    @Test
    public void ecosystemFearAndTickPreferEcoAi() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> flee = g.ecosystem().onPlayerProximity("eco-fox-1", 5f, false);
        assertThat(flee.get("state")).isEqualTo(EcosystemBehaviorService.EcoState.FLEE.name());
        Map<String, Object> tick = g.ecosystem().tick("eco-fox-1", 22, 20_000L, false);
        assertThat(tick.get("ok")).isEqualTo(true);
        assertThat(tick.get("behaviorTree")).isEqualTo("behavior_tree_eco.xml");
        assertThat(g.ecosystem().tick("eco-fox-1", 12, 20_100L, true).get("skipped")).isEqualTo(true);
    }

    @Test
    public void affinityUnlocksTreasureAndAlert() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        for (int i = 0; i < 15; i++) {
            g.affinity().feed(1L, "eco-fox-1", AffinityService.FOOD_ITEM, 1);
        }
        assertThat(g.affinity().get(1L, "eco-fox-1")).isGreaterThanOrEqualTo(AffinityService.THRESHOLD_TREASURE);
        Map<String, Object> hint = g.affinity().tryTreasureHint(1L, "eco-fox-1", 100f, 100f, 1_000L, 1);
        assertThat(hint.get("hinted")).isEqualTo(true);
        assertThat(g.collectibles().visibilityOf("chest-hidden-1"))
                .isEqualTo(CollectibleService.Visibility.HIDDEN);
        for (int i = 0; i < 20; i++) {
            g.affinity().feed(1L, "eco-fox-1", AffinityService.FOOD_ITEM, 1);
        }
        Map<String, Object> alert = g.affinity().pushCreatureAlert(1L, "eco-fox-1", "elite-1", "3_4");
        assertThat(alert.get("ok")).isEqualTo(true);
        assertThat(((Map<?, ?>) alert.get("notify")).get("msgId"))
                .isEqualTo(MessageId.CREATURE_ALERT_SC_NOTIFY);
    }

    @Test
    public void ecoCarryingCapacityLowersSpawnAndCrop() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        for (int i = 0; i < EcoCarryingCapacity.OVERKILL_THRESHOLD; i++) {
            g.placement().carrying().recordKill("wolf-camp-valley", "boar");
        }
        Map<String, Object> fill = g.placement().fillGatherables(
                "wolf-camp-valley", List.of(), 40, 1, 2, "boar");
        assertThat(((Number) fill.get("placedCount")).intValue()).isLessThan(40);
        assertThat(g.placement().carrying().cropYieldMultiplier("wolf-camp-valley")).isEqualTo(0.5);
    }

    @Test
    public void grapplePullAndSwingKick() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> light = g.grapplePhysics().pullEnemy(
                1L, 99L, GrapplePhysicsService.BodyMass.LIGHT, 40, 1L);
        assertThat(light.get("outcome")).isEqualTo("ENEMY_PULLED_TO_PLAYER");
        assertThat(light.get("enemyHardStun")).isEqualTo(true);
        Map<String, Object> heavy = g.grapplePhysics().pullEnemy(
                1L, 98L, GrapplePhysicsService.BodyMass.HEAVY, 40, 1L);
        assertThat(heavy.get("outcome")).isEqualTo("PLAYER_PULLED_TO_ENEMY");
        g.grapplePhysics().recordSwingSpeed(1L, 24);
        Map<String, Object> kick = g.grapplePhysics().swingKick(1L, 100, true);
        assertThat(((Number) kick.get("damage")).intValue()).isEqualTo(200);
    }

    @Test
    public void vehicleCombatEjectGlideAndClimbAttack() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.vehicleCombat().board(2L, "cart-coast-1");
        Map<String, Object> cast = g.vehicleCombat().castSkill(2L, "vehicle_ram");
        assertThat(cast.get("msgId")).isEqualTo(MessageId.VEHICLE_CAST_SKILL_CS_REQ);
        Map<String, Object> destroy = g.vehicleCombat().applyPoiseDamage(2L, 200f, 18f);
        assertThat(destroy.get("event")).isEqualTo(VehicleCombatService.EVENT_EJECT_GLIDE);
        g.climbAttack().setClimbing(3L, true);
        Map<String, Object> atk = g.climbAttack().climbAttack(3L, 50, false);
        assertThat(atk.get("extraStaminaCost")).isEqualTo(ClimbAttackService.EXTRA_STAMINA);
        assertThat(atk.get("interruptEnemy")).isEqualTo(true);
    }

    @Test
    public void destroyCliffCreatesClimbPath() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> hit = g.mutability().applyDamage(
                "cliff-wall-1", WorldMutabilityService.DamageType.HEAVY_ATTACK, 200, 1L, 300f);
        assertThat(hit.get("mutationType")).isEqualTo("DESTROY_CLIFF");
        assertThat(hit.get("newClimbPath")).isEqualTo(true);
    }

    @Test
    public void terrainOverloadAndFreeze() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> overload = g.terrainMutation().applyOverloadOnWater(
                "wolf-camp-valley", 10, 10, 1_000L);
        assertThat(overload.get("zone")).isEqualTo(TerrainMutationService.ELECTRO_CHARGED_ZONE);
        Map<String, Object> ice = g.terrainMutation().applyFreezeOnWater(
                "wolf-camp-valley", 10, 10, 2_000L);
        // water cells may already be electro; re-mark and freeze
        g.terrainMutation().markWater("wolf-camp-valley", 10, 10);
        g.terrainMutation().markWater("wolf-camp-valley", 10, 11);
        ice = g.terrainMutation().applyFreezeOnWater("wolf-camp-valley", 10, 10, 3_000L);
        assertThat(ice.get("zone")).isEqualTo(TerrainMutationService.ICE_TERRAIN);
        assertThat(ice.get("walkSpeedMul")).isEqualTo(0.7);
    }

    @Test
    public void swirlDeflectsAndGaugeRemnant() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> swirl = g.projectileCurve().applySwirl("arrow-1", "PYRO", 0, 0, 8);
        assertThat(swirl.get("reaction")).isEqualTo("SWIRL");
        assertThat(((Number) swirl.get("deflectDeg")).intValue()).isBetween(60, 120);
        Map<String, Object> rem = g.projectileCurve().gaugeRemnant(50L, "PYRO", 0.5, 1, 0, 1);
        assertThat(((Map<?, ?>) rem.get("aoiBroadcast")).get("event")).isEqualTo("GAUGE_REMNANT");
    }

    @Test
    public void serverEpochAndTugOfWarAndCutscene() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        for (int i = 0; i < 100; i++) {
            g.serverEpoch().bumpFlag("slay_dragon", 1);
        }
        Map<String, Object> epoch = g.serverEpoch().tryAdvanceEpoch(
                "slay_dragon", 100, Map.of("statue", "dragon_slayer", "weather", "THUNDERSTORM"));
        assertThat(epoch.get("ok")).isEqualTo(true);
        assertThat(epoch.get("epoch_version")).isEqualTo(1);
        g.tugOfWar().donate(1L, "wolf-camp-valley", RegionTugOfWarService.FACTION_KINGDOM, 50);
        g.tugOfWar().donate(2L, "wolf-camp-valley", RegionTugOfWarService.FACTION_ABYSS, 10);
        Map<String, Object> settle = g.tugOfWar().weeklySettle("wolf-camp-valley");
        assertThat(settle.get("winner")).isEqualTo(RegionTugOfWarService.FACTION_KINGDOM);
        for (int i = 0; i < 3; i++) {
            g.cutsceneTrigger().recordChoice("A");
        }
        g.cutsceneTrigger().recordChoice("B");
        Map<String, Object> cs = g.cutsceneTrigger().evaluate("A", "B", "cut-1");
        assertThat(cs.get("triggered")).isEqualTo(true);
        assertThat(((Map<?, ?>) cs.get("broadcast")).get("msgId"))
                .isEqualTo(MessageId.FORCED_CUTSCENE_START_SC_NOTIFY);
    }

    @Test
    public void siegePartBreakAndHideSeekRace() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.siegeWar().createSiegeRoom("siege-1", 1L);
        g.siegeWar().breakPart("siege-1", SiegeWarService.BossPart.LEFT_LEG);
        g.siegeWar().breakPart("siege-1", SiegeWarService.BossPart.CORE);
        Map<String, Object> shield = g.siegeWar().breakInvincibleShield(
                "siege-1", List.of("PYRO", "HYDRO", "CRYO", "ELECTRO"));
        assertThat(shield.get("invincibleBroken")).isEqualTo(true);
        g.asymmetricPlay().startHideSeek("m1", List.of(10L), List.of(20L));
        Map<String, Object> scan = g.asymmetricPlay().scannerSkill(20L, 0, 0, 20);
        assertThat(scan.get("skill")).isEqualTo("SCANNER_SKILL");
        g.asymmetricPlay().startRace("race-1", List.of("cp1", "finish"));
        g.asymmetricPlay().passCheckpoint("race-1", 7L, "cp1", 1000L);
        Map<String, Object> fin = g.asymmetricPlay().passCheckpoint("race-1", 7L, "finish", 2000L);
        assertThat(fin.get("finished")).isEqualTo(true);
        assertThat(g.asymmetricPlay().raceRanking("race-1").get("ok")).isEqualTo(true);
    }

    @Test
    public void craftingAndAuctionHouse() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> start = g.crafting().startCraft(1L, "iron_ingot", 0L);
        String jobId = String.valueOf(start.get("jobId"));
        Map<String, Object> done = g.crafting().completeIfReady(jobId, 10_000L);
        assertThat(done.get("ok")).isEqualTo(true);
        assertThat(done.get("via")).isEqualTo("MailService");
        Map<String, Object> list = g.auctionHouse().listBuyout(1L, "iron_ingot", 2, 100, 0L);
        String listingId = String.valueOf(list.get("listingId"));
        assertThat(g.auctionHouse().frozenCount(1L, "iron_ingot")).isEqualTo(2);
        Map<String, Object> buy = g.auctionHouse().buyout(2L, listingId);
        assertThat(buy.get("fee")).isEqualTo(Math.round(100 * AuctionHouseService.FEE_RATE));
        assertThat(buy.get("tradeServicePort") == null
                || list.get("tradeServicePort").equals(AuctionHouseService.PORT)).isTrue();
    }

    @Test
    public void hitFeedbackAndPrePlaybackRollback() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> fb = g.hitFeedback().buildFeedback(1L, 2L, 50, true, 0.1f);
        assertThat(fb.get("msgId")).isEqualTo(MessageId.BATTLE_HIT_FEEDBACK_SC_NOTIFY);
        assertThat(((Number) fb.get("hitStopDurationMs")).intValue()).isBetween(50, 150);
        assertThat(fb.get("pause_gameplay_tick")).isEqualTo(true);
        g.prePlayback().onActionStart(1L, "atk-1", 1000L);
        Map<String, Object> ok = g.prePlayback().confirmOrRollback(1L, "atk-1", 1000L, 1030L, true);
        assertThat(ok.get("event")).isEqualTo("IMPACT_CONFIRM");
        g.prePlayback().onActionStart(1L, "atk-2", 2000L);
        Map<String, Object> rb = g.prePlayback().confirmOrRollback(1L, "atk-2", 2000L, 2100L, true);
        assertThat(rb.get("event")).isEqualTo("ROLLBACK");
    }

    @Test
    public void squadCommandAndEnvThrow() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> cmd = g.squadCommander().issueCommand(
                "squad-archer-1", SquadCommanderService.CMD_SHIELD_WALL);
        assertThat(cmd.get("blockRateBonus")).isEqualTo(0.6);
        assertThat(g.squadCommander().overrideOf(9002L))
                .isEqualTo(SquadCommanderService.CMD_SHIELD_WALL);
        Map<String, Object> dead = g.squadCommander().onLeaderDeath("squad-archer-1");
        assertThat(g.squadCommander().isBerserk(9002L)).isTrue();
        assertThat(dead.get("leaderDead")).isEqualTo(true);
        Map<String, Object> pick = g.envUtilAi().tryPickup(55L, "boulder-1", 200f, 200f);
        assertThat(pick.get("ok")).isEqualTo(true);
        assertThat(g.envUtilAi().tryPickup(56L, "boulder-1", 200f, 200f).get("error"))
                .isEqualTo("already_held");
        Map<String, Object> thr = g.envUtilAi().throwAt(55L, "boulder-1", 77L, 1000);
        assertThat(thr.get("damage")).isEqualTo(200);
        assertThat(thr.get("knockback")).isEqualTo(true);
    }
}
