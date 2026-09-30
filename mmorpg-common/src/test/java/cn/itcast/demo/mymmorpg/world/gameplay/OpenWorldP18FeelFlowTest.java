package cn.itcast.demo.mymmorpg.world.gameplay;

import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.sync.ActionState;
import cn.itcast.demo.mymmorpg.sync.MoveIntent;
import cn.itcast.demo.mymmorpg.sync.MovementType;
import cn.itcast.demo.mymmorpg.sync.SceneMoveCmd;
import cn.itcast.demo.mymmorpg.sync.ServerShadowService;
import cn.itcast.demo.mymmorpg.world.battle.CancelAction;
import cn.itcast.demo.mymmorpg.world.battle.CombatAssistService;
import cn.itcast.demo.mymmorpg.world.battle.HitFeedbackService;
import cn.itcast.demo.mymmorpg.world.battle.InputBufferService;
import cn.itcast.demo.mymmorpg.world.battle.PoiseService;
import cn.itcast.demo.mymmorpg.world.battle.ReactionValidator;
import cn.itcast.demo.mymmorpg.world.explore.CollectibleService;
import cn.itcast.demo.mymmorpg.world.puzzle.PhysicsAuthorityService;
import cn.itcast.demo.mymmorpg.world.puzzle.TerrainMutationService;
import cn.itcast.demo.mymmorpg.world.traverse.GrappleNodeService;
import cn.itcast.demo.mymmorpg.world.traverse.GrapplePhysicsService;
import cn.itcast.demo.mymmorpg.world.traverse.InputConfidenceAnalyzer;
import cn.itcast.demo.mymmorpg.world.traverse.MovementAdmissionService;
import cn.itcast.demo.mymmorpg.world.traverse.StaminaConsumeService;
import cn.itcast.demo.mymmorpg.world.traverse.TraverseMomentumService;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** P18：八大手感短板补齐（ASM / 取消优先级 / 钩锁预测 / 攀爬挂边翻越 / 摄像机移动 / 冰面滑行 / 动态输入 / 采集续传）。 */
public class OpenWorldP18FeelFlowTest {

    @Test
    public void airComboAllowsThreeHitsInWindow() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long pid = 9001L;
        long now = System.currentTimeMillis();
        SceneMoveCmd air = new SceneMoveCmd(
                10f, 5f, 10f, 8f, now, MovementType.GLIDE, "",
                0, "", "", 0f, 0f, 0f, 0f, 0f, 0f, "",
                ActionState.AIR_NORMAL, MoveIntent.FORWARD, 0f, 8);
        for (int i = 0; i < 3; i++) {
            Map<String, Object> r = g.movementAdmission().validateActionState(pid, air, now + i * 100);
            assertThat(r.get("ok")).isEqualTo(true);
            assertThat(r.get("airComboHit")).isEqualTo(i + 1);
        }
    }

    @Test
    public void dodgeCancelsHitStopDuringLightHit() {
        HitFeedbackService hit = new HitFeedbackService();
        Map<String, Object> fb = hit.buildFeedback(1L, 2L, 20, false, 0.5f);
        assertThat(fb.get("allowCancelDuringHitStop")).isEqualTo(true);
        Map<String, Object> boss = hit.buildFeedback(1L, 2L, 50, true, 0.2f);
        assertThat(boss.get("allowCancelDuringHitStop")).isEqualTo(false);
    }

    @Test
    public void cancelPriorityOverridesStiffness() {
        PoiseService poise = new PoiseService();
        ReactionValidator reactions = new ReactionValidator();
        reactions.setCurrentAction(1L, CancelAction.NORMAL_ATTACK);
        poise.enterStiffness(1L, 200, System.currentTimeMillis());
        Map<String, Object> cancel = reactions.tryCancel(1L, CancelAction.DODGE, poise, System.currentTimeMillis());
        assertThat(cancel.get("cancelAllowed")).isEqualTo(true);
        assertThat(poise.stiffnessRemainMs(1L)).isEqualTo(0);
    }

    @Test
    public void grapplePredictReturnsImmediateAck() {
        GrappleNodeService nodes = new GrappleNodeService();
        Map<String, Object> ack = nodes.predictGrapple(1L, "n1", "hash-abc", 5f, 2f, 3f, System.currentTimeMillis());
        assertThat(ack.get("ack")).isEqualTo(true);
        assertThat(ack.get("retcode")).isEqualTo(RetCode.GRAPPLE_PREDICT_ACK);
    }

    @Test
    public void grappleAuditSoftPullsBackOnCheat() {
        PhysicsAuthorityService physics = new PhysicsAuthorityService();
        long now = System.currentTimeMillis();
        physics.scheduleGrappleAudit(1L, "tok1", 0f, 0f, 0f, 5f, 0f, 0f, now);
        Map<String, Object> audit = physics.runGrappleAudit("tok1", now + 250);
        assertThat(audit.get("softPullback")).isEqualTo(true);
    }

    @Test
    public void climbVaultSucceedsNearEdge() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.movementAdmission().registerClimbable(
                new MovementAdmissionService.ClimbableMesh("cliff-1", 1, 100f, 20f, 100f, 8f, 10f));
        long now = System.currentTimeMillis();
        SceneMoveCmd vault = new SceneMoveCmd(
                100f, 22f, 100f, 2f, now, MovementType.CLIMB_VAULT, "cliff-1",
                0, "", "", 0f, 0f, 0f, 0f, 0f, 0f, "",
                ActionState.GROUND_IDLE, MoveIntent.FORWARD, 0f, 16);
        Map<String, Object> r = g.movementAdmission().admit(1L, vault, 200, now, 100f, 21f, 100f);
        assertThat(r.get("ok")).isEqualTo(true);
        assertThat(r.get("retcode")).isEqualTo(RetCode.VAULT_SUCCESS);
    }

    @Test
    public void cameraSoftLockUsesCone() {
        CombatAssistService assist = new CombatAssistService();
        assist.registerDevice(1L, CombatAssistService.DeviceType.MOBILE);
        Map<String, Object> inCone = assist.resolveSoftLock(1L, 0f, 0f, 12f, 15f);
        assertThat(inCone.get("corrected")).isEqualTo(true);
        Map<String, Object> outCone = assist.resolveSoftLock(1L, 0f, 0f, 90f, 15f);
        assertThat(outCone.get("outOfCameraCone")).isEqualTo(true);
    }

    @Test
    public void iceSurfaceBroadcastsFriction() {
        TerrainMutationService terrain = new TerrainMutationService();
        terrain.setHumidity("river-1", 80f);
        terrain.markWater("river-1", 1, 1);
        Map<String, Object> freeze = terrain.applyFreezeOnWater("river-1", 1, 1, System.currentTimeMillis());
        assertThat(freeze.get("surfaceFriction")).isEqualTo(0.1f);
        assertThat(freeze.get("clientSlideSim")).isEqualTo(true);
    }

    @Test
    public void dynamicInputWindowScalesWithDelta() {
        assertThat(InputConfidenceAnalyzer.effectiveWindowMs(8)).isEqualTo(116);
        assertThat(InputBufferService.effectiveWindowMs(33)).isEqualTo(166);
    }

    @Test
    public void collectProgressResumesAfterPause() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        CollectibleService.CollectibleDef def = new CollectibleService.CollectibleDef(
                "herb-1", "草药", CollectibleService.Tier.COMMON_CHEST,
                1, 0f, 0f, 0f, 5f, "herb", 1, 0, 0);
        g.collectibles().register(def);
        long pid = 42L;
        long now = System.currentTimeMillis();
        Map<String, Object> start = g.collectibles().startCollect(pid, "herb-1", now);
        g.collectibles().tickCollect(String.valueOf(start.get("progressId")), 1500, now + 100);
        g.collectibles().pauseCollect(pid, "herb-1", now + 200);
        Map<String, Object> resume = g.collectibles().startCollect(pid, "herb-1", now + 500);
        assertThat(resume.get("resumed")).isEqualTo(true);
        assertThat((Integer) resume.get("collectedPercent")).isGreaterThan(0);
    }

    @Test
    public void serverShadowHeartbeatIncrementsFrame() {
        ServerShadowService shadow = new ServerShadowService();
        Map<String, Object> hb = shadow.heartbeat(7L, System.currentTimeMillis());
        assertThat(hb.get("heartbeatIntervalMs")).isEqualTo(50L);
        assertThat(hb.get("logicFrameSeq")).isEqualTo(1L);
    }

    @Test
    public void pullSpeedCurveForClientSim() {
        GrapplePhysicsService gp = new GrapplePhysicsService();
        Map<String, Object> curve = gp.pullSpeedCurve("test");
        assertThat(curve.get("clientLocalSim")).isEqualTo(true);
        assertThat(curve.get("serverSyncIntervalMs")).isEqualTo(300);
    }

    @Test
    public void iceFrictionDecaysVelocity() {
        TraverseMomentumService momentum = new TraverseMomentumService();
        var v = new TraverseMomentumService.VelocityVector(10f, 0f, 0f, 10f);
        Map<String, Object> r = momentum.applyFriction(v, 0.1f, 0.5f, 100);
        assertThat((Float) r.get("afterSpeed")).isLessThan(10f);
    }
}
