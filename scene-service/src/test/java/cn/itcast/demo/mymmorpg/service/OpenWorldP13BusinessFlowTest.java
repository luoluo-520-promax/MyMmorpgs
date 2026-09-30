package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.sync.MovementType;
import cn.itcast.demo.mymmorpg.web.InternalOpenWorldController;
import cn.itcast.demo.mymmorpg.world.battle.CombatAssistService;
import cn.itcast.demo.mymmorpg.world.ecosystem.WorldExplorationFeedbackService;
import cn.itcast.demo.mymmorpg.world.explore.ExplorationCompassService;
import cn.itcast.demo.mymmorpg.world.progression.FlexibleDailyQuestService;
import cn.itcast.demo.mymmorpg.world.traverse.RegionalTraverseService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P13 完整业务流程（经 Runtime + Internal API）：
 * 探索罗盘 → 地图标记 → 世界影响 → 区域移动 → 通用探索套件 →
 * 生态叙事 → 环境叙事 → 探索反馈 → 战斗辅助 → 自动化/日常/养成。
 */
public class OpenWorldP13BusinessFlowTest {

    private OpenWorldRuntimeService openWorld;
    private InternalOpenWorldController api;
    private static final long PLAYER = 300_013L;
    private static final String REGION = "wolf-camp-valley";

    @BeforeMethod
    public void setUp() {
        openWorld = new OpenWorldRuntimeService();
        api = new InternalOpenWorldController(openWorld);
    }

    @Test
    public void fullP13ApiJourney_exploreMoveEcoCombatProgression() {
        // ── 1) 探索罗盘：未达阈值锁定 → 刷探索度 → 探测剩余目标 ──
        Map<String, Object> locked = api.compassStatus(PLAYER, REGION);
        assertThat(locked.get("compassUnlocked")).isEqualTo(false);
        assertThat(locked.get("unlockThreshold")).isEqualTo(ExplorationCompassService.UNLOCK_THRESHOLD_PERCENT);

        boostExploration(PLAYER, 5, 6, 3, 2);

        Map<String, Object> unlocked = api.compassStatus(PLAYER, REGION);
        assertThat(unlocked.get("compassUnlocked")).isEqualTo(true);

        Map<String, Object> probe = api.compassProbe(body(
                "playerId", PLAYER,
                "regionId", REGION,
                "x", 100f, "y", 0f, "z", 100f,
                "radius", 200f));
        assertThat(probe.get("ok")).isEqualTo(true);
        assertThat((Integer) probe.get("remainingCount")).isGreaterThan(0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> targets = (List<Map<String, Object>>) probe.get("targets");
        assertThat(targets.get(0)).containsKeys("targetId", "kind", "distanceM", "mapIcon");

        // 限时挑战完成后不再出现在探测列表
        api.compassProbe(body("playerId", PLAYER, "regionId", REGION,
                "x", 148f, "y", 4f, "z", 115f, "radius", 20f));
        openWorld.gameplay().explorationCompass()
                .markChallengeComplete(PLAYER, REGION, "challenge-timed-1");

        // ── 2) 大地图标记：收集前后状态变化 ──
        Map<String, Object> markersBefore = api.mapMarkers(PLAYER, REGION);
        assertThat(markersBefore.get("ok")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> markerList = (List<Map<String, Object>>) markersBefore.get("markers");
        assertThat(markerList).isNotEmpty();

        Map<String, Object> collect = api.collectibleCollect(body(
                "playerId", PLAYER,
                "collectibleId", "chest-common-1",
                "regionId", REGION,
                "x", 100f, "y", 0f, "z", 100f));
        assertThat(collect.get("ok")).isEqualTo(true);
        assertThat(collect.get("exploration_impacts")).isNotNull();

        Map<String, Object> impact = api.exploreWorldImpact(PLAYER, REGION);
        assertThat(impact.get("ok")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> active = (List<Map<String, Object>>) impact.get("activeImpacts");
        assertThat(active).isNotEmpty();
        assertThat(active.stream().anyMatch(r ->
                "UNLOCK_PATH".equals(r.get("kind")))).isTrue();

        Map<String, Object> markersAfter = api.mapMarkers(PLAYER, "1");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> afterList = (List<Map<String, Object>>) markersAfter.get("markers");
        assertThat(afterList.stream().anyMatch(m ->
                "chest-common-1".equals(m.get("markerId"))
                        && "COLLECTED".equals(m.get("status")))).isTrue();

        // ── 3) 探索度世界影响：NPC 对话覆盖 ──

        String npcLine = openWorld.gameplay().explorationImpacts()
                .npcDialogueOverride(PLAYER, "npc:rescued-merchant", "欢迎。");
        assertThat(npcLine).contains("商路");

        // ── 4) 通用探索套件 + 区域滑索 + 攀爬歇脚点 ──
        Map<String, Object> kit = api.grantUniversalKit(PLAYER);
        assertThat(kit.get("coreKitGranted")).isEqualTo(true);

        Map<String, Object> universal = api.universalTraverseStatus(PLAYER);
        assertThat(universal.get("coreKitComplete")).isEqualTo(true);

        Map<String, Object> zipline = api.regionalTraverseUse(body(
                "playerId", PLAYER,
                "facilityId", "zipline-valley-1",
                "x", 200f, "y", 30f, "z", 200f));
        assertThat(zipline.get("ok")).isEqualTo(true);
        assertThat(zipline.get("kind")).isEqualTo(RegionalTraverseService.FacilityKind.ZIPLINE.name());

        List<Map<String, Object>> facilities = (List<Map<String, Object>>)
                api.regionalTraverseList(REGION).get("facilities");
        assertThat(facilities).isNotEmpty();

        openWorld.gameplay().stamina().consume(PLAYER, MovementType.CLIMB, 5_000L, 1f, 1_000L);
        Map<String, Object> climbRest = api.moveAdmit(body(
                "playerId", PLAYER,
                "movementType", "CLIMB",
                "climbableMeshId", "cliff-valley-north",
                "x", 405f, "y", 25f, "z", 202f,
                "fromX", 404f, "fromY", 24f, "fromZ", 201f,
                "durationMs", 200L));
        assertThat(climbRest.get("onRestPoint")).isEqualTo(true);
        assertThat(((Number) climbRest.get("recovered")).floatValue()).isGreaterThan(0f);

        // ── 5) 生态叙事 + 环境叙事 + 探索正向反馈 ──
        openWorld.gameplay().ecoNarrative().setActiveQuest(PLAYER, "quest-fox-trail");
        openWorld.gameplay().ecosystem().tick("eco-fox-1", 12, 35_000L, false);
        Map<String, Object> ecoStory = api.ecoNarrativeEvaluate(PLAYER, "eco-fox-1");
        assertThat(ecoStory.get("hasNarrative")).isEqualTo(true);

        Map<String, Object> envStory = api.envStoryInspect(PLAYER, "prop-abandoned-camp");
        assertThat(envStory.get("ok")).isEqualTo(true);
        assertThat(envStory.get("silentNarrative").toString()).contains("脚印");

        Map<String, Object> feedback = api.explorationFeedback(body(
                "playerId", PLAYER,
                "regionId", REGION,
                "action", WorldExplorationFeedbackService.ActionKind.CLEAR_CAMP.name()));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fbUnlocked = (List<Map<String, Object>>) feedback.get("newlyUnlocked");
        assertThat(fbUnlocked).isNotEmpty();

        // ── 6) 战斗辅助：简化模式 ──
        Map<String, Object> assistMode = api.combatAssistMode(PLAYER, "SIMPLIFIED");
        assertThat(assistMode.get("autoCombo")).isEqualTo(true);
        assertThat(assistMode.get("damageWarning")).isEqualTo(true);

        Map<String, Object> combat = api.combatAssistResolve(body(
                "playerId", PLAYER,
                "attackerId", PLAYER,
                "targetId", 9001L,
                "incomingDamage", 280,
                "poiseDamage", 35,
                "heavyOrFall", false,
                "targetPoiseRemainRatio", 0.15f,
                "playerHpRatio", 0.2f));
        assertThat(combat.get("autoComboEnabled")).isEqualTo(true);
        assertThat(combat.get("damageWarning")).isNotNull();
        assertThat(combat.get("timeSlow")).isNotNull();
        assertThat(((Map<?, ?>) combat.get("hitFeedback")).get("enhancedVfx")).isEqualTo(true);

        // ── 7) 资源自动化 + 灵活日常 + 智能养成 ──
        assertThat(api.automationBuild(PLAYER, "auto-mine-1").get("ok")).isEqualTo(true);
        assertThat(api.automationBuild(PLAYER, "auto-farm-1").get("ok")).isEqualTo(true);
        Map<String, Object> autoStatus = api.automationStatus(PLAYER);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> built = (List<Map<String, Object>>) autoStatus.get("facilities");
        assertThat(built).hasSize(2);

        long now = System.currentTimeMillis();
        Map<String, Object> claim = openWorld.gameplay().resourceAutomation().claimAll(PLAYER, now + 3_600_100L);
        assertThat(claim.get("ok")).isEqualTo(true);
        assertThat((Long) claim.get("totalItems")).isGreaterThan(0L);

        api.dailyReport(body("playerId", PLAYER, "kind", "ANY_BATTLE", "amount", 3));
        api.dailyReport(body("playerId", PLAYER, "kind", "STAMINA_SPENT", "amount", 100));
        api.dailyReport(body("playerId", PLAYER, "kind", "EXPLORE_DISCOVER", "amount", 5));

        Map<String, Object> dailyList = api.dailyList(PLAYER);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> quests = (List<Map<String, Object>>) dailyList.get("quests");
        assertThat(quests.stream().filter(q -> Boolean.TRUE.equals(q.get("completed"))).count())
                .isGreaterThanOrEqualTo(3);

        Map<String, Object> dailyClaim = api.dailyClaimAll(PLAYER);
        assertThat(((List<?>) dailyClaim.get("claimed")).size()).isGreaterThanOrEqualTo(3);

        Map<String, Object> rec = api.buildRecommend(PLAYER, "char_ayaka");
        assertThat(rec.get("topPick")).isNotNull();
        @SuppressWarnings("unchecked")
        String presetId = (String) ((Map<String, Object>) rec.get("topPick")).get("presetId");
        Map<String, Object> applied = api.buildApply(PLAYER, presetId);
        assertThat(applied.get("oneClickApplied")).isEqualTo(true);
    }

    @Test
    public void apiNegativePaths_compassLocked_probeCooldown_invalidFacility() {
        Map<String, Object> probeLocked = api.compassProbe(body(
                "playerId", PLAYER + 1,
                "regionId", REGION,
                "x", 0f, "y", 0f, "z", 0f));
        assertThat(probeLocked.get("ok")).isEqualTo(false);
        assertThat(probeLocked.get("error")).isEqualTo("compass_locked");

        boostExploration(PLAYER + 2, 5, 6, 3, 2);
        api.compassProbe(body("playerId", PLAYER + 2, "regionId", REGION,
                "x", 100f, "y", 0f, "z", 100f));
        Map<String, Object> cooldown = api.compassProbe(body(
                "playerId", PLAYER + 2, "regionId", REGION,
                "x", 100f, "y", 0f, "z", 100f));
        assertThat(cooldown.get("ok")).isEqualTo(false);
        assertThat(cooldown.get("error")).isEqualTo("probe_cooldown");

        Map<String, Object> badZipline = api.regionalTraverseUse(body(
                "playerId", PLAYER,
                "facilityId", "missing-zipline",
                "x", 0f, "y", 0f, "z", 0f));
        assertThat(badZipline.get("ok")).isEqualTo(false);

        Map<String, Object> classic = api.combatAssistMode(PLAYER, "CLASSIC");
        assertThat(classic.get("mode")).isEqualTo(CombatAssistService.AssistMode.CLASSIC.name());
        assertThat(classic.get("autoCombo")).isEqualTo(false);

        Map<String, Object> badDaily = api.dailyReport(body(
                "playerId", PLAYER, "kind", "STAMINA_SPENT", "amount", 10));
        Map<String, Object> claimFail = openWorld.gameplay().flexibleDaily().claim(PLAYER, "daily-stamina");
        assertThat(claimFail.get("ok")).isEqualTo(false);
        assertThat(claimFail.get("error")).isEqualTo("not_complete");

        Map<String, Object> badBuild = api.buildApply(PLAYER, "missing-preset");
        assertThat(badBuild.get("ok")).isEqualTo(false);
    }

    @Test
    public void gameplayStatusIncludesP13ServicesAfterJourney() {
        boostExploration(PLAYER + 99, 5, 6, 3, 2);
        api.grantUniversalKit(PLAYER + 99);
        api.automationBuild(PLAYER + 99, "auto-mine-1");
        api.combatAssistMode(PLAYER + 99, "SIMPLIFIED");

        Map<String, Object> status = api.gameplayStatus(PLAYER + 99, REGION);
        assertThat(status.get("ok")).isEqualTo(true);
        assertThat(status.get("region_progress")).isNotNull();
        assertThat(status.get("collectibles")).isNotNull();
    }

    private void boostExploration(long playerId, int wp, int col, int pz, int q) {
        for (int i = 0; i < wp; i++) {
            openWorld.gameplay().regionProgress().markWaypoint(playerId, REGION, "p13-wp-" + playerId + "-" + i);
        }
        for (int i = 0; i < col; i++) {
            openWorld.gameplay().regionProgress().markCollectible(playerId, REGION, "p13-col-" + playerId + "-" + i);
        }
        for (int i = 0; i < pz; i++) {
            openWorld.gameplay().regionProgress().markPuzzle(playerId, REGION, "p13-pz-" + playerId + "-" + i);
        }
        for (int i = 0; i < q; i++) {
            openWorld.gameplay().regionProgress().markWorldQuest(playerId, REGION, "p13-q-" + playerId + "-" + i);
        }
    }

    private static Map<String, Object> body(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}
