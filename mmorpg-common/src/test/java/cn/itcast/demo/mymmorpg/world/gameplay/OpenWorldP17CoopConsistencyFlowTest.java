package cn.itcast.demo.mymmorpg.world.gameplay;

import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.sync.SceneMoveCmd;
import cn.itcast.demo.mymmorpg.world.battle.BulletTimeService;
import cn.itcast.demo.mymmorpg.world.coop.CoopRoomElectionService;
import cn.itcast.demo.mymmorpg.world.ownership.AccessLevel;
import cn.itcast.demo.mymmorpg.world.puzzle.CoopPuzzleService;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P17：世界主权 / 局部子弹时间 / 主机迁移 / 地形 TSV / 解谜宽松硬性态 / 叙事旁观同步。
 */
public class OpenWorldP17CoopConsistencyFlowTest {

    @Test
    public void hostOnlyCollectibleDeniesGuest() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.worldOwnership().bindRoom("room-p17", 1L, 2L);
        Map<String, Object> denied = g.collectibles().collect(
                2L, "oculus-anemo-1", 180f, 20f, 160f, null, "room-p17");
        assertThat(denied.get("ok")).isEqualTo(false);
        assertThat(denied.get("retcode")).isEqualTo(RetCode.HOST_ONLY_DENIED);
        assertThat(denied.get("accessLevel")).isEqualTo(AccessLevel.HOST_ONLY.name());

        Map<String, Object> hostOk = g.collectibles().collect(
                1L, "oculus-anemo-1", 180f, 20f, 160f, null, "room-p17");
        assertThat(hostOk.get("ok")).isEqualTo(true);
    }

    @Test
    public void localBulletTimeDoesNotPauseOtherPlayersLogic() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long now = 10_000L;
        Map<String, Object> start = g.bulletTime().startLocal(
                "bt-1", 1L, 101L, 9001L, now, 1500L, 0.1d, Set.of(202L, 203L));
        assertThat(start.get("localDilation")).isEqualTo(true);
        assertThat(start.get("pauseNpcExceptAttacker")).isEqualTo(false);
        assertThat(start.get("standardTickMs")).isEqualTo(BulletTimeService.STANDARD_TICK_MS);

        Map<String, Object> self = g.bulletTime().notifyObserver("bt-1", 1L, now + 100);
        assertThat(self.get("showVisualSlomoOnly")).isEqualTo(false);
        assertThat(self.get("affectDamageWindow")).isEqualTo(true);

        Map<String, Object> teammate = g.bulletTime().notifyObserver("bt-1", 2L, now + 100);
        assertThat(teammate.get("showVisualSlomoOnly")).isEqualTo(true);
        assertThat(teammate.get("trailVfx")).isEqualTo(true);
        assertThat(teammate.get("serverLogicTickMs")).isEqualTo(33L);
        assertThat(g.bulletTime().timeScaleOf("bt-1", 202L, now + 100)).isEqualTo(1.0);
        assertThat(g.bulletTime().shouldApplyLocalScale("bt-1", 9001L, now + 100)).isTrue();
    }

    @Test
    public void hostMigrationElectsLowestPingAndLoadsSnapshot() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        String roomId = "coop-mig-1";
        g.worldOwnership().bindRoom(roomId, 1L, 2L, 3L);
        g.coopElection().registerHost(roomId, 1L);
        long now = System.currentTimeMillis();
        g.serverShadow().saveRoomSnapshot(roomId, 1L, 55_000L, 100_000L, "1011", "HIGH", now);
        g.coopElection().updateCandidates(roomId, List.of(
                new CoopRoomElectionService.Candidate(2L, 80L, 3),
                new CoopRoomElectionService.Candidate(3L, 30L, 5)));
        g.coopElection().onHostDisconnect(roomId, 1L, now);
        Map<String, Object> wait = g.coopElection().tryElect(roomId, now + 10_000L);
        assertThat(wait.get("elected")).isEqualTo(false);

        Map<String, Object> elected = g.coopElection().tryElect(
                roomId, now + CoopRoomElectionService.HOST_DISCONNECT_THRESHOLD_MS + 1);
        assertThat(elected.get("elected")).isEqualTo(true);
        assertThat(elected.get("newHostPlayerId")).isEqualTo(3L);
        assertThat(elected.get("msgId")).isEqualTo(MessageId.HOST_TRANSFER_SC_NOTIFY);
        assertThat(elected.get("reloadScene")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        Map<String, Object> snap = (Map<String, Object>) elected.get("roomSnapshot");
        assertThat(snap.get("bossHpRemain")).isEqualTo(55_000L);
        assertThat(g.worldOwnership().hostOf(roomId)).isEqualTo(3L);
    }

    @Test
    public void terrainStateVectorMismatchSoftPullsClient() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.terrainMutation().markWater("lake-a", 2, 3);
        Map<String, Object> overload = g.terrainMutation().applyOverloadOnWater("lake-a", 2, 3, 5_000L);
        assertThat(overload.get("tsvSynced")).isEqualTo(true);
        long rev = ((Number) overload.get("stateRevision")).longValue();
        assertThat(rev).isGreaterThan(0L);

        Map<String, Object> zone = g.terrainStateVector().snapshotZone("lake-a");
        assertThat(zone.get("ok")).isEqualTo(true);

        SceneMoveCmd cmd = SceneMoveCmd.walk(1f, 0f, 1f, 5f, 5_100L);
        Map<String, Object> mismatch = g.movementAdmission().admitWithTerrainRevision(
                9L, cmd, 100L, 5_100L, 0f, 0f, 0f, "lake-a", 2, 3, 60_000L, 0L);
        assertThat(mismatch.get("ok")).isEqualTo(false);
        assertThat(mismatch.get("retcode")).isEqualTo(RetCode.TERRAIN_STATE_MISMATCH);
        assertThat(mismatch.get("softPullback")).isEqualTo(true);

        Map<String, Object> ok = g.movementAdmission().admitWithTerrainRevision(
                9L, cmd, 100L, 5_100L, 0f, 0f, 0f, "lake-a", 2, 3, 60_000L, rev);
        assertThat(ok.get("retcode")).isNotEqualTo(RetCode.TERRAIN_STATE_MISMATCH);
    }

    @Test
    public void coopPuzzleCheckpointSurvivesLeaveTimeout() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long t0 = 20_000L;
        Map<String, Object> press = g.coopPuzzles().pressPlate("pressure-duo-1", 1L, "zone-valley-a", t0);
        assertThat(press.get("ok")).isEqualTo(true);
        assertThat(press.get("checkpoint")).isNotNull();

        Map<String, Object> left = g.coopPuzzles().tickLeave(
                "pressure-duo-1", t0 + CoopPuzzleService.LEAVE_TIMEOUT_MS + 1);
        assertThat(left.get("checkpointPreserved")).isEqualTo(true);

        Map<String, Object> resume = g.coopPuzzles().resumeCheckpoint("pressure-duo-1");
        assertThat(resume.get("ok")).isEqualTo(true);
        assertThat(resume.get("reset")).isEqualTo(false);
        assertThat(resume.get("resumeScene")).isEqualTo(true);
    }

    @Test
    public void syncCutsceneBlocksGuestMoveAndGrantsSocialToken() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.coopNarrative().registerRoom("room-cut", 1L, 2L);
        Map<String, Object> host = g.coopNarrative().onStoryInstanceStart(
                "room-cut", 1L, "legend-1", 30_000L);
        assertThat(host.get("hostMutexLock")).isEqualTo("HOST_MUTEX_LOCK");
        assertThat(host.get("loadDialogueUi")).isEqualTo(true);

        Map<String, Object> guest = g.coopNarrative().onStoryInstanceStart(
                "room-cut", 2L, "legend-1", 30_000L);
        assertThat(guest.get("effect")).isEqualTo("WORLD_SHIFT_SHIELD");
        assertThat(guest.get("moveBlocked")).isEqualTo(true);
        assertThat(guest.get("optionsGreyed")).isEqualTo(true);
        assertThat(g.coopNarrative().isMoveBlocked(2L)).isTrue();

        SceneMoveCmd cmd = SceneMoveCmd.walk(1f, 0f, 1f, 5f, 30_100L);
        Map<String, Object> blocked = g.movementAdmission().admit(2L, cmd, 100L, 30_100L);
        assertThat(blocked.get("retcode")).isEqualTo(RetCode.CUTSCENE_MOVE_BLOCKED);

        Map<String, Object> opt = g.coopNarrative().chooseDialogueOption("room-cut", 2L, "A");
        assertThat(opt.get("ok")).isEqualTo(false);

        Map<String, Object> assist = g.coopNarrative().recordCoopAssist(
                "room-cut", 2L, "boss-legend", 30_200L);
        assertThat(assist.get("badge")).isEqualTo("助人之证");
        assertThat(assist.get("socialToken")).isNotNull();
        assertThat(assist.get("hostWorldSpecialtyRedeemable")).isEqualTo(true);

        Map<String, Object> redeem = g.socialToken().redeemHostSpecialty(2L, 1L, "region_specialty_herb");
        assertThat(redeem.get("ok")).isEqualTo(true);
    }

    @Test
    public void instanceLootBucketsIsolateHostFromGuests() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long battleId = g.fightContribution().openBattle("world-boss", 1_000_000L, 1L);
        g.fightContribution().grantInstanceLoot(battleId, 1L, "host_artifact", 2);
        g.fightContribution().grantInstanceLoot(battleId, 2L, "guest_mora", 10);
        g.fightContribution().grantInstanceLoot(battleId, 3L, "guest_mora", 10);
        Map<String, Object> settle = g.fightContribution().settleInstanceLoot(battleId);
        assertThat(settle.get("hostDiluted")).isEqualTo(false);
        assertThat(settle.get("guestDiluted")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> hostLoot = (List<Map<String, Object>>) settle.get("hostLoot");
        assertThat(hostLoot).hasSize(1);
        @SuppressWarnings("unchecked")
        List<?> guests = (List<?>) settle.get("guestBuckets");
        assertThat(guests).hasSize(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void statusOverviewExposesP17Flags() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> status = g.statusOverview();
        assertThat(status.get("p17")).isInstanceOf(Map.class);
        Map<String, Object> p17 = (Map<String, Object>) status.get("p17");
        assertThat(p17.get("worldOwnership")).isEqualTo(true);
        assertThat(p17.get("localBulletTime")).isEqualTo(true);
        assertThat(p17.get("hostMigration")).isEqualTo(true);
        assertThat(p17.get("terrainStateVector")).isEqualTo(true);
        assertThat(p17.get("syncCutscene")).isEqualTo(true);
    }
}
