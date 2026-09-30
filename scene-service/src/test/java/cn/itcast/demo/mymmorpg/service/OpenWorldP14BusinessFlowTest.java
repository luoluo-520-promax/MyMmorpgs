package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.sync.MovementType;
import cn.itcast.demo.mymmorpg.web.InternalOpenWorldController;
import cn.itcast.demo.mymmorpg.world.battle.CombatAssistService;
import cn.itcast.demo.mymmorpg.world.battle.ReactionValidator;
import cn.itcast.demo.mymmorpg.world.endgame.AffixShuffleService;
import cn.itcast.demo.mymmorpg.world.explore.CollectibleService;
import cn.itcast.demo.mymmorpg.world.explore.RegionImpactService;
import cn.itcast.demo.mymmorpg.world.progression.BuildRecommendationService;
import cn.itcast.demo.mymmorpg.world.puzzle.WorldMutabilityService;
import cn.itcast.demo.mymmorpg.world.social.PhantomBorrowService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P14 完整业务流程（经 Runtime + Internal API）：
 * 客户端预测 → 动态闪避窗 → 动量继承/歇脚加成 → 确定性破坏 →
 * 探索活力/神瞳共鸣 → 野外营地/幻影借用/助战印记 → 多端辅助/连招宏 → 词缀轮换/命运残响。
 */
public class OpenWorldP14BusinessFlowTest {

    private OpenWorldRuntimeService openWorld;
    private InternalOpenWorldController api;
    private static final long PLAYER = 400_014L;
    private static final String REGION = "wolf-camp-valley";

    @BeforeMethod
    public void setUp() {
        openWorld = new OpenWorldRuntimeService();
        api = new InternalOpenWorldController(openWorld);
    }

    @Test
    public void fullP14ApiJourney_predictMoveDestroyExploreSocialEndgame() {
        long now = System.currentTimeMillis();

        // ── 1) 客户端预测：按下即播 → 软回滚保留表现 ──
        Map<String, Object> predictStart = api.predictActionStart(body(
                "playerId", PLAYER,
                "actionId", "dodge-p14-1",
                "action", "DODGE",
                "x", 10f, "y", 0f, "z", 10f,
                "speed", 12f,
                "clientTs", now));
        assertThat(predictStart.get("playAnimationImmediately")).isEqualTo(true);
        assertThat(predictStart.get("event")).isEqualTo("CLIENT_PREDICT_START");

        Map<String, Object> reconcile = api.predictActionReconcile(body(
                "playerId", PLAYER,
                "actionId", "dodge-p14-1",
                "clientTs", now,
                "x", 12f, "y", 0f, "z", 12f,
                "speed", 12f,
                "hitValid", false,
                "cooldownValid", true));
        assertThat(reconcile.get("softRollback")).isEqualTo(true);
        assertThat(reconcile.get("keepVisuals")).isEqualTo(true);
        assertThat(reconcile.get("rubberBand")).isEqualTo(false);

        // ── 2) 动态闪避窗（RTT 补偿）+ 预表现确认 ──
        Map<String, Object> window = api.reactionOpenWindow(body(
                "attackId", "atk-p14-rtt",
                "attackerEntityId", 8001L,
                "dodgeWindowMs", 200,
                "parryWindowMs", 180,
                "playerRttMs", 80));
        assertThat(window.get("dodgeWindowMs")).isEqualTo(240);
        assertThat(window.get("dynamicWindow")).isEqualTo(true);

        api.prePlaybackStart(body("playerId", PLAYER, "actionId", "hit-p14-1"));
        Map<String, Object> softPb = openWorld.gameplay().prePlayback().confirmOrRollback(
                PLAYER, "hit-p14-1", now, now + 120, false, true);
        assertThat(softPb.get("softRollback")).isEqualTo(true);
        assertThat(softPb.get("keepAnimation")).isEqualTo(true);

        // ── 3) 立体移动：歇脚攀爬跳加成 + 动量继承 ──
        api.grantUniversalKit(PLAYER);
        openWorld.gameplay().stamina().consume(PLAYER, MovementType.CLIMB, 5_000L, 1f, 1_000L);
        Map<String, Object> climbRest = api.moveAdmit(body(
                "playerId", PLAYER,
                "movementType", "CLIMB",
                "climbableMeshId", "cliff-valley-north",
                "x", 405f, "y", 25f, "z", 202f,
                "fromX", 404f, "fromY", 24f, "fromZ", 201f,
                "durationMs", 200L));
        assertThat(climbRest.get("onRestPoint")).isEqualTo(true);
        assertThat(openWorld.gameplay().climbRestPoints().climbJumpMultiplier(PLAYER)).isEqualTo(1.3f);

        var momentum = openWorld.gameplay().traverseMomentum().inheritOnTransition(
                MovementType.GLIDE, MovementType.WALK,
                new cn.itcast.demo.mymmorpg.world.traverse.TraverseMomentumService.VelocityVector(
                        10f, -1f, 8f, 12f));
        assertThat(momentum.speed()).isLessThan(12f);
        assertThat(momentum.vx()).isEqualTo(7f);

        // ── 4) 确定性破坏 + 地形客户端缓存 ──
        Map<String, Object> det = api.mutabilityDeterministic(body(
                "destroyableId", "tree-valley-1",
                "respawnCooldownMs", 90_000L));
        assertThat(det.get("clientPlayImmediately")).isEqualTo(true);
        assertThat(det.get("seed")).isNotNull();

        WorldMutabilityService.TerrainInteractionTracker tracker =
                openWorld.gameplay().mutability().terrainTracker();
        tracker.tryConsume(REGION, 5, 5, WorldMutabilityService.TerrainKind.BOUNCE_MUSHROOM, 60_000L, now);
        Map<String, Object> cacheHit = tracker.clientCacheCheck(REGION, 5, 5, now + 50_000L, now + 1000);
        assertThat(cacheHit.get("clientCacheHit")).isEqualTo(true);

        // ── 5) 探索活力 + 神瞳共鸣 ──
        Map<String, Object> vitality = api.exploreVitalityDaily(PLAYER, REGION);
        assertThat(vitality.get("ok")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> surveys = (List<Map<String, Object>>) vitality.get("surveyPoints");
        assertThat(surveys).isNotEmpty();
        String surveyId = String.valueOf(surveys.get(0).get("pointId"));
        Map<String, Object> surveyDone = api.exploreVitalityComplete(body(
                "playerId", PLAYER, "pointId", surveyId));
        assertThat(surveyDone.get("ok")).isEqualTo(true);
        assertThat(surveyDone.get("grantPlans")).isNotNull();

        seedOculiForResonance(PLAYER);

        // ── 6) 野外营地 + 幻影借用 + 助战印记 ──
        Map<String, Object> camp = api.campEstablish(body(
                "leaderId", PLAYER,
                "partyMemberIds", List.of(PLAYER + 1, PLAYER + 2),
                "regionId", REGION,
                "regionSafe", true,
                "x", 50f, "y", 0f, "z", 50f));
        assertThat(camp.get("ok")).isEqualTo(true);
        assertThat(((List<?>) camp.get("facilities")).size()).isEqualTo(3);

        Map<String, Object> sign = api.campSign(body("playerId", PLAYER, "campId", camp.get("campId")));
        assertThat(sign.get("ok")).isEqualTo(true);

        openWorld.gameplay().phantomBorrow().recordPhantom(
                "coop-puzzle-p14", PLAYER + 99, List.of("LEVER_A", "LEVER_B", "GATE_OPEN"));
        for (int i = 0; i < PhantomBorrowService.FAIL_THRESHOLD; i++) {
            openWorld.gameplay().phantomBorrow().onPuzzleFail(PLAYER, "coop-puzzle-p14");
        }
        Map<String, Object> borrow = api.phantomBorrow(body(
                "playerId", PLAYER,
                "puzzleId", "coop-puzzle-p14",
                "friendPlayerId", PLAYER + 99));
        assertThat(borrow.get("event")).isEqualTo("PHANTOM_BORROW_EXECUTE");

        Map<String, Object> token = api.socialTokenGrant(body(
                "helperId", PLAYER + 99,
                "helpedId", PLAYER,
                "reason", "puzzle_borrow"));
        assertThat(token.get("tokensAdded")).isEqualTo(3);

        // ── 7) 多端辅助 + 连招宏 ──
        Map<String, Object> mobile = api.combatAssistDevice(body(
                "playerId", PLAYER, "deviceType", "MOBILE"));
        assertThat(mobile.get("dodgeWindowBonusMs")).isEqualTo(30);
        assertThat(mobile.get("softLock")).isEqualTo(true);

        Map<String, Object> softLock = api.combatAssistSoftLock(body(
                "playerId", PLAYER,
                "aimYawDeg", 0f,
                "nearestEnemyYawDeg", 12f,
                "distanceM", 18f));
        assertThat(softLock.get("corrected")).isEqualTo(true);

        Map<String, Object> macroSave = api.buildMacroSave(body(
                "playerId", PLAYER,
                "slotIndex", 0,
                "skillSequence", List.of("NORMAL_1", "SKILL_E", "FINISHER")));
        assertThat(macroSave.get("ok")).isEqualTo(true);

        Map<String, Object> macroTrigger = api.buildMacroTrigger(body(
                "playerId", PLAYER, "slotIndex", 0));
        assertThat(macroTrigger.get("serverValidated")).isEqualTo(true);
        assertThat(macroTrigger.get("skillSequence")).isNotNull();

        Map<String, Object> pc = api.combatAssistDevice(body(
                "playerId", PLAYER + 1, "deviceType", "PC"));
        assertThat(pc.get("lootWeightBonus")).isEqualTo(0.05f);

        // ── 8) 词缀轮换 + 肉鸽命运残响闭环 ──
        Map<String, Object> affix = api.rogueWeeklyAffix(1L);
        @SuppressWarnings("unchecked")
        List<String> weekly = (List<String>) affix.get("weeklyAffixList");
        assertThat(weekly).hasSize(AffixShuffleService.WEEKLY_AFFIX_COUNT);

        openWorld.gameplay().regions().register(new RegionImpactService.RegionProfile(
                "rogue-p14-region", "P14肉鸽区", 1, 3, List.of()));
        openWorld.gameplay().regions().forceSafety("rogue-p14-region", RegionImpactService.RegionSafety.CHAOS);
        for (int i = 0; i < 4; i++) {
            openWorld.gameplay().regions().addTidePurify("rogue-p14-region", 30, now);
        }

        Map<String, Object> rogueStart = openWorld.gameplay().rogueFate().startWithRegion(
                PLAYER, 14, "rogue-p14-region", now);
        assertThat(rogueStart.get("mapLinked")).isEqualTo(true);

        Map<String, Object> rogueSettle = openWorld.gameplay().rogueFate().settleClear(PLAYER, true, now);
        assertThat(rogueSettle.get("loopClosed")).isEqualTo(true);
        assertThat(rogueSettle.get("fateEchoEarned")).isEqualTo(10);

        Map<String, Object> echo = api.rogueFateEchoExchange(body(
                "playerId", PLAYER, "mainStat", "CRIT_RATE", "cost", 10));
        assertThat(echo.get("ok")).isEqualTo(true);
        assertThat(((Map<?, ?>) echo.get("grantPlan")).get("subStatsRandom")).isEqualTo(true);
    }

    @Test
    public void apiNegativePaths_predictCampPhantomMacro() {
        Map<String, Object> badCamp = api.campEstablish(body(
                "leaderId", PLAYER,
                "regionId", REGION,
                "regionSafe", false,
                "x", 0f, "y", 0f, "z", 0f));
        assertThat(badCamp.get("ok")).isEqualTo(false);

        Map<String, Object> borrowEarly = api.phantomBorrow(body(
                "playerId", PLAYER,
                "puzzleId", "no-fails",
                "friendPlayerId", 1L));
        assertThat(borrowEarly.get("error")).isEqualTo("fail_count_insufficient");

        long now = System.currentTimeMillis();
        api.buildMacroSave(body(
                "playerId", PLAYER + 50,
                "slotIndex", 0,
                "skillSequence", List.of("A", "B")));
        assertThat(api.buildMacroTrigger(body(
                "playerId", PLAYER + 50, "slotIndex", 0)).get("ok")).isEqualTo(true);
        Map<String, Object> tooFast = openWorld.gameplay().buildRecommend().triggerMacro(
                PLAYER + 50, 0, now + 500);
        assertThat(tooFast.get("error")).isEqualTo("macro_rate_limited");

        Map<String, Object> badEcho = api.rogueFateEchoExchange(body(
                "playerId", PLAYER + 51, "mainStat", "ATK", "cost", 100));
        assertThat(badEcho.get("error")).isEqualTo("insufficient_fate_echo");
    }

    @Test
    public void gameplayStatusIncludesP14AfterJourney() {
        api.combatAssistDevice(body("playerId", PLAYER + 77, "deviceType", "MOBILE"));
        api.predictActionStart(body(
                "playerId", PLAYER + 77,
                "actionId", "a1",
                "x", 0f, "y", 0f, "z", 0f,
                "clientTs", System.currentTimeMillis()));

        Map<String, Object> overview = openWorld.gameplay().statusOverview();
        @SuppressWarnings("unchecked")
        Map<String, Object> p14 = (Map<String, Object>) overview.get("p14");
        assertThat(p14.get("clientPredict")).isEqualTo(true);
        assertThat(p14.get("weeklyAffixCount")).isEqualTo(AffixShuffleService.WEEKLY_AFFIX_COUNT);
    }

    private void seedOculiForResonance(long playerId) {
        for (int i = 0; i < 9; i++) {
            openWorld.gameplay().collectibles().register(new CollectibleService.CollectibleDef(
                    "p14-oc-" + i, "瞳" + i, CollectibleService.Tier.OCULUS,
                    1, 300f + i, 15f, 300f, 5f, "oculus_fragment", 1, 1, 5));
        }
        for (int i = 0; i < 7; i++) {
            openWorld.gameplay().collectibles().collect(
                    playerId, "p14-oc-" + i, 300f + i, 15f, 300f);
        }
        Map<String, Object> resonance = api.oculiResonance(playerId, 1);
        assertThat(resonance.get("resonanceTriggered")).isEqualTo(true);
        assertThat(resonance.get("pulseDurationMs")).isEqualTo(5000L);
    }

    private static Map<String, Object> body(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}
