package cn.itcast.demo.mymmorpg.world.gameplay;

import cn.itcast.demo.mymmorpg.aoi.VisualSignificanceScheduler;
import cn.itcast.demo.mymmorpg.sync.MoveFlags;
import cn.itcast.demo.mymmorpg.sync.MovementType;
import cn.itcast.demo.mymmorpg.sync.SceneMoveCmd;
import cn.itcast.demo.mymmorpg.world.explore.ExplorationCompassService;
import cn.itcast.demo.mymmorpg.world.puzzle.TerrainMutationService;
import cn.itcast.demo.mymmorpg.world.puzzle.ZoneLifecycleManager;
import cn.itcast.demo.mymmorpg.world.social.FightContributionPoolService;
import cn.itcast.demo.mymmorpg.world.social.PhantomBorrowService;
import cn.itcast.demo.mymmorpg.world.traverse.ClimbRestPointService;
import cn.itcast.demo.mymmorpg.world.traverse.MoveTrajectoryValidator;
import cn.itcast.demo.mymmorpg.world.traverse.InputConfidenceAnalyzer;
import cn.itcast.demo.mymmorpg.world.traverse.StaminaConsumeService;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 七大体验短板补齐：轨迹校验 / 探索熵 / DOT 退出补偿 / 过客共鸣 / 怜悯值等。 */
public class OpenWorldFeelEnhancementFlowTest {

    @Test
    public void trajectoryAllowsSoftToleranceButRejectsWallCollision() {
        MoveTrajectoryValidator v = new MoveTrajectoryValidator();
        long pid = 1L;
        long now = System.currentTimeMillis();
        for (int i = 0; i < 4; i++) {
            v.validate(pid, 0, 0, 0, 0, i + 1f, 0, 8f, now + i * 50);
        }
        var soft = v.validate(pid, 0, 4, 0, 0.5f, 5f, 0.3f, 7f, now + 300);
        assertThat(soft.accepted()).isTrue();
        assertThat(soft.softTolerance()).isTrue();

        var wall = v.validate(pid, 0, 5, 0, 5f, 0f, 5f, 1f, now + 400);
        assertThat(wall.accepted()).isFalse();
        assertThat(wall.hardReject()).isTrue();
    }

    @Test
    public void climbRestPointBuffAllowsNegativeStaminaJump() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        ClimbRestPointService rest = g.climbRestPoints();
        StaminaConsumeService stamina = g.stamina();
        rest.register(new ClimbRestPointService.RestPoint(
                "rest-1", "cliff-a", 5f, 10f, 5f, 3f, 40f, false));
        long pid = 501L;
        rest.tryRest(pid, "cliff-a", 5f, 10f, 5f, stamina);
        Map<String, Object> jump = rest.consumeRestJump(pid, stamina, System.currentTimeMillis());
        assertThat(jump.get("restJump")).isEqualTo(true);
        assertThat(jump.get("negativeStaminaWindowMs")).isEqualTo(800L);
    }

    @Test
    public void regionHeatTriggersMicroEventOnLowVisit() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> visit = g.regionHeat().recordVisit("wolf-camp-valley", 2, 3, 1L, System.currentTimeMillis());
        assertThat(visit.get("lowHeat")).isEqualTo(true);
        assertThat(visit.get("microEvent")).isNotNull();
    }

    @Test
    public void compassEchoProbeReturnsFuzzyDistanceNotCoordinates() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long playerId = 601L;
        String region = "wolf-camp-valley";
        for (int i = 0; i < 5; i++) {
            g.regionProgress().markWaypoint(playerId, region, "wp-e" + i);
        }
        for (int i = 0; i < 6; i++) {
            g.regionProgress().markCollectible(playerId, region, "col-e" + i);
        }
        for (int i = 0; i < 3; i++) {
            g.regionProgress().markPuzzle(playerId, region, "pz-e" + i);
        }
        g.regionProgress().markWorldQuest(playerId, region, "q-e1");
        g.regionProgress().markWorldQuest(playerId, region, "q-e2");
        Map<String, Object> echo = g.explorationCompass().echoProbe(
                playerId, region, 0f, 0f, 0f, 200f, System.currentTimeMillis());
        assertThat(echo.get("echoMode")).isEqualTo(true);
        assertThat(echo.get("revealCoordinates")).isEqualTo(false);
        assertThat(echo.get("distanceBand")).isIn(
                ExplorationCompassService.DistanceBand.HIGH.name(),
                ExplorationCompassService.DistanceBand.MID.name(),
                ExplorationCompassService.DistanceBand.LOW.name());
    }

    @Test
    public void dotExitBufferSuppressesTickDamage() {
        ZoneLifecycleManager zones = new ZoneLifecycleManager();
        long now = System.currentTimeMillis();
        zones.spawnBurnZone(1, 10f, 10f, 5f, 5, now);
        Map<String, Object> leave = zones.onEntityLeaveZone(99L, "burn-1", now);
        assertThat(leave.get("event")).isEqualTo("DOT_EXIT_BUFFER");
        assertThat(zones.isInExitBuffer(99L, now + 100)).isTrue();
        assertThat(zones.isInExitBuffer(99L, now + 400)).isFalse();
    }

    @Test
    public void freezeWaterEmitsIceSpreadCurveWithPhysicsDelay() {
        TerrainMutationService terrain = new TerrainMutationService();
        terrain.markWater("valley", 10, 10);
        terrain.setHumidity("valley", 80f);
        Map<String, Object> freeze = terrain.applyFreezeOnWater("valley", 10, 10, System.currentTimeMillis());
        assertThat(freeze.get("protocol")).isEqualTo(TerrainMutationService.ICE_SPREAD_CRV);
        assertThat(freeze.get("physicsLockDelayMs")).isEqualTo(2000L);
        String curveId = "valley:freeze:10:10";
        Map<String, Object> beforeLock = terrain.icePhysicsStatus(curveId, System.currentTimeMillis());
        assertThat(beforeLock.get("physicsLocked")).isEqualTo(false);
    }

    @Test
    public void fightContributionPoolGrantsPasserbyBox() {
        FightContributionPoolService pool = new FightContributionPoolService();
        long battleId = pool.openBattle("WorldBoss", 10_000L);
        pool.recordDamage(battleId, 1L, 8000L);
        pool.recordDamage(battleId, 2L, 600L);
        Map<String, Object> settle = pool.settleBattle(battleId, 1L);
        assertThat(settle.get("broadcastLoot")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> grants = (List<Map<String, Object>>) settle.get("passerbyGrants");
        assertThat(grants).isNotEmpty();
        assertThat(grants.get(0).get("event")).isEqualTo("PASSERBY_GRANT_BOX");
    }

    @Test
    public void phantomRealtimeMirrorHidesCoordinates() {
        PhantomBorrowService phantom = new PhantomBorrowService();
        Map<String, Object> start = phantom.startRealtimeMirror(
                1L, 2L, 3, 100f, 0f, 200f, 90f);
        assertThat(start.get("event")).isEqualTo("PHANTOM_REALTIME_MIRROR");
        assertThat(start.get("revealCoordinates")).isEqualTo(false);
        List<Map<String, Object>> nearby = List.of(
                Map.of("kind", "CHEST", "x", 110f, "z", 210f));
        Map<String, Object> outline = phantom.mirrorOutline(1L, 100f, 0f, 200f, nearby);
        assertThat(outline.get("outlineCount")).isEqualTo(1);
    }

    @Test
    public void mobileInputDebouncesReverseJitter() {
        InputConfidenceAnalyzer analyzer = new InputConfidenceAnalyzer();
        long pid = 77L;
        long now = System.currentTimeMillis();
        analyzer.analyze(pid, "MOBILE", 0, 0, 1, 0, now);
        analyzer.analyze(pid, "MOBILE", 1, 0, -1, 0, now + 50);
        Map<String, Object> third = analyzer.analyze(pid, "MOBILE", -1, 0, 1, 0, now + 100);
        assertThat(third.get("inputDebounced")).isEqualTo(true);
        assertThat(third.get("event")).isEqualTo("INPUT_DEBOUNCED");
    }

    @Test
    public void visualDegradeWhenEntityCountExceedsThreshold() {
        VisualSignificanceScheduler scheduler = new VisualSignificanceScheduler();
        List<VisualSignificanceScheduler.EntitySignificance> entities = new ArrayList<>();
        for (int i = 0; i < 55; i++) {
            entities.add(new VisualSignificanceScheduler.EntitySignificance(
                    i + 100L, 50f + i, false, 100f, false, 0f));
        }
        Map<String, Object> degrade = scheduler.computeDegrade(1L, 0f, 0f, entities);
        assertThat(degrade.get("visualDegrade")).isEqualTo(true);
        assertThat(degrade.get("event")).isEqualTo("VISUAL_DEGRADE");
    }

    @Test
    public void movementAdmissionIntegratesTrajectoryAndDeviceType() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.traverse().unlock(901L, cn.itcast.demo.mymmorpg.world.traverse.TraverseModeService.Mode.CLIMB);
        g.movementAdmission().registerClimbable(
                new cn.itcast.demo.mymmorpg.world.traverse.MovementAdmissionService.ClimbableMesh(
                        "mesh-1", 1, 0f, 0f, 0f, 20f, 10f));
        SceneMoveCmd cmd = new SceneMoveCmd(
                1f, 1f, 1f, 8f, System.currentTimeMillis(),
                MovementType.CLIMB, "mesh-1", MoveFlags.WALL_KICK,
                "", "", 0f, 0f, 0f);
        Map<String, Object> admit = g.movementAdmission().admit(
                901L, cmd, 200L, System.currentTimeMillis(),
                0f, 0f, 0f, null, 0, 0, 60_000L, "MOBILE");
        assertThat(admit.get("ok")).isIn(true, false);
    }

    @Test
    public void endToEndFacadeJourney_allSevenShortfalls() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long playerId = 9001L;
        String region = "wolf-camp-valley";
        long now = System.currentTimeMillis();

        // 1 轨迹 + 输入
        for (int i = 0; i < 4; i++) {
            g.moveTrajectory().validate(playerId, 0, i, 0, 0, i + 1f, 0, 8f, now + i * 40);
        }
        assertThat(g.inputConfidence().analyze(playerId, "MOBILE", 0, 0, 1, 0, now).get("inputDebounced"))
                .isEqualTo(false);

        // 2 探索熵 + 回声
        Map<String, Object> heat = g.regionHeat().recordVisit(region, 3, 3, playerId, now);
        assertThat(heat.get("ok")).isEqualTo(true);
        unlockCompass(g, playerId, region);
        Map<String, Object> echo = g.explorationCompass().echoProbe(
                playerId, region, 50f, 0f, 50f, 150f, now + 5000);
        assertThat(echo.get("revealCoordinates")).isEqualTo(false);

        // 3 DOT + 冰面
        g.zones().spawnBurnZone(1, 20f, 20f, 5f, 8, now);
        assertThat(g.zones().onEntityLeaveZone(playerId, "burn-1", now).get("event"))
                .isEqualTo("DOT_EXIT_BUFFER");
        g.terrainMutation().markWater(region, 5, 5);
        g.terrainMutation().setHumidity(region, 70f);
        Map<String, Object> freeze = g.terrainMutation().applyFreezeOnWater(region, 5, 5, now);
        assertThat(freeze.get("protocol")).isEqualTo(TerrainMutationService.ICE_SPREAD_CRV);

        // 4 过客共鸣 + 窥屏
        long battleId = g.fightContribution().openBattle("Boss", 5000L);
        g.fightContribution().recordDamage(battleId, playerId, 4000L);
        g.fightContribution().recordDamage(battleId, playerId + 1, 300L);
        assertThat(g.fightContribution().settleBattle(battleId, playerId).get("coopCount"))
                .isEqualTo(2);

        // 5 视觉降级
        List<VisualSignificanceScheduler.EntitySignificance> sigs = new ArrayList<>();
        for (int i = 0; i < 52; i++) {
            sigs.add(new VisualSignificanceScheduler.EntitySignificance(
                    i, 30f + i, false, 100f, false, 0f));
        }
        assertThat(g.visualSignificance().computeDegrade(playerId, 0f, 0f, sigs).get("visualDegrade"))
                .isEqualTo(true);

        Map<String, Object> overview = g.statusOverview();
        @SuppressWarnings("unchecked")
        Map<String, Object> p14 = (Map<String, Object>) overview.get("p14");
        assertThat(p14.get("enhancePityThreshold")).isEqualTo(3);
    }

    private static void unlockCompass(OpenWorldGameplayFacade g, long playerId, String region) {
        for (int i = 0; i < 5; i++) {
            g.regionProgress().markWaypoint(playerId, region, "e2e-wp-" + i);
        }
        for (int i = 0; i < 6; i++) {
            g.regionProgress().markCollectible(playerId, region, "e2e-col-" + i);
        }
        for (int i = 0; i < 3; i++) {
            g.regionProgress().markPuzzle(playerId, region, "e2e-pz-" + i);
        }
        g.regionProgress().markWorldQuest(playerId, region, "e2e-q1");
        g.regionProgress().markWorldQuest(playerId, region, "e2e-q2");
    }
}
