package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.web.InternalOpenWorldController;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P19 Internal API 全链路业务流程测试（scene-service 层）。
 * 六大模块各自独立用例 + 端到端串联。
 */
public class OpenWorldP19BusinessFlowTest {

    private OpenWorldRuntimeService openWorld;
    private InternalOpenWorldController api;
    private static final long PLAYER = 919_001L;
    private static final String REGION = "wolf-camp-valley";
    private static final String ROOM = "room-p19-api";

    @BeforeMethod
    public void setUp() {
        openWorld = new OpenWorldRuntimeService();
        api = new InternalOpenWorldController(openWorld);
    }

    @Test
    public void statusContainsP19Snapshot() {
        Map<String, Object> status = api.status();
        assertThat(status.get("ok")).isEqualTo(true);
        Map<String, Object> gameplay = openWorld.gameplay().statusOverview();
        assertThat(gameplay.get("p19")).isNotNull();
    }

    @Test
    public void moveAdmitWithGrassSurfaceTriggersPhysicalDetail() {
        Map<String, Object> move = api.moveAdmit(body(
                "playerId", PLAYER,
                "regionId", REGION,
                "surfaceType", "GRASS",
                "x", 230f, "y", 0f, "z", 190f,
                "fromX", 229f, "fromY", 0f, "fromZ", 190f,
                "speed", 6f, "durationMs", 200L));
        assertThat(move.get("ok")).isEqualTo(true);
        assertThat(move.get("physicalDetail")).isNotNull();
        assertThat(move.get("aoiBroadcast")).isNotNull();
    }

    @Test
    public void moveAdmitWithSnowSurfaceSpawnsFootprint() {
        Map<String, Object> move = api.moveAdmit(body(
                "playerId", PLAYER + 1,
                "regionId", REGION,
                "surfaceType", "SNOW",
                "x", 231f, "y", 0f, "z", 191f,
                "fromX", 230f, "fromY", 0f, "fromZ", 191f,
                "speed", 6f, "durationMs", 200L));
        @SuppressWarnings("unchecked")
        Map<String, Object> detail = (Map<String, Object>) move.get("physicalDetail");
        assertThat(detail.get("footprintSpawn")).isNotNull();
    }

    @Test
    public void moveAdmitPlainSurfaceSkipsFootprint() {
        Map<String, Object> move = api.moveAdmit(body(
                "playerId", PLAYER + 2,
                "regionId", REGION,
                "surfaceType", "PLAIN",
                "x", 232f, "y", 0f, "z", 192f,
                "fromX", 231f, "fromY", 0f, "fromZ", 192f,
                "speed", 6f, "durationMs", 200L));
        @SuppressWarnings("unchecked")
        Map<String, Object> detail = (Map<String, Object>) move.get("physicalDetail");
        assertThat(detail.get("footprintSpawn")).isNull();
    }

    @Test
    public void projectileHitAndTerrainDetailSnapshot() {
        api.projectileHitDebris(body(
                "ownerId", PLAYER, "zone", REGION,
                "debrisType", "ARROW",
                "x", 235f, "y", 1f, "z", 195f));
        Map<String, Object> detail = api.terrainDetailSnapshot(REGION, System.currentTimeMillis());
        assertThat(detail.get("ok")).isEqualTo(true);
        assertThat(detail.get("debris")).asList().isNotEmpty();
    }

    @Test
    public void civilizationNpcAndShopAccess() {
        Map<String, Object> guard = api.npcScheduleState("npc-guard-valley", System.currentTimeMillis());
        assertThat(guard.get("ok")).isEqualTo(true);
        assertThat(guard.get("action")).isNotNull();

        Map<String, Object> shop = api.shopAccessCheck(body(
                "npcId", "npc-shop-valley", "playerId", PLAYER));
        assertThat(shop.get("ok")).isEqualTo(true);
    }

    @Test
    public void gameClockReturnsCorrectTimeScale() {
        Map<String, Object> clock = api.gameClock(System.currentTimeMillis());
        assertThat(clock.get("realMsPerGameHour")).isEqualTo(120_000L);
        assertThat(clock.get("hourOfDay")).isNotNull();
    }

    @Test
    public void physicsKnockBackGrabThrowChain() {
        Map<String, Object> kb = api.physicsKnockBack(body(
                "attackerId", PLAYER, "targetId", 9002L,
                "attackerForce", 40, "groundFriction", 0.15));
        assertThat(kb.get("event")).isEqualTo("PREDICTED_PHYSICS_DELTA");
        assertThat(kb.get("knockBackDistance")).isNotNull();

        Map<String, Object> grab = api.grabEntity(body(
                "playerId", PLAYER, "x", 228f, "y", 0f, "z", 188f));
        assertThat(grab.get("ok")).isEqualTo(true);
        assertThat(grab.get("heldEntityId")).isEqualTo("barrel-valley-1");

        Map<String, Object> thr = api.throwEntity(body(
                "playerId", PLAYER,
                "targetX", 240f, "targetY", 0f, "targetZ", 200f));
        assertThat(thr.get("parabolaServerValidated")).isEqualTo(true);
        assertThat(thr.get("doorBreakDamage")).isNotNull();
    }

    @Test
    public void perceptionModifiersRainAndCave() {
        Map<String, Object> perc = api.perceptionModifiers(
                PLAYER, REGION, "RAIN", "CAVE", 250, true);
        assertThat(perc.get("audioMuffle")).isEqualTo(0.6);
        assertThat(perc.get("visualBlur")).isEqualTo(0.3);
        @SuppressWarnings("unchecked")
        Map<String, Object> cave = (Map<String, Object>) perc.get("cave");
        assertThat(cave.get("environmentAudioProfile")).isEqualTo("CAVE_REVERB");
    }

    @Test
    public void ecoTableauScanAndPassTree() {
        Map<String, Object> scan = api.ecoTableauScan(body("regionId", REGION));
        assertThat(scan.get("ok")).isEqualTo(true);

        for (int i = 0; i < 3; i++) {
            api.ecoPassTree(body(
                    "playerId", PLAYER + 100,
                    "treeId", "test-oak",
                    "x", 300f, "y", 0f, "z", 300f,
                    "hourOfDay", 15, "dayOfYear", 200 + i));
        }
        Map<String, Object> pass = api.ecoPassTree(body(
                "playerId", PLAYER + 100,
                "treeId", "test-oak",
                "x", 300f, "y", 0f, "z", 300f,
                "hourOfDay", 15, "dayOfYear", 203));
        assertThat(pass.get("passCount")).isEqualTo(4);
    }

    @Test
    public void coopTerrainSnapshotSaveAndRecompose() {
        Map<String, Object> snap = api.saveCoopTerrainSnapshot(body(
                "roomId", ROOM,
                "destroyedStaticMeshUids", List.of("bridge-1", "tree-2"),
                "revision", 10L));
        assertThat(snap.get("found")).isEqualTo(true);
        assertThat(snap.get("destroyedStaticMeshUids")).asList().hasSize(2);

        Map<String, Object> tsv = api.terrainTsv(REGION);
        // roomId 与 regionId 不同，TSV 按 region 查；验证 API 可调用
        assertThat(tsv.get("ok")).isEqualTo(true);

        Map<String, Object> recompose = api.coopWorldRecompose(ROOM);
        assertThat(recompose.get("rejectInstantReconnect")).isEqualTo(true);
        assertThat(recompose.get("uiHint")).isEqualTo("世界重组中，请稍候...");
        assertThat(recompose.get("destroyedCount")).isEqualTo(2);
    }

    @Test
    public void coopHostElectionWithTerrainSnapshot() {
        api.saveCoopTerrainSnapshot(body(
                "roomId", "room-elect",
                "destroyedStaticMeshUids", List.of("wall-1"),
                "revision", 5L));
        api.coopHostDisconnect(body("roomId", "room-elect", "hostPlayerId", 1000L));
        api.coopHostElect(body("roomId", "room-elect", "nowMs", System.currentTimeMillis() + 31_000L));
        // 选举需候选人；仅验证 API 不抛异常
    }

    @Test
    public void predictHeartbeatWithCaveEnvironment() {
        Map<String, Object> hb = api.predictHeartbeatEnv(body(
                "playerId", PLAYER, "biome", "CAVE", "echoDelayMs", 220));
        assertThat(hb.get("environmentAudioProfile")).isEqualTo("CAVE_REVERB");
        assertThat(hb.get("caveDepthHint")).isEqualTo("DEEP");
    }

    @Test
    public void fullP19ApiJourney() {
        // 端到端串联：移动 → 投射物 → NPC → 物理 → 感知 → 快照
        assertThat(api.moveAdmit(body(
                "playerId", PLAYER,
                "regionId", REGION,
                "surfaceType", "MUD",
                "x", 233f, "y", 0f, "z", 193f,
                "fromX", 232f, "fromY", 0f, "fromZ", 193f,
                "speed", 6f, "durationMs", 200L)).get("ok")).isEqualTo(true);

        assertThat(api.projectileHitDebris(body(
                "ownerId", PLAYER, "zone", REGION,
                "x", 235f, "y", 1f, "z", 195f)).get("persist")).isEqualTo(true);

        assertThat(api.npcScheduleState("npc-guard-valley", System.currentTimeMillis()).get("ok"))
                .isEqualTo(true);

        assertThat(api.physicsKnockBack(body(
                "attackerId", PLAYER, "targetId", 9002L,
                "attackerForce", 40, "groundFriction", 0.15)).get("event"))
                .isEqualTo("PREDICTED_PHYSICS_DELTA");

        assertThat(api.perceptionModifiers(PLAYER, REGION, "RAIN", "CAVE", 250, true)
                .get("audioMuffle")).isEqualTo(0.6);

        assertThat(api.saveCoopTerrainSnapshot(body(
                "roomId", ROOM + "-full",
                "destroyedStaticMeshUids", List.of("bridge-1"),
                "revision", 10L)).get("found")).isEqualTo(true);
    }

    private static Map<String, Object> body(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}
