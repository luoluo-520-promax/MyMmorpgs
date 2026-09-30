package cn.itcast.demo.mymmorpg.world.gameplay;

import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.sync.SceneMoveCmd;
import cn.itcast.demo.mymmorpg.world.coop.CoopRoomElectionService;
import cn.itcast.demo.mymmorpg.world.loot.LootOwnershipPolicy;
import cn.itcast.demo.mymmorpg.world.ownership.AccessLevel;
import cn.itcast.demo.mymmorpg.world.puzzle.CoopPuzzleService;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** P17 边界与负路径：主权拒绝、选举无候选人、快照过期、剧情选项权、解谜硬性态等。 */
public class OpenWorldP17EdgeCaseTest {

    @Test
    public void outsiderCannotInteractEvenWithAllShare() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.worldOwnership().bindRoom("r1", 1L, 2L);
        Map<String, Object> r = g.worldOwnership().authorizeInteract(
                99L, "r1", AccessLevel.ALL_SHARE, "collect");
        assertThat(r.get("authorized")).isEqualTo(false);
        assertThat(r.get("error")).isEqualTo("not_room_member");
    }

    @Test
    public void guestReadDeniesWriteButMarksVisible() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.worldOwnership().bindRoom("r2", 1L, 2L);
        Map<String, Object> r = g.worldOwnership().authorizeInteract(
                2L, "r2", AccessLevel.GUEST_READ, "chest");
        assertThat(r.get("ok")).isEqualTo(false);
        assertThat(r.get("visible")).isEqualTo(true);
        assertThat(r.get("error")).isEqualTo("guest_read_only");
    }

    @Test
    public void lootOwnershipHostOnlyMatchesPolicy() {
        LootOwnershipPolicy.LootDecision deny = LootOwnershipPolicy.decide(
                LootOwnershipPolicy.SyncMode.HOST_ONLY, 2L, 1L, Set.of(1L, 2L), 1L);
        assertThat(deny.allowed()).isFalse();
        LootOwnershipPolicy.LootDecision ok = LootOwnershipPolicy.decide(
                LootOwnershipPolicy.SyncMode.HOST_ONLY, 1L, 1L, Set.of(1L, 2L), 1L);
        assertThat(ok.allowed()).isTrue();
        assertThat(ok.pickupEligibleIds()).containsExactly(1L);
    }

    @Test
    public void electionDissolvesWhenNoCandidates() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        String room = "solo-host";
        g.coopElection().registerHost(room, 1L);
        g.coopElection().updateCandidates(room, List.of(
                new CoopRoomElectionService.Candidate(1L, 10L, 1)));
        long t0 = System.currentTimeMillis();
        g.coopElection().onHostDisconnect(room, 1L, t0);
        Map<String, Object> r = g.coopElection().tryElect(
                room, t0 + CoopRoomElectionService.HOST_DISCONNECT_THRESHOLD_MS + 1);
        assertThat(r.get("elected")).isEqualTo(false);
        assertThat(r.get("dissolveRoom")).isEqualTo(true);
        assertThat(r.get("error")).isEqualTo("no_candidate");
    }

    @Test
    public void roomSnapshotExpiresAfterTtl() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long old = System.currentTimeMillis() - cn.itcast.demo.mymmorpg.sync.ServerShadowService.ROOM_SNAPSHOT_TTL_MS - 1;
        g.serverShadow().saveRoomSnapshot("expired", 1L, 1L, 10L, "0", "IDLE", old);
        Map<String, Object> load = g.serverShadow().loadRoomSnapshot("expired");
        assertThat(load.get("ok")).isEqualTo(false);
        assertThat(load.get("error")).isEqualTo("snapshot_expired");
    }

    @Test
    public void hostReconnectCancelsPendingElection() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.coopElection().registerHost("r3", 1L);
        g.coopElection().onHostDisconnect("r3", 1L, System.currentTimeMillis());
        Map<String, Object> r = g.coopElection().onHostReconnect("r3", 1L);
        assertThat(r.get("electionCancelled")).isEqualTo(true);
        assertThat(r.get("stillHost")).isEqualTo(true);
        Map<String, Object> elect = g.coopElection().tryElect("r3", System.currentTimeMillis() + 60_000L);
        assertThat(elect.get("error")).isEqualTo("host_not_disconnected");
    }

    @Test
    public void guestDialogueOptionRejected() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.coopNarrative().registerRoom("r4", 1L, 2L);
        g.coopNarrative().onStoryInstanceStart("r4", 1L, "s1", 1L);
        Map<String, Object> r = g.coopNarrative().chooseDialogueOption("r4", 2L, "A");
        assertThat(r.get("ok")).isEqualTo(false);
        assertThat(r.get("error")).isEqualTo("host_option_only");
    }

    @Test
    public void redeemSpecialtyFailsWhenInsufficientTokens() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> r = g.socialToken().redeemHostSpecialty(88L, 1L, "herb");
        assertThat(r.get("ok")).isEqualTo(false);
        assertThat(r.get("error")).isEqualTo("insufficient_tokens");
    }

    @Test
    public void strictPhaseDowngradesRequiredMembers() {
        CoopPuzzleService puzzles = new CoopPuzzleService();
        puzzles.register(new CoopPuzzleService.PuzzleDef(
                "pressure-4", "zone-a", 4, "g4"));
        long t0 = 100_000L;
        Map<String, Object> first = puzzles.pressPlate("pressure-4", 1L, "zone-a", t0);
        assertThat(first.get("started")).isEqualTo(false);
        assertThat(first.get("phase")).isEqualTo("LOOSE");

        Map<String, Object> mid = puzzles.pressPlate(
                "pressure-4", 2L, "zone-a", t0 + CoopPuzzleService.LOOSE_PHASE_MS + 1);
        assertThat(mid.get("phase")).isEqualTo("STRICT");
        assertThat(mid.get("dynamicDifficulty")).isEqualTo(true);
        assertThat(((Number) mid.get("required")).intValue()).isEqualTo(2);
        assertThat(mid.get("tempBuff")).isNotNull();
    }

    @Test
    public void cutsceneReleaseAllowsMoveAgain() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.coopNarrative().registerRoom("r5", 1L, 2L);
        g.coopNarrative().onStoryInstanceStart("r5", 1L, "s", 1L);
        g.coopNarrative().onStoryInstanceStart("r5", 2L, "s", 1L);
        assertThat(g.coopNarrative().isMoveBlocked(2L)).isTrue();
        g.coopNarrative().releaseHostMutex("r5");
        assertThat(g.coopNarrative().isMoveBlocked(2L)).isFalse();
        SceneMoveCmd cmd = SceneMoveCmd.walk(1f, 0f, 1f, 5f, 2L);
        Map<String, Object> admit = g.movementAdmission().admit(2L, cmd, 100L, 2L);
        assertThat(admit.get("retcode")).isNotEqualTo(RetCode.CUTSCENE_MOVE_BLOCKED);
    }

    @Test
    public void instanceLootInvalidArgsRejected() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long bid = g.fightContribution().openBattle("b", 100L, 1L);
        Map<String, Object> bad = g.fightContribution().grantInstanceLoot(bid, 0L, "x", 1);
        assertThat(bad.get("ok")).isEqualTo(false);
        assertThat(bad.get("error")).isEqualTo("invalid_args");
    }
}
