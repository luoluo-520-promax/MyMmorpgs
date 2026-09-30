package cn.itcast.demo.mymmorpg.world.gameplay;

import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.sync.ActionState;
import cn.itcast.demo.mymmorpg.sync.MoveIntent;
import cn.itcast.demo.mymmorpg.sync.MovementType;
import cn.itcast.demo.mymmorpg.sync.SceneMoveCmd;
import cn.itcast.demo.mymmorpg.world.battle.CancelAction;
import cn.itcast.demo.mymmorpg.world.battle.InputBufferService;
import cn.itcast.demo.mymmorpg.world.battle.PoiseService;
import cn.itcast.demo.mymmorpg.world.battle.ReactionValidator;
import cn.itcast.demo.mymmorpg.world.explore.CollectibleService;
import cn.itcast.demo.mymmorpg.world.puzzle.PhysicsAuthorityService;
import cn.itcast.demo.mymmorpg.world.traverse.MovementAdmissionService;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** P18 边界与异常路径。 */
public class OpenWorldP18EdgeCaseTest {

    @Test
    public void airComboCapsAtThreeHits() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long pid = 9100L;
        long now = System.currentTimeMillis();
        SceneMoveCmd air = new SceneMoveCmd(
                0f, 5f, 0f, 8f, now, MovementType.GLIDE, "",
                0, "", "", 0f, 0f, 0f, 0f, 0f, 0f, "",
                ActionState.AIR_NORMAL, MoveIntent.FORWARD, 0f, 8);
        for (int i = 0; i < 4; i++) {
            g.movementAdmission().validateActionState(pid, air, now + i * 200);
        }
        Map<String, Object> fourth = g.movementAdmission().validateActionState(pid, air, now + 800);
        assertThat(fourth.get("airComboCapped")).isEqualTo(true);
    }

    @Test
    public void climbHangTimesOutAfterThreeSeconds() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.movementAdmission().registerClimbable(
                new MovementAdmissionService.ClimbableMesh("c1", 1, 0f, 10f, 0f, 8f, 10f));
        long pid = 9101L;
        long start = System.currentTimeMillis();
        SceneMoveCmd hang = new SceneMoveCmd(
                0f, 10f, 0f, 0.1f, start, MovementType.CLIMB_HANG, "c1",
                0, "", "", 0f, 0f, 0f, 0f, 0f, 0f, "",
                ActionState.GROUND_IDLE, MoveIntent.FORWARD, 0f, 16);
        g.movementAdmission().admit(pid, hang, 500, start, 0f, 10f, 0f);
        Map<String, Object> timeout = g.movementAdmission().admit(
                pid, hang, 500, start + 3100, 0f, 10f, 0f);
        assertThat(timeout.get("ok")).isEqualTo(false);
        assertThat(timeout.get("error")).isEqualTo("hang_timeout");
    }

    @Test
    public void collectProgressExpiresAfterFiveSeconds() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.collectibles().register(new CollectibleService.CollectibleDef(
                "ore-edge", "矿", CollectibleService.Tier.COMMON_CHEST,
                1, 0f, 0f, 0f, 5f, "ore", 1, 0, 0));
        long pid = 9102L;
        long now = System.currentTimeMillis();
        Map<String, Object> start = g.collectibles().startCollect(pid, "ore-edge", now);
        String progressId = String.valueOf(start.get("progressId"));
        g.collectibles().tickCollect(progressId, 800, now + 100);
        g.collectibles().pauseCollect(pid, "ore-edge", now + 200);
        Map<String, Object> expired = g.collectibles().startCollect(pid, "ore-edge", now + 6200);
        assertThat(expired.get("resumed")).isEqualTo(false);
        assertThat(expired.get("collectedPercent")).isEqualTo(0);
    }

    @Test
    public void inputBufferBypassesWhenCancelAllowed() {
        PoiseService poise = new PoiseService();
        ReactionValidator reactions = new ReactionValidator();
        InputBufferService buffer = new InputBufferService(reactions);
        buffer.bindPoise(poise);
        long pid = 9103L;
        reactions.setCurrentAction(pid, CancelAction.NORMAL_ATTACK);
        poise.enterStiffness(pid, 200, System.currentTimeMillis());
        Map<String, Object> bypass = buffer.enqueue(pid, "DODGE", CancelAction.DODGE, System.currentTimeMillis(), 16);
        assertThat(bypass.get("bypassBuffer")).isEqualTo(true);
        assertThat(bypass.get("ok")).isEqualTo(true);
    }

    @Test
    public void grappleAuditPendingBeforeDelay() {
        PhysicsAuthorityService physics = new PhysicsAuthorityService();
        long now = System.currentTimeMillis();
        physics.scheduleGrappleAudit(1L, "pending-tok", 0f, 0f, 0f, 0.5f, 0f, 0f, now);
        Map<String, Object> pending = physics.runGrappleAudit("pending-tok", now + 50);
        assertThat(pending.get("pending")).isEqualTo(true);
    }

    @Test
    public void vaultRejectedWhenTooFarFromEdge() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.movementAdmission().registerClimbable(
                new MovementAdmissionService.ClimbableMesh("cliff-far", 1, 100f, 20f, 100f, 8f, 10f));
        SceneMoveCmd vault = new SceneMoveCmd(
                100f, 15f, 100f, 2f, System.currentTimeMillis(), MovementType.CLIMB_VAULT, "cliff-far",
                0, "", "", 0f, 0f, 0f, 0f, 0f, 0f, "",
                ActionState.GROUND_IDLE, MoveIntent.FORWARD, 0f, 16);
        Map<String, Object> r = g.movementAdmission().admit(1L, vault, 200, System.currentTimeMillis(), 100f, 14f, 100f);
        assertThat(r.get("ok")).isEqualTo(false);
        assertThat(r.get("error")).isEqualTo("vault_edge_too_far");
    }

    @Test
    public void groundPhysicsUsesTighterPullback() {
        PhysicsAuthorityService physics = new PhysicsAuthorityService();
        long now = System.currentTimeMillis();
        physics.validateHash(1L, "", 5f, 0f, 0f, 1f, 0f, 1f, 0f, now, false);
        Map<String, Object> ground = physics.validateHash(
                1L, "", 20f, 0f, 0f, 1f, 0f, 1f, 0f, now + 600, false);
        Map<String, Object> ice = physics.validateHash(
                1L, "", 20f, 0f, 0f, 1f, 0f, 1f, 0f, now + 1200, true);
        assertThat(ground.get("pullbackThresholdM")).isEqualTo(0.5);
        assertThat(ice.get("pullbackThresholdM")).isEqualTo(2.0);
    }

    @Test
    public void swimAttackRejectedOnLand() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        SceneMoveCmd cmd = new SceneMoveCmd(
                0f, 0f, 0f, 5f, System.currentTimeMillis(), MovementType.WALK, "",
                0, "", "", 0f, 0f, 0f, 0f, 0f, 0f, "",
                ActionState.SWIM_ATTACK, MoveIntent.FORWARD, 0f, 16);
        Map<String, Object> r = g.movementAdmission().validateActionState(1L, cmd, System.currentTimeMillis());
        assertThat(r.get("ok")).isEqualTo(false);
        assertThat(r.get("retcode")).isEqualTo(RetCode.ACTION_STATE_REJECTED);
    }
}
