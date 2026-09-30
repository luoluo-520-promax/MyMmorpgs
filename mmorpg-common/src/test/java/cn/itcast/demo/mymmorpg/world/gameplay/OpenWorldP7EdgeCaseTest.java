package cn.itcast.demo.mymmorpg.world.gameplay;

import cn.itcast.demo.mymmorpg.sync.MoveFlags;
import cn.itcast.demo.mymmorpg.sync.MovementType;
import cn.itcast.demo.mymmorpg.sync.SceneMoveCmd;
import cn.itcast.demo.mymmorpg.world.battle.ReactionValidator;
import cn.itcast.demo.mymmorpg.world.puzzle.CoopPuzzleService;
import cn.itcast.demo.mymmorpg.world.puzzle.WorldMutabilityService;
import cn.itcast.demo.mymmorpg.world.traverse.TraverseModeService;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** P7 边界 / 拒绝路径：窗口外闪避、普通伤害不破坏、协同超时清空、体力耗尽等。 */
public class OpenWorldP7EdgeCaseTest {

    @Test
    public void perfectDodgeOutsideWindowRejected() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long t0 = 1_000_000L;
        g.reactions().openAttackWindow("atk-edge", 1L, t0, 100, 100);
        Map<String, Object> r = g.reactions().validate(
                ReactionValidator.ReactionKind.PERFECT_DODGE, "b", 1L, "atk-edge", t0 + 500, t0 + 500);
        assertThat(r.get("ok")).isEqualTo(false);
        assertThat(r.get("error")).isEqualTo("outside_window");
    }

    @Test
    public void normalDamageDoesNotDestroyEnvironment() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> r = g.mutability().applyDamage(
                "tree-valley-1", WorldMutabilityService.DamageType.NORMAL, 999, 1L, 300f);
        assertThat(r.get("ok")).isEqualTo(false);
        assertThat(r.get("error")).isEqualTo("requires_heavy_attack");
    }

    @Test
    public void coopPuzzleClearsWhenMembersLeaveTimeout() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long t0 = 10_000_000L;
        g.coopPuzzles().pressPlate("pressure-duo-1", 1L, "zone-valley-a", t0);
        Map<String, Object> cleared = g.coopPuzzles().tickLeave("pressure-duo-1", t0 + CoopPuzzleService.LEAVE_TIMEOUT_MS + 1);
        assertThat(cleared.get("cleared")).isEqualTo(true);
        assertThat(cleared.get("gadgetState")).isEqualTo("IDLE");
    }

    @Test
    public void airDashFailsWhenStaminaExhausted() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long now = System.currentTimeMillis();
        // 耗尽体力：连续空中冲刺
        for (int i = 0; i < 6; i++) {
            SceneMoveCmd dash = new SceneMoveCmd(
                    1f, 1f, 1f, 20f, now, MovementType.AIR_DASH, "",
                    MoveFlags.MID_AIR_DASH, "", "", 0f, 0f, 0f);
            g.movementAdmission().admit(88L, dash, 50L, now + i);
        }
        SceneMoveCmd last = new SceneMoveCmd(
                1f, 1f, 1f, 20f, now, MovementType.AIR_DASH, "",
                MoveFlags.MID_AIR_DASH, "", "", 0f, 0f, 0f);
        Map<String, Object> r = g.movementAdmission().admit(88L, last, 50L, now + 10);
        assertThat(r.get("ok")).isEqualTo(false);
        assertThat(r.get("error")).isEqualTo("stamina_exhausted");
    }

    @Test
    public void grappleLockedWithoutHookUnlock() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        SceneMoveCmd cmd = new SceneMoveCmd(
                480f, 60f, 490f, 20f, 1L, MovementType.GRAPPLE, "",
                0, "", "grapple-ruin-1", 0f, 0f, 0f);
        Map<String, Object> r = g.movementAdmission().admit(9L, cmd, 200L, 1L, 470f, 55f, 485f);
        assertThat(r.get("ok")).isEqualTo(false);
        assertThat(r.get("error")).isEqualTo("grapple_locked");
        g.traverse().unlock(9L, TraverseModeService.Mode.HOOK);
        Map<String, Object> ok = g.movementAdmission().admit(9L, cmd, 200L, 2L, 470f, 55f, 485f);
        assertThat(ok.get("ok")).isEqualTo(true);
    }

    @Test
    public void homelandCookRequiresCrop() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> r = g.homeland().cookWithCrop(1L, "recipe-x", "crop_wheat_common", 1L);
        assertThat(r.get("ok")).isEqualTo(false);
        assertThat(r.get("error")).isEqualTo("need_homeland_crop");
    }

    @Test
    public void replayTruncatesAtMaxEntries() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> start = g.replays().start("replay-cap", 1L, 1L);
        String id = String.valueOf(start.get("replayId"));
        for (int i = 0; i < 5000; i++) {
            assertThat(g.replays().append(id, i, i, "MOVE", Map.of()).get("ok")).isEqualTo(true);
        }
        Map<String, Object> over = g.replays().append(id, 5000, 5000L, "MOVE", Map.of());
        assertThat(over.get("ok")).isEqualTo(false);
        assertThat(over.get("error")).isEqualTo("truncated");
    }

    @Test
    public void catchCreatureRejectsWithoutItem() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.creatures().spawn(new cn.itcast.demo.mymmorpg.world.sideplay.CreatureCatchService.WildCreature(
                "wild-edge-1", "anemo_slime", 1, 1f, 0f, 1f, 10, 5, 50));
        Map<String, Object> r = g.creatures().catchCreature(
                1L, "wild-edge-1", "", 20, 0, System.currentTimeMillis());
        assertThat(r.get("ok")).isEqualTo(false);
        assertThat(r.get("error")).isEqualTo("item_required");
    }
}
