package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.web.InternalOpenWorldController;
import cn.itcast.demo.mymmorpg.world.battle.BulletTimeService;
import cn.itcast.demo.mymmorpg.world.coop.CoopRoomElectionService;
import cn.itcast.demo.mymmorpg.world.ownership.AccessLevel;
import cn.itcast.demo.mymmorpg.world.puzzle.CoopPuzzleService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P17 完整业务流程（经 Runtime + Internal API）：
 * 世界主权绑定 → Host-Only 采集拒绝 → 局部子弹时间 → 地形 TSV 失配软拉回 →
 * 主机迁移选举 → 解谜 Checkpoint → 叙事旁观封锁移动 → 助战代币兑特产 → 掉落分桶。
 */
public class OpenWorldP17BusinessFlowTest {

    private OpenWorldRuntimeService openWorld;
    private InternalOpenWorldController api;

    private static final long HOST = 417_001L;
    private static final long GUEST_A = 417_002L;
    private static final long GUEST_B = 417_003L;
    private static final String ROOM = "coop-p17-biz";
    private static final String LAKE = "lake-p17";

    @BeforeMethod
    public void setUp() {
        openWorld = new OpenWorldRuntimeService();
        api = new InternalOpenWorldController(openWorld);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void fullP17ApiJourney_ownershipBulletTimeTerrainHostMigrationPuzzleNarrativeLoot() {
        // ── 1) 绑定联机主权 + 叙事房间 ──
        Map<String, Object> ownership = api.coopOwnershipBind(body(
                "roomId", ROOM,
                "hostPlayerId", HOST,
                "members", List.of(GUEST_A, GUEST_B)));
        assertThat(ownership.get("ok")).isEqualTo(true);
        assertThat(ownership.get("hostPlayerId")).isEqualTo(HOST);
        assertThat(((Number) ownership.get("memberCount")).intValue()).isEqualTo(3);

        Map<String, Object> narrativeRoom = api.coopNarrativeRoom(body(
                "roomId", ROOM,
                "hostPlayerId", HOST,
                "guests", List.of(GUEST_A, GUEST_B)));
        assertThat(narrativeRoom.get("ok")).isEqualTo(true);

        // ── 2) Host-Only：访客不可偷神瞳，房主可采 ──
        Map<String, Object> guestSteal = api.collectibleCollect(body(
                "playerId", GUEST_A,
                "collectibleId", "oculus-anemo-1",
                "x", 180f, "y", 20f, "z", 160f,
                "coopRoomId", ROOM));
        assertThat(guestSteal.get("ok")).isEqualTo(false);
        assertThat(guestSteal.get("retcode")).isEqualTo(RetCode.HOST_ONLY_DENIED);
        assertThat(guestSteal.get("accessLevel")).isEqualTo(AccessLevel.HOST_ONLY.name());

        Map<String, Object> hostCollect = api.collectibleCollect(body(
                "playerId", HOST,
                "collectibleId", "oculus-anemo-1",
                "x", 180f, "y", 20f, "z", 160f,
                "coopRoomId", ROOM));
        assertThat(hostCollect.get("ok")).isEqualTo(true);

        // ── 3) 局部子弹时间：队友仅视觉慢动作 ──
        Map<String, Object> bt = api.bulletTimeLocal(body(
                "battleId", "p17-bt",
                "playerId", HOST,
                "attackerEntityId", 101L,
                "lockedBossEntityId", 9001L,
                "observerPlayerId", GUEST_A,
                "ignoredEntityIds", List.of(202L, 203L),
                "durationMs", 1500L,
                "timeScale", 0.1d));
        assertThat(bt.get("localDilation")).isEqualTo(true);
        assertThat(bt.get("pauseNpcExceptAttacker")).isEqualTo(false);
        assertThat(bt.get("standardTickMs")).isEqualTo(BulletTimeService.STANDARD_TICK_MS);
        Map<String, Object> obs = (Map<String, Object>) bt.get("observerNotify");
        assertThat(obs.get("showVisualSlomoOnly")).isEqualTo(true);
        assertThat(obs.get("msgId")).isEqualTo(MessageId.BATTLE_HIT_FEEDBACK_SC_NOTIFY);

        // ── 4) 地形感电 → TSV 全量同步 → revision 落后软拉回 ──
        openWorld.gameplay().terrainMutation().markWater(LAKE, 4, 5);
        Map<String, Object> overload = api.terrainOverload(body(
                "regionId", LAKE, "gx", 4, "gz", 5));
        assertThat(overload.get("ok")).isEqualTo(true);
        assertThat(overload.get("tsvSynced")).isEqualTo(true);
        long rev = ((Number) overload.get("stateRevision")).longValue();

        Map<String, Object> tsv = api.terrainTsv(LAKE);
        assertThat(tsv.get("ok")).isEqualTo(true);
        assertThat(((List<?>) tsv.get("mutations")).size()).isGreaterThanOrEqualTo(1);

        Map<String, Object> mismatch = api.moveAdmitTsv(body(
                "playerId", GUEST_A,
                "x", 1f, "y", 0f, "z", 1f,
                "fromX", 0f, "fromY", 0f, "fromZ", 0f,
                "regionId", LAKE,
                "terrainCellX", 4, "terrainCellY", 5,
                "clientTerrainRevision", 0L));
        assertThat(mismatch.get("ok")).isEqualTo(false);
        assertThat(mismatch.get("retcode")).isEqualTo(RetCode.TERRAIN_STATE_MISMATCH);
        assertThat(mismatch.get("softPullback")).isEqualTo(true);

        Map<String, Object> matched = api.moveAdmitTsv(body(
                "playerId", GUEST_A,
                "x", 1f, "y", 0f, "z", 1f,
                "fromX", 0f, "fromY", 0f, "fromZ", 0f,
                "regionId", LAKE,
                "terrainCellX", 4, "terrainCellY", 5,
                "clientTerrainRevision", rev));
        assertThat(matched.get("retcode")).isNotEqualTo(RetCode.TERRAIN_STATE_MISMATCH);

        // ── 5) 房主断线 → 超阈值选举新 Host（Ping 最低）→ 加载 roomSnapshot ──
        Map<String, Object> disc = api.coopHostDisconnect(body(
                "roomId", ROOM,
                "hostPlayerId", HOST,
                "bossHpRemain", 42_000L,
                "bossHpMax", 100_000L,
                "gadgetBitmap", "1101",
                "tideState", "HIGH",
                "candidates", List.of(
                        Map.of("playerId", GUEST_A, "pingMs", 90L, "sceneActorLoad", 2),
                        Map.of("playerId", GUEST_B, "pingMs", 25L, "sceneActorLoad", 4))));
        assertThat(disc.get("lockAcquired")).isEqualTo(true);
        assertThat(disc.get("pendingElection")).isEqualTo(true);
        long discAt = ((Number) disc.get("disconnectAtMs")).longValue();

        Map<String, Object> waiting = api.coopHostElect(body(
                "roomId", ROOM,
                "nowMs", discAt + 5_000L));
        assertThat(waiting.get("elected")).isEqualTo(false);

        Map<String, Object> elected = api.coopHostElect(body(
                "roomId", ROOM,
                "nowMs", discAt + CoopRoomElectionService.HOST_DISCONNECT_THRESHOLD_MS + 1));
        assertThat(elected.get("elected")).isEqualTo(true);
        assertThat(elected.get("newHostPlayerId")).isEqualTo(GUEST_B);
        assertThat(elected.get("msgId")).isEqualTo(MessageId.HOST_TRANSFER_SC_NOTIFY);
        assertThat(elected.get("reloadScene")).isEqualTo(false);
        Map<String, Object> snap = (Map<String, Object>) elected.get("roomSnapshot");
        assertThat(snap.get("bossHpRemain")).isEqualTo(42_000L);
        assertThat(snap.get("gadgetBitmap")).isEqualTo("1101");

        // ── 6) 解谜：踩板 → Checkpoint 保留（离开超时不清空进度）──
        Map<String, Object> press = api.coopPuzzlePress(body(
                "puzzleId", "pressure-duo-1",
                "playerId", GUEST_A,
                "zoneId", "zone-valley-a"));
        assertThat(press.get("ok")).isEqualTo(true);
        assertThat(press.get("checkpoint")).isNotNull();

        Map<String, Object> left = openWorld.gameplay().coopPuzzles().tickLeave(
                "pressure-duo-1", System.currentTimeMillis() + CoopPuzzleService.LEAVE_TIMEOUT_MS + 1);
        assertThat(left.get("checkpointPreserved")).isEqualTo(true);
        Map<String, Object> resume = openWorld.gameplay().coopPuzzles().resumeCheckpoint("pressure-duo-1");
        assertThat(resume.get("resumeScene")).isEqualTo(true);
        assertThat(resume.get("reset")).isEqualTo(false);

        // ── 7) 叙事旁观：房主剧情 → 访客移动封锁 + 选项置灰 + 助战代币 ──
        api.coopNarrativeRoom(body(
                "roomId", "room-cut-p17",
                "hostPlayerId", HOST,
                "guests", List.of(GUEST_A)));
        Map<String, Object> storyHost = api.storyInstanceStart(body(
                "playerId", HOST,
                "storyId", "legend-ayaka-1",
                "coopRoomId", "room-cut-p17"));
        assertThat(storyHost.get("ok")).isEqualTo(true);

        Map<String, Object> storyGuest = openWorld.gameplay().coopNarrative().onStoryInstanceStart(
                "room-cut-p17", GUEST_A, "legend-ayaka-1", System.currentTimeMillis());
        assertThat(storyGuest.get("moveBlocked")).isEqualTo(true);
        assertThat(storyGuest.get("effect")).isEqualTo("WORLD_SHIFT_SHIELD");

        Map<String, Object> moveBlocked = api.moveAdmitTsv(body(
                "playerId", GUEST_A,
                "x", 2f, "y", 0f, "z", 2f,
                "fromX", 0f, "fromY", 0f, "fromZ", 0f,
                "regionId", "",
                "terrainCellX", 0, "terrainCellY", 0,
                "clientTerrainRevision", 0L));
        assertThat(moveBlocked.get("retcode")).isEqualTo(RetCode.CUTSCENE_MOVE_BLOCKED);

        Map<String, Object> assist = api.coopNarrativeAssist(body(
                "roomId", "room-cut-p17",
                "guestPlayerId", GUEST_A,
                "storyBossId", "boss-p17"));
        assertThat(assist.get("badge")).isEqualTo("助人之证");
        assertThat(assist.get("socialToken")).isNotNull();

        Map<String, Object> redeem = api.redeemHostSpecialty(body(
                "guestPlayerId", GUEST_A,
                "hostPlayerId", HOST,
                "specialtyItemId", "valley_herb"));
        assertThat(redeem.get("ok")).isEqualTo(true);
        assertThat(((Map<?, ?>) redeem.get("grantPlan")).get("source")).isEqualTo("HOST_WORLD_SPECIALTY");

        // ── 8) Instance Loot 分桶：房主/访客互不稀释 ──
        Map<String, Object> opened = api.fightContributionOpen(body(
                "bossName", "p17-boss",
                "maxHp", 500_000L,
                "hostPlayerId", HOST));
        long battleId = ((Number) opened.get("battleId")).longValue();
        api.fightInstanceLoot(body("battleId", battleId, "playerId", HOST, "itemId", "host_drop", "count", 2));
        api.fightInstanceLoot(body("battleId", battleId, "playerId", GUEST_A, "itemId", "guest_drop", "count", 5));
        api.fightInstanceLoot(body("battleId", battleId, "playerId", GUEST_B, "itemId", "guest_drop", "count", 5));
        Map<String, Object> lootSettle = api.fightInstanceLootSettle(body("battleId", battleId));
        assertThat(lootSettle.get("hostDiluted")).isEqualTo(false);
        assertThat(lootSettle.get("guestDiluted")).isEqualTo(false);
        assertThat((List<?>) lootSettle.get("hostLoot")).hasSize(1);
        assertThat((List<?>) lootSettle.get("guestBuckets")).hasSize(2);

        // ── 门面 p17 快照 ──
        Map<String, Object> status = api.gameplayStatus(null, null);
        Map<String, Object> p17 = (Map<String, Object>) status.get("p17");
        assertThat(p17.get("worldOwnership")).isEqualTo(true);
        assertThat(p17.get("localBulletTime")).isEqualTo(true);
        assertThat(p17.get("hostMigration")).isEqualTo(true);
        assertThat(p17.get("terrainStateVector")).isEqualTo(true);
        assertThat(p17.get("syncCutscene")).isEqualTo(true);
        assertThat(p17.get("instanceLootBuckets")).isEqualTo(true);
    }

    private static Map<String, Object> body(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}
