package cn.itcast.demo.mymmorpg.world.gameplay;

import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.world.economy.AuctionHouseService;
import cn.itcast.demo.mymmorpg.world.ecosystem.AffinityService;
import cn.itcast.demo.mymmorpg.world.endgame.AsymmetricPlayService;
import cn.itcast.demo.mymmorpg.world.endgame.SiegeWarService;
import cn.itcast.demo.mymmorpg.world.narrative.RegionTugOfWarService;
import cn.itcast.demo.mymmorpg.world.puzzle.WorldMutabilityService;
import cn.itcast.demo.mymmorpg.world.traverse.GrapplePhysicsService;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** P10 边界与负路径：权限不足、冷却、参数非法、过期、争抢等。 */
public class OpenWorldP10EdgeCaseTest {

    @Test
    public void affinityRejectsNonFoodAndLowThreshold() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> bad = g.affinity().feed(1L, "c1", "STONE", 1);
        assertThat(bad.get("ok")).isEqualTo(false);
        assertThat(bad.get("error")).isEqualTo("requires_FOOD_ITEM");

        Map<String, Object> hint = g.affinity().tryTreasureHint(1L, "c1", 0, 0, 1L, 1);
        assertThat(hint.get("error")).isEqualTo("affinity_too_low");
        assertThat(hint.get("need")).isEqualTo(AffinityService.THRESHOLD_TREASURE);

        Map<String, Object> alert = g.affinity().pushCreatureAlert(1L, "c1", "e1", "1_1");
        assertThat(alert.get("error")).isEqualTo("affinity_too_low");
        assertThat(MessageId.CREATURE_ALERT_SC_NOTIFY).isEqualTo(2200);
    }

    @Test
    public void affinityTreasureCooldownBlocksRepeat() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        for (int i = 0; i < 15; i++) {
            g.affinity().feed(2L, "c2", AffinityService.FOOD_ITEM, 1);
        }
        Map<String, Object> first = g.affinity().tryTreasureHint(2L, "c2", 100, 100, 1000L, 30_000L);
        assertThat(first.get("hinted")).isEqualTo(true);
        Map<String, Object> second = g.affinity().tryTreasureHint(2L, "c2", 100, 100, 2000L, 30_000L);
        assertThat(second.get("hinted")).isEqualTo(false);
        assertThat(second.get("reason")).isEqualTo("cooldown");
    }

    @Test
    public void grappleSwingKickRequiresApex() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.grapplePhysics().recordSwingSpeed(3L, 20);
        Map<String, Object> miss = g.grapplePhysics().swingKick(3L, 100, false);
        assertThat(miss.get("ok")).isEqualTo(false);
        assertThat(miss.get("error")).isEqualTo("not_at_apex");
        assertThat(MessageId.GRAPPLE_PULL_ENEMY_CS_REQ).isEqualTo(2210);
        assertThat(MessageId.VEHICLE_CAST_SKILL_CS_REQ).isEqualTo(2220);
    }

    @Test
    public void vehicleCastRequiresBoardAndKnownSkill() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> noBoard = g.vehicleCombat().castSkill(4L, "vehicle_ram");
        assertThat(noBoard.get("error")).isEqualTo("not_boarding");
        g.vehicleCombat().board(4L, "cart-coast-1");
        Map<String, Object> badSkill = g.vehicleCombat().castSkill(4L, "unknown_skill");
        assertThat(badSkill.get("error")).isEqualTo("skill_not_in_vehicle_slots");
    }

    @Test
    public void climbAttackRequiresClimbingState() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> r = g.climbAttack().climbAttack(5L, 40, false);
        assertThat(r.get("ok")).isEqualTo(false);
        assertThat(r.get("error")).isEqualTo("not_climbing");
    }

    @Test
    public void mutabilityRejectsNonHeavyAndFreezeNeedsHumidity() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> normal = g.mutability().applyDamage(
                "cliff-wall-1", WorldMutabilityService.DamageType.NORMAL, 999, 1L, 100f);
        assertThat(normal.get("error")).isEqualTo("requires_heavy_attack");

        g.terrainMutation().setHumidity("wolf-camp-valley", 40f);
        g.terrainMutation().markWater("dry-lake", 1, 1);
        g.terrainMutation().setHumidity("dry-lake", 40f);
        Map<String, Object> freeze = g.terrainMutation().applyFreezeOnWater("dry-lake", 1, 1, 1L);
        assertThat(freeze.get("error")).isEqualTo("humidity_too_low");
    }

    @Test
    public void epochThresholdAndFactionValidation() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.serverEpoch().bumpFlag("flag-x", 5);
        Map<String, Object> fail = g.serverEpoch().tryAdvanceEpoch("flag-x", 100, Map.of());
        assertThat(fail.get("ok")).isEqualTo(false);
        assertThat(fail.get("error")).isEqualTo("threshold_not_met");

        Map<String, Object> badFaction = g.tugOfWar().donate(1L, "wolf-camp-valley", "海盗", 10);
        assertThat(badFaction.get("error")).isEqualTo("invalid_faction");
        assertThat(MessageId.FORCED_CUTSCENE_START_SC_NOTIFY).isEqualTo(2400);
    }

    @Test
    public void cutsceneNeedsChoiceCombination() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.cutsceneTrigger().recordChoice("A");
        g.cutsceneTrigger().recordChoice("A");
        // A=2 < 3
        Map<String, Object> r = g.cutsceneTrigger().evaluate("A", "B", "c1");
        assertThat(r.get("triggered")).isEqualTo(false);
    }

    @Test
    public void siegeNeedsCoreAndFourElements() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.siegeWar().createSiegeRoom("s1", 1L);
        Map<String, Object> noCore = g.siegeWar().breakInvincibleShield(
                "s1", List.of("PYRO", "HYDRO", "CRYO", "ELECTRO"));
        assertThat(noCore.get("error")).isEqualTo("core_not_exposed");
        g.siegeWar().breakPart("s1", SiegeWarService.BossPart.CORE);
        Map<String, Object> few = g.siegeWar().breakInvincibleShield("s1", List.of("PYRO", "HYDRO"));
        assertThat(few.get("error")).isEqualTo("need_4_distinct_elements");
    }

    @Test
    public void hideSeekScannerRejectsHiderAndPropAuth() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.asymmetricPlay().startHideSeek("m1", List.of(10L), List.of(20L));
        Map<String, Object> bad = g.asymmetricPlay().scannerSkill(10L, 0, 0, 10);
        assertThat(bad.get("error")).isEqualTo("not_seeker");

        Map<String, Object> noSig = g.propTransform().submitPropAuth(10L, "", 1000L);
        assertThat(noSig.get("error")).isEqualTo("missing_PROP_AUTH");
        assertThat(g.propTransform().validateMove(10L, 1.0).get("ok")).isEqualTo(false);
        assertThat(AsymmetricPlayService.MODE_HIDE_SEEK).isEqualTo("HIDE_SEEK");
    }

    @Test
    public void raceCheckpointOrderAndAuctionRules() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.asymmetricPlay().startRace("r1", List.of("a", "b", "finish"));
        Map<String, Object> skip = g.asymmetricPlay().passCheckpoint("r1", 1L, "finish", 1L);
        assertThat(skip.get("error")).isEqualTo("checkpoint_order");

        Map<String, Object> badList = g.auctionHouse().listBuyout(1L, "x", 0, 100, 0L);
        assertThat(badList.get("error")).isEqualTo("invalid_params");
        Map<String, Object> list = g.auctionHouse().listAuction(1L, "ore", 1, 50, 0L);
        String id = String.valueOf(list.get("listingId"));
        Map<String, Object> lowBid = g.auctionHouse().bid(2L, id, 40, 1000L);
        assertThat(lowBid.get("error")).isEqualTo("bid_too_low");
        Map<String, Object> earlySettle = g.auctionHouse().settleAuction(id, 1000L);
        assertThat(earlySettle.get("error")).isEqualTo("not_expired");
        assertThat(AuctionHouseService.FEE_RATE).isEqualTo(0.05);
    }

    @Test
    public void prePlaybackInvalidHitAndHitFeedbackMsgId() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.prePlayback().onActionStart(9L, "a1", 1000L);
        Map<String, Object> invalid = g.prePlayback().confirmOrRollback(9L, "a1", 1000L, 1020L, false);
        assertThat(invalid.get("event")).isEqualTo("ROLLBACK");
        assertThat(invalid.get("reason")).isEqualTo("hit_invalid");

        Map<String, Object> fb = g.hitFeedback().buildFeedback(1L, 2L, 10, false, 1.0f);
        assertThat(fb.get("msgId")).isEqualTo(MessageId.BATTLE_HIT_FEEDBACK_SC_NOTIFY);
        assertThat(MessageId.BATTLE_HIT_FEEDBACK_SC_NOTIFY).isEqualTo(2090);
    }

    @Test
    public void envAiTooFarAndSquadUnknown() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> far = g.envUtilAi().tryPickup(1L, "boulder-1", 0f, 0f);
        assertThat(far.get("error")).isEqualTo("too_far");
        Map<String, Object> unknown = g.squadCommander().issueCommand("no-such", "COMMAND_SHIELD_WALL");
        assertThat(unknown.get("error")).isEqualTo("squad_not_found");
        assertThat(RegionTugOfWarService.FACTION_KINGDOM).isEqualTo("王国军");
        assertThat(GrapplePhysicsService.BASE_PULL_FORCE).isEqualTo(40.0);
    }
}
