package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.sync.MovementType;
import cn.itcast.demo.mymmorpg.web.InternalOpenWorldController;
import cn.itcast.demo.mymmorpg.world.battle.InputBufferService;
import cn.itcast.demo.mymmorpg.world.battle.PoiseService;
import cn.itcast.demo.mymmorpg.world.content.RareEliteSpawnService;
import cn.itcast.demo.mymmorpg.world.sideplay.HomelandGuardService;
import cn.itcast.demo.mymmorpg.world.traverse.FallAttackValidator;
import cn.itcast.demo.mymmorpg.world.traverse.UnderwaterPhysicsService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P9 完整业务流程（经 Runtime + Internal API）：
 * 命座机制 → 下落重击 → 水下突进 → 稀有精英 → 跟随采集 →
 * 家园防盗 → 幻影引导 → 韧性/预输入 → 区域觉醒 → 世界之核。
 */
public class OpenWorldP9BusinessFlowTest {

    private OpenWorldRuntimeService openWorld;
    private InternalOpenWorldController api;
    private static final long PLAYER = 90_001L;
    private static final long FRIEND = 90_002L;

    @BeforeMethod
    public void setUp() {
        openWorld = new OpenWorldRuntimeService();
        api = new InternalOpenWorldController(openWorld);
    }

    @Test
    public void fullP9Journey_monetizationVerticalEcoSocialFeelMastery() {
        // ── 1) 命座解锁 → 技能机制覆盖（非纯数值）──
        openWorld.gameplay().constellation().creditMaterial(PLAYER, 5);
        Map<String, Object> unlock = api.constellationUnlock(Map.of(
                "playerId", PLAYER,
                "characterId", "char-warden",
                "matCost", 1));
        assertThat(unlock.get("ok")).isEqualTo(true);
        assertThat(unlock.get("constellationLv")).isEqualTo(1);
        assertThat(unlock.get("attributeRecalc")).isInstanceOf(Map.class);

        Map<String, Object> cast = api.skillCastResolve(Map.of(
                "playerId", PLAYER,
                "characterId", "char-warden",
                "skillId", "skill-evil-warding"));
        assertThat(cast.get("ok")).isEqualTo(true);
        assertThat(cast.get("source")).isEqualTo("constellation_buff_override");
        assertThat(cast.get("projectileCount")).isEqualTo(3);
        assertThat(cast.get("hitCount")).isEqualTo(2);

        // 专武精炼特效与命座解耦
        openWorld.gameplay().battleTriggers().refineWeapon("wpn-p9", 3, "COORDINATED_ATK", 0.25);
        Map<String, Object> trigger = openWorld.gameplay().battleTriggers()
                .onHit("battle-p9", "wpn-p9", PLAYER, 9001L, 200, System.currentTimeMillis());
        assertThat(trigger.get("triggered")).isEqualTo(true);

        // ── 2) 下落重击：条件校验 → 命中冲击波 → 未命中硬直 ──
        Map<String, Object> fallMiss = api.fallAttackValidate(body(
                "playerId", PLAYER,
                "fallDistance", 3f,
                "velocityY", -5f,
                "hitEnemy", false,
                "baseDamage", 100,
                "worldId", 1,
                "x", 10f, "y", 20f, "z", 10f));
        assertThat(fallMiss.get("ok")).isEqualTo(false);

        Map<String, Object> fallHit = api.fallAttackValidate(body(
                "playerId", PLAYER,
                "fallDistance", 15f,
                "velocityY", -6f,
                "hitEnemy", true,
                "baseDamage", 100,
                "worldId", 1,
                "x", 10f, "y", 0f, "z", 10f));
        assertThat(fallHit.get("ok")).isEqualTo(true);
        assertThat(fallHit.get("action")).isEqualTo(FallAttackValidator.ACTION_FALL_HEAVY);
        assertThat(((Number) fallHit.get("damage")).intValue()).isGreaterThan(100);
        assertThat(fallHit.get("shockwave")).isInstanceOf(Map.class);

        Map<String, Object> fallLag = api.fallAttackValidate(body(
                "playerId", FRIEND,
                "fallDistance", 10f,
                "velocityY", -4f,
                "hitEnemy", false,
                "baseDamage", 80,
                "worldId", 1,
                "x", 5f, "y", 0f, "z", 5f));
        assertThat(fallLag.get("forceAnim")).isEqualTo("LANDING_LAG");
        assertThat(((Number) fallLag.get("landingLagMs")).intValue()).isGreaterThan(0);

        // ── 3) 水下：进入 → 突进缩距 → 爆炸改导电 DoT ──
        Map<String, Object> enterUw = api.underwaterEnter(PLAYER, 1);
        assertThat(enterUw.get("ok")).isEqualTo(true);
        assertThat(enterUw.get("biome")).isEqualTo(UnderwaterPhysicsService.BIOME_UNDERWATER);

        Map<String, Object> dash = api.moveAdmit(Map.of(
                "playerId", PLAYER,
                "movementType", MovementType.DASH.name(),
                "x", 20f, "y", 0f, "z", 20f,
                "speed", 12f,
                "durationMs", 1000L));
        assertThat(dash.get("underwater")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> dashDist = (Map<String, Object>) dash.get("dashDistance");
        assertThat(((Number) dashDist.get("reductionPct")).intValue()).isEqualTo(60);

        Map<String, Object> rx = openWorld.gameplay().underwater()
                .filterElementReaction(PLAYER, "OVERLOAD", "ELECTRO");
        assertThat(rx.get("rewritten")).isEqualTo(true);
        assertThat(rx.get("reaction")).isEqualTo("ELECTRO_CONDUCT_DOT");
        assertThat(api.underwaterLeave(PLAYER).get("ok")).isEqualTo(true);

        // ── 4) 稀有精英：格子击杀阈值 → GRID_INFESTATION + AOI 广播 ──
        long now = System.currentTimeMillis();
        Map<String, Object> eliteLast = Map.of();
        for (int i = 0; i < RareEliteSpawnService.KILL_THRESHOLD; i++) {
            eliteLast = api.rareEliteKill("7_8", now + i);
        }
        assertThat(eliteLast.get("spawned")).isEqualTo(true);
        assertThat(eliteLast.get("infestation")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> aoi = (Map<String, Object>) eliteLast.get("aoiBroadcast");
        assertThat(aoi.get("event")).isEqualTo(RareEliteSpawnService.EVENT_RARE_ELITE_APPEAR);
        @SuppressWarnings("unchecked")
        Map<String, Object> elite = (Map<String, Object>) eliteLast.get("elite");
        Map<String, Object> settle = openWorld.gameplay().rareElites()
                .settleKill(String.valueOf(elite.get("eliteId")), PLAYER);
        assertThat(settle.get("ok")).isEqualTo(true);
        assertThat(settle.get("grantPlans")).isInstanceOf(java.util.List.class);

        // ── 5) 驯服跟随采集：捕捉 → 跟随模式 → 采集加成直入背包 ──
        Map<String, Object> caught = openWorld.gameplay().creatures()
                .forceCatch(PLAYER, "wild-fox-1", now);
        assertThat(caught.get("caught")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> pet = (Map<String, Object>) caught.get("pet");
        Map<String, Object> follow = api.creatureFollowHarvest(PLAYER, String.valueOf(pet.get("instanceId")));
        assertThat(follow.get("ok")).isEqualTo(true);
        assertThat(follow.get("mode")).isEqualTo("FOLLOW_HARVEST");

        Map<String, Object> collect = openWorld.gameplay().collectWithDynamicLoot(
                PLAYER, "chest-common-1", 100f, 0f, 100f, 4, 0.6f);
        assertThat(collect.get("bonusApplied")).isEqualTo(true);
        assertThat(collect.get("bonusGrantPlans")).isNotNull();

        // ── 6) 家园：守卫拦截偷菜 + 无守卫时 20% 上限 ──
        openWorld.gameplay().homelandGuard().creditCoins(PLAYER, 50);
        openWorld.gameplay().homelandGuard().markReady("crop-api-1", 100);
        Map<String, Object> guard = api.homelandGuard(Map.of(
                "ownerId", PLAYER, "plotId", "plot-api-1", "coinCost", 10));
        assertThat(guard.get("ok")).isEqualTo(true);

        Map<String, Object> stealBlocked = api.homelandSteal(Map.of(
                "friendId", FRIEND, "plotId", "plot-api-1", "cropId", "crop-api-1"));
        assertThat(stealBlocked.get("ok")).isEqualTo(false);
        assertThat(stealBlocked.get("debuff")).isEqualTo(HomelandGuardService.DEBUFF_SPEED_DOWN);

        openWorld.gameplay().homelandGuard().markReady("crop-api-2", 50);
        Map<String, Object> stealOk = api.homelandSteal(Map.of(
                "friendId", FRIEND, "plotId", "plot-open-2", "cropId", "crop-api-2"));
        assertThat(stealOk.get("ok")).isEqualTo(true);
        assertThat(stealOk.get("amount")).isEqualTo(10); // 20% of 50

        // ── 7) 幻影求助：失败 3 次 → PHANTOM_GUIDE_MARK ──
        Map<String, Object> leave = api.phantomLeave(Map.of(
                "playerId", FRIEND,
                "ownerName", "通关者",
                "sceneId", 1,
                "x", 12f, "y", 0f, "z", 12f,
                "pose", "SOLVE",
                "emoteFrames", java.util.List.of("W", "I")));
        assertThat(leave.get("ok")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> phantom = (Map<String, Object>) leave.get("phantom");
        openWorld.gameplay().encounters()
                .recordPuzzleClearPhantom("puzzle-p9", String.valueOf(phantom.get("phantomId")));
        api.phantomPuzzleFail(Map.of("playerId", PLAYER, "puzzleId", "puzzle-p9"));
        api.phantomPuzzleFail(Map.of("playerId", PLAYER, "puzzleId", "puzzle-p9"));
        Map<String, Object> guide = api.phantomPuzzleFail(Map.of(
                "playerId", PLAYER, "puzzleId", "puzzle-p9"));
        assertThat(guide.get("recommend")).isEqualTo(true);
        assertThat(((Map<?, ?>) guide.get("guideMark")).get("skipPuzzle")).isEqualTo(false);

        // ── 8) 韧性破韧处决 + 开大霸体；预输入保留后清空 ──
        Map<String, Object> poiseBreak = api.poiseHit(Map.of(
                "attackerId", PLAYER,
                "targetId", 9100L,
                "poiseMax", 40f,
                "attackPoiseDamage", 50d));
        assertThat(poiseBreak.get("event")).isEqualTo(PoiseService.EVENT_HIT_CONFIRM);
        assertThat(poiseBreak.get("execution")).isEqualTo(PoiseService.EVENT_EXECUTION_TRIGGER);
        assertThat(poiseBreak.get("stagger_level")).isEqualTo(2);

        openWorld.gameplay().poise().initEntity(PLAYER, 100, 5);
        openWorld.gameplay().poise().enterUltimate(PLAYER, System.currentTimeMillis());
        Map<String, Object> armor = api.poiseHit(Map.of(
                "attackerId", 9100L,
                "targetId", PLAYER,
                "attackPoiseDamage", 999d));
        assertThat(armor.get("superArmor")).isEqualTo(true);
        assertThat(armor.get("interrupted")).isEqualTo(false);

        Map<String, Object> arm = api.inputBufferArm(Map.of(
                "playerId", PLAYER, "attackId", "atk-buf-p9", "dodgeWindowMs", 200));
        assertThat(arm.get("ok")).isEqualTo(true);
        long t0 = ((Number) arm.get("bufferDeadlineMs")).longValue() - 50;
        // enqueue via service with controlled timestamp near deadline window
        Map<String, Object> queued = openWorld.gameplay().inputBuffer()
                .enqueue(PLAYER, "HEAVY", t0);
        assertThat(queued.get("ok")).isEqualTo(true);
        Map<String, Object> expired = openWorld.gameplay().inputBuffer()
                .enqueue(PLAYER, "SKILL", ((Number) arm.get("bufferDeadlineMs")).longValue() + 1);
        assertThat(expired.get("event")).isEqualTo(InputBufferService.EVENT_INPUT_CLEAR);

        // ── 9) 区域 100% → REGION_MASTERY + WorldBuff ──
        String region = "wolf-camp-valley";
        for (int i = 0; i < 5; i++) {
            openWorld.gameplay().regionProgress().markWaypoint(PLAYER, region, "wp-p9-" + i);
        }
        for (int i = 0; i < 8; i++) {
            openWorld.gameplay().regionProgress().markCollectible(PLAYER, region, "col-p9-" + i);
        }
        for (int i = 0; i < 4; i++) {
            openWorld.gameplay().regionProgress().markPuzzle(PLAYER, region, "pz-p9-" + i);
        }
        for (int i = 0; i < 3; i++) {
            openWorld.gameplay().regionProgress().markWorldQuest(PLAYER, region, "q-p9-" + i);
        }
        Map<String, Object> awaken = api.regionAwaken(Map.of(
                "playerId", PLAYER, "playerName", "探索王", "regionId", region));
        assertThat(awaken.get("awakened")).isEqualTo(true);
        assertThat(awaken.get("event")).isEqualTo("REGION_MASTERY");
        assertThat(((Map<?, ?>) awaken.get("broadcast")).get("event")).isEqualTo("EPIC_MVP");
        assertThat(openWorld.gameplay().regionAwakening()
                .totalAttrBonus(PLAYER, System.currentTimeMillis())).isEqualTo(0.05);

        // ── 10) 奇观全解锁 → 世界之核 4 人房 ──
        assertThat(api.worldCoreStatus(PLAYER).get("unlocked")).isEqualTo(false);
        openWorld.gameplay().worldCore().markWonderComplete(PLAYER, "floating-ruin");
        openWorld.gameplay().worldCore().markWonderComplete(PLAYER, "sealed-sanctum");
        Map<String, Object> status = api.worldCoreStatus(PLAYER);
        assertThat(status.get("unlocked")).isEqualTo(true);
        Map<String, Object> room = api.worldCoreCreate(PLAYER);
        assertThat(room.get("ok")).isEqualTo(true);
        assertThat(room.get("dungeonId")).isEqualTo("WORLD_CORE");
        assertThat(room.get("maxMembers")).isEqualTo(4);
        Map<String, Object> join = openWorld.gameplay().worldCore().join(FRIEND, String.valueOf(room.get("roomId")));
        assertThat(join.get("ok")).isEqualTo(true);
    }

    private static Map<String, Object> body(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}
