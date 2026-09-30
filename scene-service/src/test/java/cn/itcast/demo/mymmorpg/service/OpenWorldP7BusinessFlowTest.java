package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.sync.MoveFlags;
import cn.itcast.demo.mymmorpg.web.InternalOpenWorldController;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P7 完整业务流程（经 Runtime + Internal API）：
 * 完美闪避/弹反 → 钩锁/载具 → 可破坏 → 协同解谜 → 编年史 →
 * 家园种植烹饪 → 空中冲刺/壁走 → 突破本 → 捕捉骑乘 → 战斗重播。
 */
public class OpenWorldP7BusinessFlowTest {

    private OpenWorldRuntimeService openWorld;
    private InternalOpenWorldController api;
    private static final long PLAYER = 77_001L;
    private static final long FRIEND = 77_002L;

    @BeforeMethod
    public void setUp() {
        openWorld = new OpenWorldRuntimeService();
        api = new InternalOpenWorldController(openWorld);
    }

    @Test
    public void fullP7Journey_feelTraverseMutabilityCoopChronicleHomelandAscensionReplay() {
        // ── 1) 瞬时博弈：开窗 → 完美闪避 → 子弹时间 → 结算评分 ──
        Map<String, Object> window = api.reactionOpenWindow(Map.of(
                "attackId", "atk-p7-1",
                "attackerEntityId", 9001L,
                "dodgeWindowMs", 200,
                "parryWindowMs", 180));
        assertThat(window.get("ok")).isEqualTo(true);
        assertThat(window.get("dodgeWindowMs")).isEqualTo(200);

        long now = System.currentTimeMillis();
        Map<String, Object> dodge = api.reactionValidate(Map.of(
                "kind", "PERFECT_DODGE",
                "battleId", "battle-p7",
                "playerId", PLAYER,
                "attackId", "atk-p7-1",
                "clientTs", now));
        assertThat(dodge.get("ok")).isEqualTo(true);
        assertThat(dodge.get("event")).isEqualTo("bulletTimeStart");
        assertThat(dodge.get("bulletTimeStart")).isInstanceOf(Map.class);
        assertThat(openWorld.gameplay().bulletTime()
                .shouldTickNpc("battle-p7", 9001L, now + 100)).isTrue();
        assertThat(openWorld.gameplay().bulletTime()
                .shouldTickNpc("battle-p7", 9002L, now + 100)).isFalse();

        Map<String, Object> score = openWorld.gameplay().reactions().settleScore("battle-p7");
        assertThat(score.get("perfectDodgeCount")).isEqualTo(1);
        assertThat(((Number) score.get("lootWeightBonus")).doubleValue()).isEqualTo(0.02d);

        // 弹反：重新开窗
        api.reactionOpenWindow(Map.of(
                "attackId", "atk-p7-2", "attackerEntityId", 9001L));
        Map<String, Object> parry = api.reactionValidate(Map.of(
                "kind", "PARRY",
                "battleId", "battle-p7",
                "playerId", PLAYER,
                "attackId", "atk-p7-2",
                "clientTs", System.currentTimeMillis()));
        assertThat(parry.get("ok")).isEqualTo(true);
        assertThat(parry.get("event")).isEqualTo("parrySuccess");

        // ── 2) 钩锁：解锁 → 阻挡拒绝 → 清除后 moveAdmit 成功 ──
        assertThat(api.unlockTraverse(PLAYER, "HOOK").get("ok")).isEqualTo(true);
        openWorld.gameplay().grappleNodes().markBlocked(475f, 57f, 487f);
        Map<String, Object> grappleBlocked = api.grappleAdmit(Map.of(
                "playerId", PLAYER,
                "nodeId", "grapple-ruin-1",
                "fromX", 470f, "fromY", 55f, "fromZ", 485f,
                "toX", 480f, "toY", 60f, "toZ", 490f));
        assertThat(grappleBlocked.get("ok")).isEqualTo(false);
        assertThat(grappleBlocked.get("error")).isEqualTo("line_of_sight_blocked");
        openWorld.gameplay().grappleNodes().clearBlocked(475f, 57f, 487f);

        Map<String, Object> grappleMove = api.moveAdmit(map(
                "playerId", PLAYER,
                "x", 480f, "y", 60f, "z", 490f,
                "fromX", 470f, "fromY", 55f, "fromZ", 485f,
                "movementType", "GRAPPLE",
                "grappleNodeId", "grapple-ruin-1",
                "durationMs", 300L,
                "speed", 28f));
        assertThat(grappleMove.get("ok")).isEqualTo(true);

        // ── 3) 载具：登船 → VehicleSyncCmd 纠偏 → 下船 ──
        assertThat(api.unlockTraverse(PLAYER, "VEHICLE").get("ok")).isEqualTo(true);
        Map<String, Object> board = api.vehicleBoard(Map.of(
                "playerId", PLAYER,
                "vehicleId", "boat-lake-1",
                "x", 80f, "y", 0f, "z", 40f));
        assertThat(board.get("ok")).isEqualTo(true);
        assertThat(board.get("cmd")).isEqualTo("VehicleSyncCmd");

        Map<String, Object> sync = api.vehicleSync(Map.of(
                "playerId", PLAYER,
                "x", 85f, "y", 0f, "z", 42f,
                "yawDeg", 30f, "speed", 10f, "accel", 4f));
        assertThat(sync.get("ok")).isEqualTo(true);
        assertThat(sync.get("fuel")).isInstanceOf(Number.class);

        // ── 4) 可破坏环境：重击破坏 → MutationBroadcast → 分帧重生 ──
        Map<String, Object> dmg = api.mutabilityDamage(Map.of(
                "id", "tree-valley-1",
                "damageType", "HEAVY_ATTACK",
                "damage", 100,
                "radiusM", 300f));
        assertThat(dmg.get("mutated")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> broadcast = (Map<String, Object>) dmg.get("mutationBroadcast");
        assertThat(broadcast.get("cmd")).isEqualTo("MutationBroadcast");
        assertThat(broadcast.get("radiusM")).isEqualTo(300f);

        long respawnAt = ((Number) dmg.get("respawnTs")).longValue();
        Map<String, Object> earlyScan = openWorld.gameplay().mutability()
                .scanRespawn(respawnAt - 1_000L, 100);
        assertThat(((Number) earlyScan.get("count")).intValue()).isEqualTo(0);
        Map<String, Object> lateScan = api.mutabilityRespawnScan(100);
        // API 用当前时间，若 CD 很长可能为 0；用服务层精确扫
        Map<String, Object> forceScan = openWorld.gameplay().mutability()
                .scanRespawn(respawnAt + 1, 100);
        assertThat(((Number) forceScan.get("count")).intValue()).isGreaterThanOrEqualTo(1);

        // ── 5) 多人协同压力板 ──
        Map<String, Object> press1 = api.coopPuzzlePress(Map.of(
                "puzzleId", "pressure-duo-1",
                "playerId", PLAYER,
                "zoneId", "zone-valley-a"));
        assertThat(press1.get("started")).isEqualTo(false);
        Map<String, Object> press2 = api.coopPuzzlePress(Map.of(
                "puzzleId", "pressure-duo-1",
                "playerId", FRIEND,
                "zoneId", "zone-valley-a"));
        assertThat(press2.get("started")).isEqualTo(true);
        assertThat(press2.get("event")).isEqualTo("CoopPuzzleStart");
        assertThat(press2.get("forceCutscene")).isEqualTo(true);

        Map<String, Object> ruleHit = api.ruleFire(Map.of(
                "eventType", "PRESS_PLATE",
                "party_members", 2));
        assertThat(((Number) ruleHit.get("matchedCount")).intValue()).isGreaterThanOrEqualTo(1);

        // ── 6) 编年史选择 → 全服 global_flag ──
        for (int i = 0; i < 8; i++) {
            assertThat(api.chronicleChoose(Map.of(
                    "playerId", PLAYER + 10 + i,
                    "nodeId", "choice-dragon-fate",
                    "choiceId", "slay_dragon")).get("ok")).isEqualTo(true);
        }
        api.chronicleChoose(Map.of(
                "playerId", PLAYER + 100, "nodeId", "choice-dragon-fate", "choiceId", "spare_dragon"));
        api.chronicleChoose(Map.of(
                "playerId", PLAYER + 101, "nodeId", "choice-dragon-fate", "choiceId", "spare_dragon"));
        Map<String, Object> flag = api.chronicleGlobalFlag("choice-dragon-fate", 0.8, 0.5);
        assertThat(flag.get("global_flag")).isEqualTo(true);
        assertThat(((Number) flag.get("bossSpawnRateMul")).doubleValue()).isEqualTo(1.5);

        // ── 7) 家园：种植 → 好友浇水 → 惰性收获 → 烹饪消耗作物 ──
        Map<String, Object> plant = api.homelandPlant(Map.of(
                "playerId", PLAYER,
                "plotId", "plot-p7-biz",
                "seedId", "wheat",
                "growMs", 500L));
        assertThat(plant.get("ok")).isEqualTo(true);
        String cropId = String.valueOf(plant.get("cropId"));
        openWorld.gameplay().homeland().water(cropId, FRIEND);
        Map<String, Object> notReady = openWorld.gameplay().homeland()
                .harvest(PLAYER, cropId, ((Number) plant.get("plantTimeMs")).longValue() + 100L);
        assertThat(notReady.get("ok")).isEqualTo(false);
        assertThat(notReady.get("error")).isEqualTo("not_ready");

        Map<String, Object> harvest = openWorld.gameplay().homeland().harvest(
                PLAYER, cropId, ((Number) plant.get("plantTimeMs")).longValue() + 2_000L);
        assertThat(harvest.get("ok")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cropPlans = (List<Map<String, Object>>) harvest.get("grantPlans");
        String cropItem = String.valueOf(cropPlans.get(0).get("itemId"));
        Map<String, Object> cook = api.homelandCook(Map.of(
                "playerId", PLAYER,
                "recipeId", "sweet_madame",
                "cropItemId", cropItem));
        assertThat(cook.get("ok")).isEqualTo(true);
        assertThat(cook.get("grantPlans")).isInstanceOf(List.class);

        // ── 8) 空中冲刺 / 壁走（moveFlags）──
        Map<String, Object> airDash = api.moveAdmit(Map.of(
                "playerId", PLAYER,
                "x", 10f, "y", 8f, "z", 10f,
                "movementType", "AIR_DASH",
                "moveFlags", MoveFlags.MID_AIR_DASH,
                "durationMs", 50L,
                "speed", 22f));
        assertThat(airDash.get("ok")).isEqualTo(true);
        assertThat(((Number) airDash.get("consumed")).floatValue()).isEqualTo(20f);

        Map<String, Object> wallRun = api.moveAdmit(map(
                "playerId", PLAYER,
                "x", 10f, "y", 5f, "z", 12f,
                "fromX", 10f, "fromY", 5f, "fromZ", 10f,
                "movementType", "WALL_RUN",
                "moveFlags", MoveFlags.WALL_RUN,
                "wallNormalX", 1f, "wallNormalY", 0f, "wallNormalZ", 0f,
                "durationMs", 1000L,
                "speed", 8f));
        assertThat(wallRun.get("ok")).isEqualTo(true);
        assertThat(wallRun.get("wallNormal")).isInstanceOf(Map.class);

        // ── 9) 突破本：进入独立实例 → 胜利升级世界等级 ──
        openWorld.gameplay().ascension().worldLevels().setWorldLevel(1, PLAYER, 1);
        Map<String, Object> enter = api.ascensionEnter(Map.of(
                "playerId", PLAYER,
                "questId", "asc-wl-1-to-2",
                "worldId", 1,
                "expOverflow", true,
                "prerequisiteDone", true));
        assertThat(enter.get("event")).isEqualTo("ENTER_ASCENSION_DUNGEON");
        assertThat(enter.get("sharedZone")).isEqualTo(false);
        assertThat(enter.get("ascension")).isEqualTo(true);
        String runId = String.valueOf(enter.get("runId"));

        Map<String, Object> settle = api.ascensionSettle(Map.of(
                "runId", runId,
                "victory", true,
                "worldId", 1));
        assertThat(settle.get("upgraded")).isEqualTo(true);
        assertThat(settle.get("worldLevel")).isEqualTo(2);
        assertThat(openWorld.gameplay().ascension().worldLevels().getWorldLevel(1, PLAYER))
                .isEqualTo(2);

        // ── 10) 捕捉 → 驯服命名 → 骑乘 AOI ──
        openWorld.gameplay().creatures().spawn(
                new cn.itcast.demo.mymmorpg.world.sideplay.CreatureCatchService.WildCreature(
                        "wild-p7-biz", "crystal_fox", 1, 100f, 0f, 100f, 20, 15, 100, 30));
        Map<String, Object> caught = openWorld.gameplay().creatures()
                .forceCatch(PLAYER, "wild-p7-biz", System.currentTimeMillis());
        assertThat(caught.get("caught")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> pet = (Map<String, Object>) caught.get("pet");
        String instanceId = String.valueOf(pet.get("instanceId"));
        Map<String, Object> tame = api.creatureTame(Map.of(
                "playerId", PLAYER,
                "instanceId", instanceId,
                "displayName", "霜风"));
        assertThat(tame.get("tamed")).isEqualTo(true);
        Map<String, Object> mount = api.creatureMount(PLAYER, instanceId);
        assertThat(mount.get("ok")).isEqualTo(true);
        assertThat(mount.get("aoiSync")).isEqualTo(true);

        // ── 11) 战斗指令流重播 ──
        Map<String, Object> replayStart = api.replayStart(Map.of(
                "battleId", "replay-p7-biz", "seed", 99L));
        String replayId = String.valueOf(replayStart.get("replayId"));
        api.replayAppend(Map.of(
                "replayId", replayId, "tick", 1, "actionType", "MOVE",
                "payload", Map.of("x", 1)));
        api.replayAppend(Map.of(
                "replayId", replayId, "tick", 2, "actionType", "PERFECT_DODGE",
                "payload", Map.of("damage", 0)));
        api.replayAppend(Map.of(
                "replayId", replayId, "tick", 3, "actionType", "SKILL",
                "payload", Map.of("damage", 150)));
        Map<String, Object> playback = api.replayPlayback(Map.of(
                "replayId", replayId,
                "speedMul", 4f,
                "expectedDamageMin", 100f,
                "expectedDamageMax", 200f));
        assertThat(playback.get("withinSeedExpectation")).isEqualTo(true);
        assertThat(((Number) playback.get("frameCount")).intValue()).isEqualTo(3);
        assertThat(((Number) playback.get("speedMul")).doubleValue()).isEqualTo(4.0);
    }

    @Test
    public void reactionOutsideWindowIsRejected() {
        api.reactionOpenWindow(Map.of(
                "attackId", "atk-late", "attackerEntityId", 1L, "dodgeWindowMs", 50));
        Map<String, Object> late = openWorld.gameplay().reactions().validate(
                cn.itcast.demo.mymmorpg.world.battle.ReactionValidator.ReactionKind.PERFECT_DODGE,
                "b-late", PLAYER, "atk-late", 10_000L, 10_000L);
        // openAt 为当前时间，clientTs=10000 会 outside；用服务层精确窗口
        long t0 = 5_000_000L;
        openWorld.gameplay().reactions().openAttackWindow("atk-late-2", 1L, t0, 50, 50);
        Map<String, Object> reject = openWorld.gameplay().reactions().validate(
                cn.itcast.demo.mymmorpg.world.battle.ReactionValidator.ReactionKind.PERFECT_DODGE,
                "b-late", PLAYER, "atk-late-2", t0 + 200L, t0 + 200L);
        assertThat(reject.get("ok")).isEqualTo(false);
        assertThat(reject.get("error")).isEqualTo("outside_window");
    }

    @Test
    public void ascensionRejectsWithoutExpOverflowOrPrerequisite() {
        openWorld.gameplay().ascension().worldLevels().setWorldLevel(1, PLAYER + 9, 1);
        Map<String, Object> noExp = api.ascensionEnter(Map.of(
                "playerId", PLAYER + 9,
                "questId", "asc-wl-1-to-2",
                "worldId", 1,
                "expOverflow", false,
                "prerequisiteDone", true));
        assertThat(noExp.get("ok")).isEqualTo(false);
        assertThat(noExp.get("error")).isEqualTo("exp_not_overflow");

        Map<String, Object> noPrereq = api.ascensionEnter(Map.of(
                "playerId", PLAYER + 9,
                "questId", "asc-wl-1-to-2",
                "worldId", 1,
                "expOverflow", true,
                "prerequisiteDone", false));
        assertThat(noPrereq.get("ok")).isEqualTo(false);
        assertThat(noPrereq.get("error")).isEqualTo("prerequisite_incomplete");
    }

    @Test
    public void wallRunOnFlatNormalIsRejected() {
        Map<String, Object> flat = api.moveAdmit(map(
                "playerId", PLAYER + 3,
                "x", 10f, "y", 5f, "z", 12f,
                "fromX", 10f, "fromY", 5f, "fromZ", 10f,
                "movementType", "WALL_RUN",
                "moveFlags", MoveFlags.WALL_RUN,
                "wallNormalX", 0f, "wallNormalY", 1f, "wallNormalZ", 0f,
                "durationMs", 200L,
                "speed", 8f));
        assertThat(flat.get("ok")).isEqualTo(false);
        assertThat(flat.get("error")).isEqualTo("wall_angle_invalid");
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}
