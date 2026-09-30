package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.cache.LocalPositionCache;
import cn.itcast.demo.mymmorpg.cache.RedisPositionBatchWriter;
import cn.itcast.demo.mymmorpg.ecs.SceneTickMicroPipeline;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.protocol.protobuf.MoveCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.MoveScRsp;
import cn.itcast.demo.mymmorpg.sync.BroadcastImportanceFuseService;
import cn.itcast.demo.mymmorpg.sync.DynamicFrequencyService;
import cn.itcast.demo.mymmorpg.sync.MergedMoveAckService;
import cn.itcast.demo.mymmorpg.sync.MoveDeltaEncoder;
import cn.itcast.demo.mymmorpg.web.InternalOpenWorldController;
import cn.itcast.demo.mymmorpg.world.ZoneLoadBalancer;
import cn.itcast.demo.mymmorpg.world.WorldZoneManager;
import cn.itcast.demo.mymmorpg.world.battle.DamageEvent;
import cn.itcast.demo.mymmorpg.world.battle.ReactionValidator;
import cn.itcast.demo.mymmorpg.world.gameplay.OpenWorldGameplayFacade;
import cn.itcast.demo.mymmorpg.world.puzzle.TerrainStateVector;
import cn.itcast.demo.mymmorpg.world.scene.BattleScenePodAllocator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 人群拥挤 / 战斗手感 / 分布式架构 — 完整业务流程（scene-service 集成）。
 * 覆盖：微流水线 Tick → 移动广播熔断 → 位置增量 Redis → 闪避时间戳背书 →
 * 地形延迟写入 → 双时钟 → 零 GC 战斗事件 → 战斗 Pod 隔离 → Zone 负载均衡。
 */
public class PerformanceCrowdOptimizationBusinessFlowTest {

    private OpenWorldRuntimeService openWorld;
    private InternalOpenWorldController openWorldApi;
    private SceneActorService sceneActor;
    private SceneActorServiceTestHarness sceneHarness;
    private BroadcastImportanceFuseService broadcastFuse;
    private SceneTickMicroPipeline microPipeline;
    private RedisPositionBatchWriter redisWriter;

    private static final long PLAYER = 600_020L;
    private static final long PLAYER2 = 600_021L;

    @BeforeMethod
    @SuppressWarnings("unchecked")
    public void setUp() {
        openWorld = new OpenWorldRuntimeService();
        openWorldApi = new InternalOpenWorldController(openWorld);
        sceneHarness = new SceneActorServiceTestHarness();
        microPipeline = new SceneTickMicroPipeline();
        broadcastFuse = new BroadcastImportanceFuseService();
        broadcastFuse.configure(64_000L);

        ObjectProvider<StringRedisTemplate> redisProvider = mock(ObjectProvider.class);
        when(redisProvider.getIfAvailable()).thenReturn(null);
        redisWriter = new RedisPositionBatchWriter(redisProvider);

        sceneActor = buildSceneWithOptimizations();
    }

    @Test
    public void fullCrowdOptimizationJourney_moveTickFuseRedisReactionTerrainBattlePod() throws Exception {
        OpenWorldGameplayFacade gameplay = openWorld.gameplay();

        // ── 1) 进场景 + 双玩家移动：触发微流水线 / 位置缓存 / 增量编码 ──
        enterAndMove(PLAYER, 3, 150f, 0f, 220f, 50f);
        enterAndMove(PLAYER2, 3, 160f, 0f, 230f, 45f);

        sceneActor.sceneComponentTick();
        sceneActor.sceneLogicTick();
        sceneActor.sceneSyncTick();

        Map<String, Object> perf = sceneActor.performanceSnapshot();
        assertThat(perf.get("tickMicroPipeline")).isNotNull();
        assertThat(perf.get("broadcastFuse")).isNotNull();
        assertThat(((Map<?, ?>) perf.get("tickMicroPipeline")).get("movementRuns")).isNotNull();

        long now = System.currentTimeMillis();
        // ── 2) 静止玩家跳过 Redis 写入；移动玩家写入 ──
        // 进场景移动时已 enqueue 一次，同坐标应跳过
        assertThat(redisWriter.shouldWrite(PLAYER, 150f, 0f, 220f, now)).isFalse();
        assertThat(redisWriter.shouldWrite(PLAYER, 150f, 0f, 220f,
                now + RedisPositionBatchWriter.STATIONARY_SKIP_MS + 100)).isFalse();
        assertThat(redisWriter.stats().get("skippedStationary")).isNotNull();
        // 坐标变更仍可写入
        assertThat(redisWriter.shouldWrite(PLAYER, 155f, 0f, 225f, now + 500)).isTrue();
        redisWriter.enqueue(PLAYER, 155f, 0f, 225f, now + 500);

        // ── 3) 广播熔断：超带宽后非战斗降频，战斗玩家保持高频 ──
        broadcastFuse.markCombat(PLAYER, true);
        broadcastFuse.recordOutboundBytes(100_000, now);
        assertThat(broadcastFuse.isFused()).isTrue();
        var combatDecision = broadcastFuse.decide(PLAYER, 10f,
                cn.itcast.demo.mymmorpg.aoi.AoiBroadcastStrategy.FrequencyTier.NEAR_20HZ, now);
        var idleDecision = broadcastFuse.decide(PLAYER2, 10f,
                cn.itcast.demo.mymmorpg.aoi.AoiBroadcastStrategy.FrequencyTier.NEAR_20HZ, now);
        assertThat(combatDecision.positionOnly()).isFalse();
        assertThat(idleDecision.positionOnly()).isTrue();

        // ── 4) 客户端时间戳背书闪避（经 Internal API） ──
        Map<String, Object> window = openWorldApi.reactionOpenWindow(body(
                "attackId", "crowd-atk-1",
                "attackerEntityId", 9001L,
                "dodgeWindowMs", 200,
                "parryWindowMs", 180,
                "playerRttMs", 100));
        assertThat(window.get("dodgeWindowMs")).isEqualTo(250);

        long openAt = ((Number) window.get("openAtMs")).longValue();
        Map<String, Object> dodgeOk = gameplay.reactions().validate(
                ReactionValidator.ReactionKind.PERFECT_DODGE, "crowd-battle",
                PLAYER, "crowd-atk-1", openAt + 280, openAt + 400);
        assertThat(dodgeOk.get("ok")).isEqualTo(true);
        assertThat(dodgeOk.get("clientTimestampBacked")).isEqualTo(true);

        // ── 5) 地形延迟写入 + 乐观读校验 ──
        TerrainStateVector tsv = gameplay.terrainStateVector();
        Map<String, Object> cliff = tsv.scheduleBump("mondstadt-cliff", 5, 8, "DESTROY_CLIFF", now);
        assertThat(cliff.get("visualImmediate")).isEqualTo(true);
        assertThat(tsv.validateMoveRevision("mondstadt-cliff", 5, 8, 0L).get("optimisticRead"))
                .isEqualTo(true);
        assertThat(tsv.flushPendingWrites(now + TerrainStateVector.TERRAIN_WRITE_DELAY_MS + 50))
                .isEqualTo(1);

        // ── 6) 双时钟：子弹时间下 Wall CD 仍按真实时间流逝 ──
        gameplay.dualClock().startSkillCooldown(PLAYER, 0, 2000, 0.1, now);
        assertThat(gameplay.dualClock().isSkillReadyWall(PLAYER, 0, now + 1500)).isFalse();
        assertThat(gameplay.dualClock().isSkillReadyWall(PLAYER, 0, now + 2100)).isTrue();
        assertThat(gameplay.dualClock().skillRemainWallMs(PLAYER, 0, now + 500)).isGreaterThan(1400L);

        // ── 7) 零 GC 战斗事件环 ──
        DamageEvent evt = gameplay.combatEvents().publishDamage(
                PLAYER, 9001L, 120, 100, DamageEvent.VERDICT_HIT,
                DamageEvent.ELEMENT_ELECTRO, 15, now);
        assertThat(evt.active()).isTrue();
        assertThat(gameplay.combatEvents().drainActiveDamage(10)).isNotEmpty();

        // ── 8) HitStop 反馈含 Wall-Clock 标记 ──
        Map<String, Object> hitFb = gameplay.hitFeedback().buildFeedback(
                PLAYER, 9001L, 30, false, 0.5f);
        assertThat(hitFb.get("skillCdUsesWallClock")).isEqualTo(true);
        assertThat(hitFb.get("buffTimerUsesWallClock")).isEqualTo(true);

        // ── 9) 战斗 Pod 与大世界进程隔离 ──
        BattleScenePodAllocator pods = gameplay.battleScenePods();
        var owPod = pods.registerOpenWorld(3, 200, now);
        var battlePod = pods.allocateBattlePod(9001, true, 600_000L, now);
        assertThat(owPod.sceneClass()).isEqualTo(BattleScenePodAllocator.SceneClass.OPEN_WORLD);
        assertThat(battlePod.maxPlayers()).isEqualTo(20);
        assertThat(battlePod.memorySnapshotOnly()).isTrue();
        assertThat(pods.admitPlayer(battlePod.podId())).isTrue();
        pods.captureSnapshot(battlePod.podId(), new byte[]{1, 2, 3}, now);
        assertThat(pods.latestSnapshot(battlePod.podId()).revision()).isEqualTo(1L);

        // ── 10) Zone 负载均衡 rebalance ──
        WorldZoneManager wzm = new WorldZoneManager();
        ZoneLoadBalancer balancer = new ZoneLoadBalancer(wzm);
        Map<String, Object> reb = balancer.rebalance(3, Map.of(0, 55));
        assertThat(reb.get("ok")).isEqualTo(true);
        assertThat(balancer.metrics().get("rebalanceRuns")).isNotNull();
    }

    @Test
    public void repeatedMoveTriggersMicroPipelineWithoutBlocking() throws Exception {
        enterAndMove(PLAYER, 3, 100f, 0f, 200f, 40f);
        for (int i = 0; i < 5; i++) {
            MoveCsReq req = MoveCsReq.newBuilder()
                    .setTargetX(100f + i * 2)
                    .setTargetY(0f)
                    .setTargetZ(200f + i * 2)
                    .setSpeed(40f)
                    .setTimestamp(System.currentTimeMillis())
                    .build();
            ProtocolMessage msg = sceneActor.handleMove(PLAYER, req);
            assertThat(MoveScRsp.parseFrom(msg.payload()).getRetcode()).isEqualTo(RetCode.OK);
            sceneActor.sceneComponentTick();
        }
        Map<String, Object> pipe = (Map<String, Object>) sceneActor.performanceSnapshot().get("tickMicroPipeline");
        assertThat(((Number) pipe.get("movementRuns")).longValue()).isGreaterThan(0L);
    }

    private SceneActorService buildSceneWithOptimizations() {
        SceneActorService svc = sceneHarness.createService();
        svc.setSceneTickMicroPipeline(microPipeline);
        svc.setBroadcastImportanceFuseService(broadcastFuse);
        svc.setDynamicFrequencyService(new DynamicFrequencyService());
        svc.setMoveDeltaEncoder(new MoveDeltaEncoder());
        svc.setMergedMoveAckService(new MergedMoveAckService());
        svc.setLocalPositionCache(new LocalPositionCache());
        svc.setRedisPositionBatchWriter(redisWriter);
        WorldZoneManager wzm = new WorldZoneManager();
        svc.setZoneLoadBalancer(new ZoneLoadBalancer(wzm));
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
        assertThat(msg.msgId()).isEqualTo(MessageId.MOVE_SC_RSP);
        assertThat(MoveScRsp.parseFrom(msg.payload()).getRetcode()).isEqualTo(RetCode.OK);
    }

    private static Map<String, Object> body(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}
