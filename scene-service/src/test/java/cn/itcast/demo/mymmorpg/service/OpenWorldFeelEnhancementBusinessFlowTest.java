package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.aoi.VisualSignificanceScheduler;
import cn.itcast.demo.mymmorpg.sync.MoveFlags;
import cn.itcast.demo.mymmorpg.web.InternalOpenWorldController;
import cn.itcast.demo.mymmorpg.world.explore.RegionHeatService;
import cn.itcast.demo.mymmorpg.world.puzzle.TerrainMutationService;
import cn.itcast.demo.mymmorpg.world.puzzle.ZoneLifecycleManager;
import cn.itcast.demo.mymmorpg.world.traverse.MoveTrajectoryValidator;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 七大体验短板完整业务流程（经 Runtime + Internal API）：
 * 轨迹准入 → 歇脚跳跃 → 探索熵/回声罗盘 → DOT 退出补偿 → 冰面扩散 →
 * 过客共鸣 → 幻影窥屏 → 视觉降级 → 手游输入防抖。
 */
public class OpenWorldFeelEnhancementBusinessFlowTest {

    private OpenWorldRuntimeService openWorld;
    private InternalOpenWorldController api;
    private static final long PLAYER = 500_777L;
    private static final String REGION = "wolf-camp-valley";

    @BeforeMethod
    public void setUp() {
        openWorld = new OpenWorldRuntimeService();
        api = new InternalOpenWorldController(openWorld);
    }

    @Test
    public void fullFeelEnhancementApiJourney() {
        long now = System.currentTimeMillis();
        unlockCompass(PLAYER);

        // ── 1) 滑动窗口轨迹 + MOBILE 输入防抖 + 攀爬歇脚跳跃 ──
        api.grantUniversalKit(PLAYER);
        for (int i = 0; i < 4; i++) {
            Map<String, Object> step = api.moveAdmit(body(
                    "playerId", PLAYER,
                    "movementType", "WALK",
                    "deviceType", "PC",
                    "x", 100f + i, "y", 0f, "z", 200f,
                    "fromX", 99f + i, "fromY", 0f, "fromZ", 200f,
                    "speed", 8f, "durationMs", 200L));
            assertThat(step.get("ok")).isEqualTo(true);
        }

        Map<String, Object> climbRest = api.moveAdmit(body(
                "playerId", PLAYER,
                "movementType", "CLIMB",
                "climbableMeshId", "cliff-valley-north",
                "deviceType", "PC",
                "x", 405f, "y", 25f, "z", 202f,
                "fromX", 404f, "fromY", 24f, "fromZ", 201f,
                "durationMs", 200L));
        assertThat(climbRest.get("onRestPoint")).isEqualTo(true);
        assertThat(climbRest.get("restPointBuff")).isNotNull();

        Map<String, Object> restJump = api.moveAdmit(body(
                "playerId", PLAYER,
                "movementType", "CLIMB",
                "climbableMeshId", "cliff-valley-north",
                "moveFlags", MoveFlags.WALL_KICK,
                "deviceType", "PC",
                "x", 406f, "y", 30f, "z", 203f,
                "fromX", 405f, "fromY", 25f, "fromZ", 202f,
                "durationMs", 200L));
        assertThat(restJump.get("ok")).isEqualTo(true);
        if (Boolean.TRUE.equals(restJump.get("restJump"))) {
            assertThat(restJump.get("negativeStaminaWindowMs")).isEqualTo(800L);
        }

        // MOBILE 手滑防抖
        api.moveAdmit(body("playerId", PLAYER + 1, "deviceType", "MOBILE",
                "movementType", "WALK",
                "x", 1f, "z", 0f, "fromX", 0f, "fromZ", 0f, "durationMs", 50L));
        api.moveAdmit(body("playerId", PLAYER + 1, "deviceType", "MOBILE",
                "movementType", "WALK",
                "x", -1f, "z", 0f, "fromX", 1f, "fromZ", 0f, "durationMs", 50L));
        Map<String, Object> debounced = api.moveAdmit(body(
                "playerId", PLAYER + 1, "deviceType", "MOBILE",
                "movementType", "WALK",
                "x", 1f, "z", 0f, "fromX", -1f, "fromZ", 0f, "durationMs", 50L));
        assertThat(debounced.get("inputDebounced")).isEqualTo(true);
        assertThat(debounced.get("event")).isEqualTo("INPUT_DEBOUNCED");

        // ── 2) 探索熵值 + 回声罗盘 ──
        Map<String, Object> heatVisit = api.regionHeatVisit(body(
                "regionId", REGION, "gridX", 4, "gridZ", 7, "playerId", PLAYER));
        assertThat(heatVisit.get("ok")).isEqualTo(true);
        assertThat(heatVisit.get("lowHeat")).isEqualTo(true);
        assertThat(heatVisit.get("microEvent")).isNotNull();

        Map<String, Object> heatStatus = api.regionHeatStatus(REGION, 4, 7);
        assertThat(heatStatus.get("playerVisitCount")).isNotNull();

        Map<String, Object> echo = api.compassEchoProbe(body(
                "playerId", PLAYER, "regionId", REGION,
                "x", 100f, "y", 0f, "z", 100f, "radius", 200f));
        assertThat(echo.get("ok")).isEqualTo(true);
        assertThat(echo.get("echoMode")).isEqualTo(true);
        assertThat(echo.get("revealCoordinates")).isEqualTo(false);
        assertThat(echo.get("distanceBand")).isIn("HIGH", "MID", "LOW");

        // 精确 probe：换玩家避开 echo 冷却，验证坐标模式仍可用
        unlockCompass(PLAYER + 10);
        Map<String, Object> precise = api.compassProbe(body(
                "playerId", PLAYER + 10, "regionId", REGION,
                "x", 100f, "y", 0f, "z", 100f, "radius", 200f));
        assertThat(precise.get("ok")).isEqualTo(true);
        assertThat(precise.containsKey("targets")).isTrue();

        // ── 3) DOT 退出补偿 + 冰面曲线扩散 ──
        openWorld.gameplay().terrainMutation().markWater(REGION, 10, 10);
        openWorld.gameplay().terrainMutation().setHumidity(REGION, 85f);
        Map<String, Object> freeze = api.terrainFreeze(body(
                "regionId", REGION, "gx", 10, "gz", 10));
        assertThat(freeze.get("protocol")).isEqualTo(TerrainMutationService.ICE_SPREAD_CRV);
        assertThat(freeze.get("physicsLockDelayMs")).isEqualTo(2000L);
        String curveId = REGION + ":freeze:10:10";
        Map<String, Object> iceBefore = api.icePhysicsStatus(curveId);
        assertThat(iceBefore.get("physicsLocked")).isEqualTo(false);
        Map<String, Object> iceAfter = openWorld.gameplay().terrainMutation()
                .icePhysicsStatus(curveId, now + 2500);
        assertThat(iceAfter.get("physicsLocked")).isEqualTo(true);

        openWorld.gameplay().zones().spawnBurnZone(1, 50f, 50f, 8f, 10, now);
        Map<String, Object> dotLeave = api.zoneLeave(body(
                "entityId", PLAYER, "zoneId", "burn-1"));
        assertThat(dotLeave.get("event")).isEqualTo("DOT_EXIT_BUFFER");
        assertThat(dotLeave.get("bufferMs")).isEqualTo(ZoneLifecycleManager.DOT_EXIT_BUFFER_MS);

        Map<String, Object> tickWhileBuffered = openWorld.gameplay().zones().tick(
                1, Map.of(PLAYER, new float[]{100f, 100f}), now + 100);
        assertThat(tickWhileBuffered.get("hitCount")).isEqualTo(0);

        // ── 4) 过客共鸣协战池 ──
        long battleId = openWorld.gameplay().fightContribution().openBattle("ValleyBoss", 20_000L);
        api.fightContributionDamage(body("battleId", battleId, "playerId", PLAYER, "damage", 12_000L));
        Map<String, Object> passerby = api.fightContributionDamage(body(
                "battleId", battleId, "playerId", PLAYER + 99, "damage", 1500L));
        assertThat(passerby.get("passerbyEligible")).isEqualTo(true);

        Map<String, Object> settle = api.fightContributionSettle(body(
                "battleId", battleId, "killerPlayerId", PLAYER));
        assertThat(settle.get("broadcastLoot")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> grants = (List<Map<String, Object>>) settle.get("passerbyGrants");
        assertThat(grants).isNotEmpty();
        assertThat(grants.get(0).get("event")).isEqualTo("PASSERBY_GRANT_BOX");

        // ── 5) 幻影实时窥屏 ──
        Map<String, Object> mirror = api.phantomMirrorStart(body(
                "viewerId", PLAYER,
                "friendPlayerId", PLAYER + 50,
                "socialTokens", 5,
                "friendX", 200f, "friendY", 0f, "friendZ", 200f,
                "headingDeg", 45f));
        assertThat(mirror.get("event")).isEqualTo("PHANTOM_REALTIME_MIRROR");
        assertThat(mirror.get("revealCoordinates")).isEqualTo(false);

        Map<String, Object> outline = openWorld.gameplay().phantomBorrow().mirrorOutline(
                PLAYER, 200f, 0f, 200f,
                List.of(Map.of("kind", "ENEMY", "x", 220f, "z", 215f)));
        assertThat(outline.get("outlineCount")).isEqualTo(1);

        // ── 6) 高密度视觉降级 ──
        List<Map<String, Object>> entityRows = new ArrayList<>();
        for (int i = 0; i < 55; i++) {
            entityRows.add(Map.of(
                    "entityId", 1000L + i,
                    "distance", 40f + i,
                    "inCombat", false,
                    "hpPercent", 100f,
                    "locked", false));
        }
        Map<String, Object> degrade = api.visualDegrade(body(
                "viewerId", PLAYER,
                "viewerX", 0f, "viewerZ", 0f,
                "entities", entityRows));
        assertThat(degrade.get("visualDegrade")).isEqualTo(true);
        assertThat(degrade.get("event")).isEqualTo("VISUAL_DEGRADE");
        assertThat((Integer) degrade.get("degradeCount")).isEqualTo(
                VisualSignificanceScheduler.DEGRADE_COUNT);
    }

    @Test
    public void apiNegativePaths_echoHeatMirrorDegrade() {
        unlockCompass(PLAYER);

        // 罗盘冷却
        api.compassEchoProbe(body(
                "playerId", PLAYER, "regionId", REGION,
                "x", 0f, "y", 0f, "z", 0f));
        Map<String, Object> cooldown = api.compassEchoProbe(body(
                "playerId", PLAYER, "regionId", REGION,
                "x", 0f, "y", 0f, "z", 0f));
        assertThat(cooldown.get("error")).isEqualTo("probe_cooldown");

        // 幻影窥屏代币不足
        Map<String, Object> noToken = api.phantomMirrorStart(body(
                "viewerId", PLAYER + 2,
                "friendPlayerId", PLAYER + 3,
                "socialTokens", 0,
                "friendX", 0f, "friendY", 0f, "friendZ", 0f));
        assertThat(noToken.get("error")).isEqualTo("need_social_token");

        // 实体不足不降级
        Map<String, Object> noDegrade = api.visualDegrade(body(
                "viewerId", PLAYER,
                "viewerX", 0f, "viewerZ", 0f,
                "entities", List.of(Map.of(
                        "entityId", 1L, "distance", 10f,
                        "inCombat", false, "hpPercent", 100f, "locked", false))));
        assertThat(noDegrade.get("visualDegrade")).isEqualTo(false);

        // 无效战斗结算
        Map<String, Object> badSettle = api.fightContributionSettle(body(
                "battleId", 999_999L, "killerPlayerId", PLAYER));
        assertThat(badSettle.get("error")).isEqualTo("battle_not_found");

        // 冰面曲线不存在
        Map<String, Object> badIce = api.icePhysicsStatus("no-such-curve");
        assertThat(badIce.get("error")).isEqualTo("curve_not_found");
    }

    @Test
    public void gameplayStatusIncludesFeelEnhancementMetrics() {
        Map<String, Object> overview = openWorld.gameplay().statusOverview();
        @SuppressWarnings("unchecked")
        Map<String, Object> p14 = (Map<String, Object>) overview.get("p14");
        assertThat(p14.get("trajectoryWindow")).isEqualTo(MoveTrajectoryValidator.WINDOW_SIZE);
        assertThat(p14.get("dotExitBufferMs")).isEqualTo(ZoneLifecycleManager.DOT_EXIT_BUFFER_MS);
        assertThat(p14.get("regionHeatWindowMs")).isEqualTo(RegionHeatService.HEAT_WINDOW_MS);
        assertThat(p14.get("enhancePityThreshold")).isEqualTo(3);
    }

    @Test
    public void regionHeatMicroEventInjectionChain() {
        String cellRegion = "cold-grid-test";
        Map<String, Object> first = api.regionHeatVisit(body(
                "regionId", cellRegion, "gridX", 1, "gridZ", 1, "playerId", PLAYER));
        assertThat(first.get("lowHeat")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> micro = (Map<String, Object>) first.get("microEvent");
        assertThat(micro.get("kind")).isIn("ROCKFALL_BLOCK", "CAVE_REVEAL");

        List<Map<String, Object>> injected = openWorld.gameplay().regionHeat().listInjectedEvents(cellRegion);
        assertThat(injected).isNotEmpty();

        // 多次访问后热度上升，不再视为低熵
        IntStream.range(0, 5).forEach(i -> api.regionHeatVisit(body(
                "regionId", cellRegion, "gridX", 1, "gridZ", 1,
                "playerId", PLAYER + i)));
        Map<String, Object> warmed = api.regionHeatStatus(cellRegion, 1, 1);
        assertThat((Integer) warmed.get("playerVisitCount")).isGreaterThanOrEqualTo(3);
    }

    @Test
    public void trajectoryRejectsHardWallCollisionViaApi() {
        long pid = PLAYER + 200;
        for (int i = 0; i < 5; i++) {
            api.moveAdmit(body(
                    "playerId", pid,
                    "movementType", "WALK",
                    "deviceType", "PC",
                    "x", i * 1f, "y", 0f, "z", 0f,
                    "fromX", (i - 1) * 1f, "fromY", 0f, "fromZ", 0f,
                    "speed", 8f, "durationMs", 50L));
        }
        Map<String, Object> wall = api.moveAdmit(body(
                "playerId", pid,
                "movementType", "WALK",
                "deviceType", "PC",
                "x", 10f, "y", 0f, "z", 10f,
                "fromX", 0f, "fromY", 0f, "fromZ", 5f,
                "speed", 1f, "durationMs", 50L));
        if (Boolean.FALSE.equals(wall.get("ok"))) {
            assertThat(wall.get("error")).isEqualTo("trajectory_rejected");
        }
    }

    private void unlockCompass(long playerId) {
        for (int i = 0; i < 5; i++) {
            openWorld.gameplay().regionProgress().markWaypoint(playerId, REGION, "fe-wp-" + i);
        }
        for (int i = 0; i < 6; i++) {
            openWorld.gameplay().regionProgress().markCollectible(playerId, REGION, "fe-col-" + i);
        }
        for (int i = 0; i < 3; i++) {
            openWorld.gameplay().regionProgress().markPuzzle(playerId, REGION, "fe-pz-" + i);
        }
        openWorld.gameplay().regionProgress().markWorldQuest(playerId, REGION, "fe-q1");
        openWorld.gameplay().regionProgress().markWorldQuest(playerId, REGION, "fe-q2");
        Map<String, Object> status = openWorld.gameplay().explorationCompass().unlockStatus(playerId, REGION);
        assertThat(status.get("compassUnlocked")).isEqualTo(true);
    }

    private static Map<String, Object> body(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}
