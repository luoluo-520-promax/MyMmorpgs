package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.cache.LocalPositionCache;
import cn.itcast.demo.mymmorpg.ecs.SceneTickEngine;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.protocol.protobuf.MoveCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.MoveScRsp;
import cn.itcast.demo.mymmorpg.persistence.PlayerWritePipeline;
import cn.itcast.demo.mymmorpg.rpc.InternalPhysicsRpcBridge;
import cn.itcast.demo.mymmorpg.sync.DynamicFrequencyService;
import cn.itcast.demo.mymmorpg.sync.MergedMoveAckService;
import cn.itcast.demo.mymmorpg.sync.MoveDeltaEncoder;
import cn.itcast.demo.mymmorpg.web.InternalOpenWorldController;
import cn.itcast.demo.mymmorpg.web.InternalSceneFastPathController;
import cn.itcast.demo.mymmorpg.world.puzzle.PhysicsAuthorityService;
import cn.itcast.demo.mymmorpg.world.traverse.GrapplePhysicsService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P15 性能深化业务流程（scene-service）：
 * 场景移动 ECS 同步 → 动态频率/增量编码 → L1 位置缓存 →
 * 物理异步校验 API → 钩锁落点粗筛 → RSocket Bridge → 战斗快速路径。
 */
public class OpenWorldP15BusinessFlowTest {

    private OpenWorldRuntimeService openWorld;
    private InternalOpenWorldController openWorldApi;
    private SceneActorService sceneActor;
    private InternalSceneFastPathController fastPathApi;
    private InternalPhysicsRpcBridge rpcBridge;
    private SceneActorServiceTestHarness sceneHarness;

    private static final long PLAYER = 500_015L;

    @BeforeMethod
    public void setUp() {
        openWorld = new OpenWorldRuntimeService();
        openWorldApi = new InternalOpenWorldController(openWorld);
        sceneHarness = new SceneActorServiceTestHarness();
        sceneActor = buildSceneActorWithP15();
        fastPathApi = new InternalSceneFastPathController(sceneActor);
        rpcBridge = new InternalPhysicsRpcBridge(openWorld.gameplay().physicsAuthority());
    }

    @Test
    public void fullP15Journey_moveEcsPhysicsGrappleRpcFastPath() throws Exception {
        long now = System.currentTimeMillis();

        // ── 1) 进场景 + 移动：触发 ECS / 增量 / L1 ──
        enterAndMove(PLAYER, 3, 150f, 0f, 220f, 50f);

        Map<String, Object> perf = sceneActor.performanceSnapshot();
        assertThat(perf.get("sceneTickEngine")).isNotNull();
        assertThat(perf.get("dynamicFrequency")).isNotNull();
        assertThat(perf.get("moveDeltaEncoder")).isNotNull();
        assertThat(perf.get("localPositionCache")).isNotNull();
        assertThat(perf.get("ecsEntities")).isEqualTo(2); /* 玩家 + ensureMonsters 向导 NPC */

        sceneActor.sceneComponentTick();
        assertThat(((Map<?, ?>) perf.get("sceneTickEngine")).get("ticksRun")).isNotNull();

        // ── 2) 物理哈希：同步校验 + 异步提交 ──
        PhysicsAuthorityService auth = openWorld.gameplay().physicsAuthority();
        auth.recordExpected(PLAYER, new PhysicsAuthorityService.ExpectedPhysics(
                5f, 0f, 2f, 1f, 0f, 1f, 0f, now));

        Map<String, Object> syncValidate = openWorldApi.physicsHashValidate(body(
                "playerId", PLAYER,
                "physicsStateHash", PhysicsAuthorityService.computeHash(5f, 0f, 2f, 1f, 0f, 1f, 0f),
                "vx", 5f, "vy", 0f, "vz", 2f,
                "gravityScale", 1f,
                "nx", 0f, "ny", 1f, "nz", 0f));
        assertThat(syncValidate.get("ok")).isEqualTo(true);

        Map<String, Object> asyncSubmit = openWorldApi.physicsHashValidateAsync(body(
                "playerId", PLAYER,
                "physicsStateHash", "tampered",
                "vx", 5f, "vy", 0f, "vz", 2f,
                "gravityScale", 9f,
                "nx", 0f, "ny", 1f, "nz", 0f,
                "onIceSurface", false));
        assertThat(asyncSubmit.get("submitted")).isEqualTo(true);
        assertThat(asyncSubmit.get("async")).isEqualTo(true);

        // ── 3) 钩锁落点：LitePhysics 半径粗筛 ──
        GrapplePhysicsService grapple = openWorld.gameplay().grapplePhysics();
        Map<String, Object> landingOk = grapple.validateGrappleLanding(
                PLAYER, 10f, 0f, 10f, 10f, 0f, 10f, 5f);
        assertThat(landingOk.get("ok")).isEqualTo(true);
        assertThat(landingOk.get("serverRadiusCheck")).isEqualTo(true);

        Map<String, Object> landingFail = grapple.validateGrappleLanding(
                PLAYER, 50f, 0f, 50f, 10f, 0f, 10f, 5f);
        assertThat(landingFail.get("ok")).isEqualTo(false);

        // ── 4) RSocket Bridge 二进制请求 ──
        String rpcRsp = rpcBridge.handle(PLAYER + "|hash|5|0|2|1|false");
        assertThat(rpcRsp).contains("ok=");

        // ── 5) 战斗快速路径 API ──
        Map<String, Object> fastStatus = fastPathApi.status();
        assertThat(fastStatus.get("fastPath")).isEqualTo(true);
        assertThat(fastStatus.get("performance")).isNotNull();

        Map<String, Object> ackRsp = fastPathApi.moveAckBatch(body("serverSeq", 99L));
        assertThat(ackRsp.get("ok")).isEqualTo(true);
        assertThat(ackRsp.get("mergedMoveAck")).isNotNull();

        // ── 6) 写流水线去重 ──
        PlayerWritePipeline pipeline = new PlayerWritePipeline();
        pipeline.enqueue(PLAYER, "HP", Map.of("hp", 100));
        pipeline.enqueue(PLAYER, "HP", Map.of("hp", 100));
        pipeline.enqueue(PLAYER, "HP", Map.of("hp", 80));
        Map<String, Object> flushed = pipeline.flush(ops ->
                assertThat(ops.size()).isGreaterThanOrEqualTo(1));
        assertThat(flushed.get("flushed")).isNotNull();
        assertThat(pipeline.stats().get("dedupeSkipped")).isNotNull();
    }

    @Test
    public void moveWithCameraYaw_deltaEncoderSkipsSmallYawChange() throws Exception {
        enterAndMove(PLAYER, 3, 160f, 0f, 230f, 45f);

        MoveCsReq req = MoveCsReq.newBuilder()
                .setTargetX(165f)
                .setTargetY(0f)
                .setTargetZ(235f)
                .setSpeed(40f)
                .setTimestamp(System.currentTimeMillis())
                .setCameraYaw(10f)
                .build();
        ProtocolMessage msg = sceneActor.handleMove(PLAYER, req);
        MoveScRsp rsp = MoveScRsp.parseFrom(msg.payload());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.OK);

        MoveDeltaEncoder encoder = new MoveDeltaEncoder();
        long ts = System.currentTimeMillis();
        encoder.encode(PLAYER, new cn.itcast.demo.mymmorpg.sync.SceneMoveCmd(
                165f, 0f, 235f, 40f, ts,
                cn.itcast.demo.mymmorpg.sync.MovementType.WALK, "",
                0, "", "", 0f, 0f, 0f, 0f, 0f, 0f, "",
                cn.itcast.demo.mymmorpg.sync.ActionState.GROUND_IDLE,
                cn.itcast.demo.mymmorpg.sync.MoveIntent.FORWARD,
                10f, 16));
        var smallYaw = encoder.encode(PLAYER,
                new cn.itcast.demo.mymmorpg.sync.SceneMoveCmd(
                        165f, 0f, 235f, 40f, ts + 16,
                        cn.itcast.demo.mymmorpg.sync.MovementType.WALK, "",
                        0, "", "", 0f, 0f, 0f, 0f, 0f, 0f, "",
                        cn.itcast.demo.mymmorpg.sync.ActionState.GROUND_IDLE,
                        cn.itcast.demo.mymmorpg.sync.MoveIntent.FORWARD,
                        11f, 16));
        assertThat(smallYaw.changedMask() & MoveDeltaEncoder.MASK_CAMERA_YAW).isZero();
    }

    private SceneActorService buildSceneActorWithP15() {
        SceneActorService svc = sceneHarness.createService();
        svc.setSceneTickEngine(new SceneTickEngine());
        svc.setDynamicFrequencyService(new DynamicFrequencyService());
        svc.setMoveDeltaEncoder(new MoveDeltaEncoder());
        svc.setMergedMoveAckService(new MergedMoveAckService());
        svc.setLocalPositionCache(new LocalPositionCache());
        return svc;
    }

    private void enterAndMove(long playerId, int sceneId, float x, float y, float z, float speed)
            throws Exception {
        sceneHarness.enterScene(sceneActor, playerId, sceneId);
        MoveCsReq move = MoveCsReq.newBuilder()
                .setTargetX(x)
                .setTargetY(y)
                .setTargetZ(z)
                .setSpeed(speed)
                .setTimestamp(System.currentTimeMillis())
                .build();
        ProtocolMessage msg = sceneActor.handleMove(playerId, move);
        MoveScRsp rsp = MoveScRsp.parseFrom(msg.payload());
        assertThat(msg.msgId()).isEqualTo(MessageId.MOVE_SC_RSP);
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.OK);
    }

    private static Map<String, Object> body(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}
