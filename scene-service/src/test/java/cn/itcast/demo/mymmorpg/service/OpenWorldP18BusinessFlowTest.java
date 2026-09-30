package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.web.InternalOpenWorldController;
import cn.itcast.demo.mymmorpg.world.explore.CollectibleService;
import cn.itcast.demo.mymmorpg.world.puzzle.TerrainMutationService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P18 八大手感短板 — Internal API 全链路业务流程：
 * ASM 空中连击 → 取消优先级 → 钩锁预测/审计 → 攀爬挂边/翻越 →
 * 摄像机软锁 → 冰面摩擦 → 动态输入窗口 → 分帧采集续传 → 影子心跳。
 */
public class OpenWorldP18BusinessFlowTest {

    private OpenWorldRuntimeService openWorld;
    private InternalOpenWorldController api;
    private static final long PLAYER = 818_001L;
    private static final String REGION = "wolf-camp-valley";
    private static final String HERB_ID = "herb-p18-1";

    @BeforeMethod
    public void setUp() {
        openWorld = new OpenWorldRuntimeService();
        api = new InternalOpenWorldController(openWorld);
        openWorld.gameplay().collectibles().register(new CollectibleService.CollectibleDef(
                HERB_ID, "测试草药", CollectibleService.Tier.COMMON_CHEST,
                1, 50f, 0f, 50f, 6f, "herb", 1, 0, 0));
    }

    @Test
    public void fullP18FeelApiJourney() {
        api.grantUniversalKit(PLAYER);

        // ── 1) ASM：空中连击 + 摄像机相对后撤步 ──
        for (int i = 0; i < 3; i++) {
            Map<String, Object> air = api.moveAdmit(body(
                    "playerId", PLAYER,
                    "movementType", "GLIDE",
                    "actionState", "AIR_NORMAL",
                    "moveIntent", "FORWARD",
                    "cameraYaw", 0f,
                    "clientDeltaMs", 8,
                    "deviceType", "PC",
                    "x", 500f + i, "y", 80f, "z", 500f,
                    "fromX", 499f + i, "fromY", 80f, "fromZ", 500f,
                    "speed", 8f, "durationMs", 100L));
            assertThat(air.get("ok")).isEqualTo(true);
            assertThat(air.get("actionState")).isEqualTo("AIR_NORMAL");
            if (i < 3) {
                assertThat(air.get("airComboHit")).isEqualTo(i + 1);
            }
        }

        Map<String, Object> dashBack = api.moveAdmit(body(
                "playerId", PLAYER,
                "movementType", "DASH",
                "actionState", "GROUND_RUN",
                "moveIntent", "BACKWARD",
                "cameraYaw", 90f,
                "clientDeltaMs", 16,
                "deviceType", "PC",
                "x", 498f, "y", 0f, "z", 500f,
                "fromX", 500f, "fromY", 0f, "fromZ", 500f,
                "speed", 12f, "durationMs", 200L));
        assertThat(dashBack.get("ok")).isEqualTo(true);
        assertThat(dashBack.get("dashVariant")).isEqualTo("DASH_BACKWARD");

        // ── 2) 取消优先级：硬直中闪避打断 ──
        openWorld.gameplay().reactions().setCurrentAction(PLAYER, cn.itcast.demo.mymmorpg.world.battle.CancelAction.NORMAL_ATTACK);
        openWorld.gameplay().poise().enterStiffness(PLAYER, 200, System.currentTimeMillis());
        Map<String, Object> cancel = api.tryCombatCancel(body(
                "playerId", PLAYER,
                "expectedCancelAction", "DODGE"));
        assertThat(cancel.get("cancelAllowed")).isEqualTo(true);
        assertThat(openWorld.gameplay().poise().stiffnessRemainMs(PLAYER)).isEqualTo(0L);

        // 预输入窗口 + 动态 clientDeltaMs
        Map<String, Object> arm = api.inputBufferArm(body(
                "playerId", PLAYER,
                "attackId", "boss-slam-1",
                "dodgeWindowMs", 200,
                "clientDeltaMs", 33));
        assertThat(arm.get("ok")).isEqualTo(true);
        assertThat(arm.get("effectiveWindowMs")).isEqualTo(166);

        Map<String, Object> queued = api.inputBufferEnqueueLegacy(body(
                "playerId", PLAYER,
                "action", "HEAVY_ATTACK",
                "expectedCancelAction", "HEAVY_ATTACK",
                "clientDeltaMs", 33));
        assertThat(queued.get("ok")).isEqualTo(true);
        assertThat(queued.get("queued")).isEqualTo("HEAVY_ATTACK");

        // ── 3) 钩锁预测 ACK + 异步审计 ──
        Map<String, Object> predict = api.grapplePredict(body(
                "playerId", PLAYER,
                "nodeId", "grapple-ruin-1",
                "targetPointHash", "abc123",
                "velX", 5f, "velY", 2f, "velZ", 3f));
        assertThat(predict.get("ack")).isEqualTo(true);
        assertThat(predict.get("retcode")).isEqualTo(RetCode.GRAPPLE_PREDICT_ACK);
        String auditToken = String.valueOf(predict.get("auditToken"));

        Map<String, Object> auditSchedule = api.grappleAudit(body(
                "playerId", PLAYER,
                "auditToken", auditToken,
                "predictedX", 480f, "predictedY", 60f, "predictedZ", 490f,
                "actualX", 485f, "actualY", 60f, "actualZ", 490f));
        assertThat(auditSchedule.get("ok")).isEqualTo(true);

        Map<String, Object> curve = openWorld.gameplay().grapplePhysics().pullSpeedCurve("ruin-pull");
        assertThat(curve.get("clientLocalSim")).isEqualTo(true);

        // ── 4) 攀爬挂边 + 翻越（独立玩家，避免轨迹窗口污染） ──
        long climbPlayer = PLAYER + 100;
        openWorld.gameplay().stamina().consumeAllowNegative(climbPlayer, 98f);
        Map<String, Object> hang = api.moveAdmit(body(
                "playerId", climbPlayer,
                "movementType", "CLIMB_HANG",
                "climbableMeshId", "cliff-valley-north",
                "actionState", "GROUND_IDLE",
                "deviceType", "PC",
                "x", 400f, "y", 30f, "z", 200f,
                "fromX", 400f, "fromY", 29f, "fromZ", 200f,
                "speed", 0.2f, "durationMs", 1000L));
        assertThat(hang.get("ok")).isEqualTo(true);
        assertThat(hang.get("hangRecoverRate")).isEqualTo(10f);

        Map<String, Object> vault = api.moveAdmit(body(
                "playerId", climbPlayer,
                "movementType", "CLIMB_VAULT",
                "climbableMeshId", "cliff-valley-north",
                "actionState", "GROUND_IDLE",
                "deviceType", "PC",
                "x", 400f, "y", 32f, "z", 200f,
                "fromX", 400f, "fromY", 31f, "fromZ", 200f,
                "speed", 2f, "durationMs", 200L));
        assertThat(vault.get("ok")).isEqualTo(true);
        assertThat(vault.get("retcode")).isEqualTo(RetCode.VAULT_SUCCESS);

        // ── 5) 摄像机视野锥软锁 ──
        api.combatAssistDevice(body("playerId", PLAYER, "deviceType", "MOBILE"));
        Map<String, Object> softLock = api.combatAssistSoftLock(body(
                "playerId", PLAYER,
                "cameraYawDeg", 0f,
                "aimYawDeg", 0f,
                "nearestEnemyYawDeg", 12f,
                "distanceM", 15f));
        assertThat(softLock.get("corrected")).isEqualTo(true);
        assertThat(softLock.get("cameraConeDeg")).isEqualTo(60f);

        // ── 6) 冰面摩擦 + 物理校验宽容阈值 ──
        TerrainMutationService terrain = openWorld.gameplay().terrainMutation();
        terrain.setHumidity(REGION, 80f);
        terrain.markWater(REGION, 2, 2);
        Map<String, Object> freeze = terrain.applyFreezeOnWater(REGION, 2, 2, System.currentTimeMillis());
        assertThat(freeze.get("surfaceFriction")).isEqualTo(0.1f);
        assertThat(freeze.get("clientSlideSim")).isEqualTo(true);

        Map<String, Object> icePhysicsSeed = api.physicsHashValidate(body(
                "playerId", PLAYER,
                "physicsStateHash", "seed",
                "vx", 5f, "vy", 0f, "vz", 0f));
        assertThat(icePhysicsSeed.get("ok")).isEqualTo(true);

        Map<String, Object> icePhysics = api.physicsHashValidate(body(
                "playerId", PLAYER,
                "physicsStateHash", "seed",
                "vx", 10f, "vy", 0f, "vz", 0f,
                "onIceSurface", true));
        assertThat(icePhysics.get("pullbackThresholdM")).isEqualTo(2.0);

        // ── 7) 影子心跳 + 高频输入窗口 ──
        Map<String, Object> hb1 = api.shadowHeartbeat(PLAYER);
        Map<String, Object> hb2 = api.shadowHeartbeat(PLAYER);
        assertThat(hb1.get("heartbeatIntervalMs")).isEqualTo(50L);
        assertThat(hb2.get("logicFrameSeq")).isEqualTo(2L);

        Map<String, Object> mobileMove = api.moveAdmit(body(
                "playerId", PLAYER + 1,
                "deviceType", "MOBILE",
                "movementType", "WALK",
                "clientDeltaMs", 33,
                "x", 1f, "z", 0f, "fromX", 0f, "fromZ", 0f,
                "durationMs", 50L));
        assertThat(mobileMove.get("effectiveWindowMs")).isEqualTo(166);

        // ── 8) 分帧采集：开始 → tick → 受击暂停 → 续传 → 完成 ──
        Map<String, Object> start = api.collectibleStart(body(
                "playerId", PLAYER,
                "collectibleId", HERB_ID));
        assertThat(start.get("ok")).isEqualTo(true);
        String progressId = String.valueOf(start.get("progressId"));

        Map<String, Object> tick1 = api.collectibleTick(body(
                "progressId", progressId,
                "tickMs", 1200L));
        assertThat(tick1.get("collectedPercent")).isNotNull();
        assertThat((Integer) tick1.get("collectedPercent")).isGreaterThan(0);

        Map<String, Object> pause = api.collectiblePause(body(
                "playerId", PLAYER,
                "collectibleId", HERB_ID));
        assertThat(pause.get("paused")).isEqualTo(true);

        Map<String, Object> resume = api.collectibleStart(body(
                "playerId", PLAYER,
                "collectibleId", HERB_ID));
        assertThat(resume.get("resumed")).isEqualTo(true);

        Map<String, Object> tickFinish = api.collectibleTick(body(
                "progressId", String.valueOf(resume.get("progressId")),
                "tickMs", 2000L));
        if (Boolean.TRUE.equals(tickFinish.get("readyToComplete"))) {
            Map<String, Object> done = openWorld.gameplay().collectibles().collect(
                    PLAYER, HERB_ID, 50f, 0f, 50f);
            assertThat(done.get("ok")).isEqualTo(true);
            assertThat(done.get("collectedPercent")).isEqualTo(100);
        }
    }

    @Test
    public void cancelPriorityDeniedWhenLowerPriority() {
        long pid = PLAYER + 99;
        openWorld.gameplay().reactions().setCurrentAction(pid, cn.itcast.demo.mymmorpg.world.battle.CancelAction.HEAVY_ATTACK);
        openWorld.gameplay().poise().enterStiffness(pid, 300, System.currentTimeMillis());
        Map<String, Object> denied = api.tryCombatCancel(body(
                "playerId", pid,
                "expectedCancelAction", "NORMAL_ATTACK"));
        assertThat(denied.get("cancelAllowed")).isEqualTo(false);
        assertThat(denied.get("retcode")).isEqualTo(RetCode.CANCEL_PRIORITY_DENIED);
    }

    @Test
    public void grappleAuditConfirmsValidPath() {
        Map<String, Object> predict = api.grapplePredict(body(
                "playerId", PLAYER + 2,
                "nodeId", "grapple-ruin-1",
                "targetPointHash", "valid",
                "velX", 1f, "velY", 0f, "velZ", 1f));
        String token = String.valueOf(predict.get("auditToken"));
        api.grappleAudit(body(
                "playerId", PLAYER + 2,
                "auditToken", token,
                "predictedX", 480f, "predictedY", 60f, "predictedZ", 490f,
                "actualX", 481f, "actualY", 60f, "actualZ", 491f));
        // 等待审计窗口（服务端用当前时间 +250ms 模拟）
        Map<String, Object> result = openWorld.gameplay().physicsAuthority().runGrappleAudit(
                token, System.currentTimeMillis() + 250);
        assertThat(result.get("softPullback")).isEqualTo(false);
        assertThat(result.get("note")).isEqualTo("grapple_predict_confirmed");
    }

    @Test
    public void airHeavyRejectedOnGround() {
        Map<String, Object> reject = api.moveAdmit(body(
                "playerId", PLAYER + 3,
                "movementType", "WALK",
                "actionState", "AIR_HEAVY",
                "deviceType", "PC",
                "x", 10f, "y", 0f, "z", 10f,
                "fromX", 9f, "fromY", 0f, "fromZ", 10f,
                "durationMs", 100L));
        assertThat(reject.get("ok")).isEqualTo(false);
        assertThat(reject.get("retcode")).isEqualTo(RetCode.ACTION_STATE_REJECTED);
    }

    private static Map<String, Object> body(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}
