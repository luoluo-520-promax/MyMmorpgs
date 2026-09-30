package cn.itcast.demo.mymmorpg.world.gameplay;

import cn.itcast.demo.mymmorpg.sync.MovementType;
import cn.itcast.demo.mymmorpg.world.battle.CombatAssistService;
import cn.itcast.demo.mymmorpg.world.battle.PoiseService;
import cn.itcast.demo.mymmorpg.world.battle.PrePlaybackService;
import cn.itcast.demo.mymmorpg.world.battle.ReactionValidator;
import cn.itcast.demo.mymmorpg.world.endgame.AffixShuffleService;
import cn.itcast.demo.mymmorpg.world.traverse.ClimbRestPointService;
import cn.itcast.demo.mymmorpg.world.traverse.StaminaConsumeService;
import cn.itcast.demo.mymmorpg.world.traverse.TraverseMomentumService;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** P14：手感延迟 / 立体移动 / 破坏反馈 / 探索惊喜 / 社交沉淀 / 多端适配 / 终局变量。 */
public class OpenWorldP14FlowTest {

    @Test
    public void clientPredictSoftRollbackKeepsVisuals() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long playerId = 201L;
        long ts = System.currentTimeMillis();

        Map<String, Object> start = g.clientPredict().predictStart(
                playerId, "dodge-1",
                cn.itcast.demo.mymmorpg.world.battle.ClientPredictedActionService.PredictedAction.DODGE,
                10f, 0f, 10f, 12f, ts);
        assertThat(start.get("playAnimationImmediately")).isEqualTo(true);

        Map<String, Object> reconcile = g.clientPredict().reconcile(
                playerId, "dodge-1", ts, 12f, 0f, 12f, 12f, false, true);
        assertThat(reconcile.get("softRollback")).isEqualTo(true);
        assertThat(reconcile.get("keepVisuals")).isEqualTo(true);
    }

    @Test
    public void dynamicDodgeWindowScalesWithRtt() {
        ReactionValidator v = new ReactionValidator();
        assertThat(v.dynamicDodgeWindowMs(200, 0)).isEqualTo(200);
        assertThat(v.dynamicDodgeWindowMs(200, 80)).isEqualTo(240);
        Map<String, Object> win = v.openAttackWindow("atk-rtt", 1L, System.currentTimeMillis(), 200, 180, 100);
        assertThat(win.get("dodgeWindowMs")).isEqualTo(250);
        assertThat(win.get("dynamicWindow")).isEqualTo(true);
    }

    @Test
    public void prePlaybackSoftRollbackDoesNotRubberBand() {
        PrePlaybackService pb = new PrePlaybackService();
        long now = System.currentTimeMillis();
        pb.onActionStart(1L, "hit-1", now);
        Map<String, Object> soft = pb.confirmOrRollback(1L, "hit-1", now, now + 200, false, true);
        assertThat(soft.get("softRollback")).isEqualTo(true);
        assertThat(soft.get("keepPosition")).isEqualTo(true);
        assertThat(soft.get("rubberBand")).isEqualTo(false);
    }

    @Test
    public void glideToFallRetainsHorizontalMomentum() {
        TraverseMomentumService m = new TraverseMomentumService();
        var before = new TraverseMomentumService.VelocityVector(10f, -2f, 8f, 12f);
        var after = m.inheritOnTransition(MovementType.GLIDE, MovementType.WALK, before);
        assertThat(after.vx()).isEqualTo(7f);
        assertThat(after.vz()).isEqualTo(5.6f);
    }

    @Test
    public void climbRestGrantsJumpBonus() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        ClimbRestPointService rest = g.climbRestPoints();
        rest.register(new ClimbRestPointService.RestPoint(
                "rest-1", "cliff-a", 5f, 10f, 5f, 3f, 40f, false));
        StaminaConsumeService stamina = g.stamina();
        Map<String, Object> r = rest.tryRest(301L, "cliff-a", 5f, 10f, 5f, stamina);
        assertThat(r.get("climbJumpBonusActive")).isEqualTo(true);
        assertThat(g.climbRestPoints().climbJumpMultiplier(301L)).isEqualTo(1.3f);
    }

    @Test
    public void poiseArmorVsDodgeIframe() {
        PoiseService poise = new PoiseService();
        long now = System.currentTimeMillis();
        Map<String, Object> iframe = poise.enterDodgeIframe(1L, 300, now);
        assertThat(iframe.get("invincible")).isEqualTo(true);
        Map<String, Object> hit = poise.applyPoiseDamage(2L, 1L, 50, now + 50);
        assertThat(hit.get("damageTaken")).isEqualTo(false);

        poise.initEntity(3L, 100, 5);
        poise.enterPoiseArmor(3L, 30, now);
        Map<String, Object> armorHit = poise.applyPoiseDamage(2L, 3L, 10, now + 100);
        assertThat(armorHit.get("armorLayer")).isEqualTo("POISE_ARMOR");
    }

    @Test
    public void deterministicMutationIssuesSeed() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> m = g.deterministicMutation().issueLocalPlayback("tree-1", System.currentTimeMillis(), 60_000L);
        assertThat(m.get("clientPlayImmediately")).isEqualTo(true);
        assertThat(m.get("seed")).isNotNull();
    }

    @Test
    public void mobileDeviceGetsAssistBonus() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> reg = g.combatAssist().registerDevice(401L, CombatAssistService.DeviceType.MOBILE);
        assertThat(reg.get("dodgeWindowBonusMs")).isEqualTo(30);
        assertThat(reg.get("grappleSnapBonus")).isEqualTo(0.15f);
    }

    @Test
    public void weeklyAffixShufflePicksFive() {
        AffixShuffleService affix = new AffixShuffleService();
        Map<String, Object> weekly = affix.weeklyAffixList(1L, 1_700_000_000_000L);
        @SuppressWarnings("unchecked")
        List<String> list = (List<String>) weekly.get("weeklyAffixList");
        assertThat(list).hasSize(5);
    }

    @Test
    public void coopCampEstablishInSafeRegion() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> camp = g.coopCamp().establish(
                501L, List.of(502L, 503L), "wolf-camp-valley", true, 0f, 0f, 0f, System.currentTimeMillis());
        assertThat(camp.get("ok")).isEqualTo(true);
        assertThat(camp.get("durability")).isEqualTo(100);
    }
}
