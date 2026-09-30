package cn.itcast.demo.mymmorpg.world.gameplay;

import cn.itcast.demo.mymmorpg.sync.MoveFlags;
import cn.itcast.demo.mymmorpg.sync.MovementType;
import cn.itcast.demo.mymmorpg.sync.SceneMoveCmd;
import cn.itcast.demo.mymmorpg.world.battle.ReactionValidator;
import cn.itcast.demo.mymmorpg.world.puzzle.WorldMutabilityService;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** P7：瞬时博弈 / 钩锁载具 / 可破坏 / 协同解谜 / 编年史 / 家园 / 极限移动 / 突破本 / 重播。 */
public class OpenWorldP7FeelFlowTest {

    @Test
    public void perfectDodgeOpensBulletTimeAndCountsScore() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long now = 1_000_000L;
        g.reactions().openAttackWindow("atk-1", 9001L, now, 200, 180);
        Map<String, Object> r = g.reactions().validate(
                ReactionValidator.ReactionKind.PERFECT_DODGE, "b1", 7L, "atk-1", now + 50, now + 50);
        assertThat(r.get("ok")).isEqualTo(true);
        assertThat(r.get("event")).isEqualTo("bulletTimeStart");
        assertThat(g.bulletTime().shouldTickNpc("b1", 9001L, now + 100)).isTrue();
        assertThat(g.bulletTime().shouldTickNpc("b1", 9002L, now + 100)).isFalse();
        assertThat(g.reactions().settleScore("b1").get("perfectDodgeCount")).isEqualTo(1);
    }

    @Test
    public void grappleAdmitRejectsBlockedRay() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.grappleNodes().register(new cn.itcast.demo.mymmorpg.world.traverse.GrappleNodeService.GrappleNode(
                "g-test", 1, 20f, 10f, 20f, 5f, 100L));
        g.grappleNodes().markBlocked(10f, 10f, 10f);
        Map<String, Object> blocked = g.grappleNodes().admitGrapple(
                1L, "g-test", 0f, 10f, 0f, 20f, 10f, 20f, 1000L);
        assertThat(blocked.get("ok")).isEqualTo(false);
        assertThat(blocked.get("error")).isEqualTo("line_of_sight_blocked");
        g.grappleNodes().clearBlocked(10f, 10f, 10f);
        Map<String, Object> ok = g.grappleNodes().admitGrapple(
                1L, "g-test", 0f, 10f, 0f, 20f, 10f, 20f, 2000L);
        assertThat(ok.get("ok")).isEqualTo(true);
    }

    @Test
    public void heavyAttackDestroysAndRespawnsViaScan() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long now = 2_000_000L;
        Map<String, Object> hit = g.mutability().applyDamage(
                "tree-valley-1", WorldMutabilityService.DamageType.HEAVY_ATTACK, 100, now, 300f);
        assertThat(hit.get("mutated")).isEqualTo(true);
        assertThat(((Map<?, ?>) hit.get("mutationBroadcast")).get("cmd")).isEqualTo("MutationBroadcast");
        Map<String, Object> scan = g.mutability().scanRespawn(now + 200_000L, 100);
        assertThat(((Number) scan.get("count")).intValue()).isGreaterThanOrEqualTo(1);
    }

    @Test
    public void coopPressurePlateStartsWhenTwoInSameZone() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long now = 3_000_000L;
        Map<String, Object> a = g.coopPuzzles().pressPlate("pressure-duo-1", 1L, "zone-valley-a", now);
        assertThat(a.get("started")).isEqualTo(false);
        Map<String, Object> b = g.coopPuzzles().pressPlate("pressure-duo-1", 2L, "zone-valley-a", now + 10);
        assertThat(b.get("started")).isEqualTo(true);
        assertThat(b.get("event")).isEqualTo("CoopPuzzleStart");
    }

    @Test
    public void chronicleGlobalFlagBoostsBossSpawn() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        for (long i = 1; i <= 8; i++) {
            g.chronicle().choose(i, "choice-dragon-fate", "slay_dragon");
        }
        g.chronicle().choose(9L, "choice-dragon-fate", "spare_dragon");
        g.chronicle().choose(10L, "choice-dragon-fate", "spare_dragon");
        Map<String, Object> flag = g.chronicle().evaluateGlobalFlag("choice-dragon-fate", 0.8, 0.5);
        assertThat(flag.get("global_flag")).isEqualTo(true);
        assertThat(((Number) flag.get("bossSpawnRateMul")).doubleValue()).isEqualTo(1.5);
    }

    @Test
    public void homelandLazyHarvestAndCookConsumesCrop() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long now = 4_000_000L;
        g.homeland().claimLand(11L, "plot-p7-1");
        Map<String, Object> plant = g.homeland().plant(11L, "plot-p7-1", "wheat", 1_000L, now);
        assertThat(plant.get("ok")).isEqualTo(true);
        String cropId = String.valueOf(plant.get("cropId"));
        g.homeland().water(cropId, 99L);
        Map<String, Object> harvest = g.homeland().harvest(11L, cropId, now + 2_000L);
        assertThat(harvest.get("ok")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> plans = (List<Map<String, Object>>) harvest.get("grantPlans");
        String cropItem = String.valueOf(plans.get(0).get("itemId"));
        Map<String, Object> cook = g.homeland().cookWithCrop(11L, "sweet_madame", cropItem, now + 3_000L);
        assertThat(cook.get("ok")).isEqualTo(true);
    }

    @Test
    public void airDashAndWallRunConsumeStaminaWithFlags() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long now = System.currentTimeMillis();
        SceneMoveCmd dash = new SceneMoveCmd(
                10f, 5f, 10f, 20f, now, MovementType.AIR_DASH, "",
                MoveFlags.MID_AIR_DASH, "", "", 0f, 0f, 0f);
        Map<String, Object> r1 = g.movementAdmission().admit(3L, dash, 50L, now);
        assertThat(r1.get("ok")).isEqualTo(true);
        assertThat(((Number) r1.get("consumed")).floatValue()).isEqualTo(20f);

        SceneMoveCmd wall = new SceneMoveCmd(
                10f, 5f, 12f, 8f, now, MovementType.WALL_RUN, "",
                MoveFlags.WALL_RUN, "", "", 1f, 0f, 0f);
        Map<String, Object> r2 = g.movementAdmission().admit(3L, wall, 1000L, now, 10f, 5f, 10f);
        assertThat(r2.get("ok")).isEqualTo(true);
        assertThat(r2.get("wallNormal")).isInstanceOf(Map.class);
    }

    @Test
    public void ascensionDungeonUpgradesWorldLevelOnVictory() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.ascension().worldLevels().setWorldLevel(1, 22L, 1);
        Map<String, Object> enter = g.ascension().enter(
                22L, "asc-wl-1-to-2", 1, true, true, System.currentTimeMillis());
        assertThat(enter.get("event")).isEqualTo("ENTER_ASCENSION_DUNGEON");
        String runId = String.valueOf(enter.get("runId"));
        Map<String, Object> settle = g.ascension().settle(runId, true, 1, System.currentTimeMillis());
        assertThat(settle.get("upgraded")).isEqualTo(true);
        assertThat(g.ascension().worldLevels().getWorldLevel(1, 22L)).isEqualTo(2);
    }

    @Test
    public void battleReplayRecordsAndPlayback() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> start = g.replays().start("replay-demo", 42L, System.currentTimeMillis());
        String id = String.valueOf(start.get("replayId"));
        g.replays().append(id, 1, 1L, "MOVE", Map.of("x", 1));
        g.replays().append(id, 2, 2L, "PERFECT_DODGE", Map.of("damage", 0));
        g.replays().append(id, 3, 3L, "SKILL", Map.of("damage", 120));
        Map<String, Object> play = g.replays().playback(id, 2.0, 100d, 200d);
        assertThat(play.get("withinSeedExpectation")).isEqualTo(true);
        assertThat(((Number) play.get("frameCount")).intValue()).isEqualTo(3);
    }

    @Test
    public void requirePartyMembersRuleFires() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> miss = g.rules().fire("PRESS_PLATE", Map.of("party_members", 1));
        assertThat(((Number) miss.get("matchedCount")).intValue()).isEqualTo(0);
        Map<String, Object> hit = g.rules().fire("PRESS_PLATE", Map.of("party_members", 2));
        assertThat(((Number) hit.get("matchedCount")).intValue()).isGreaterThanOrEqualTo(1);
    }

    @Test
    public void creatureCatchUsesServerRollAndMount() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> forced = g.creatures().forceCatch(5L, "wild-fox-1", System.currentTimeMillis());
        assertThat(forced.get("caught")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> pet = (Map<String, Object>) forced.get("pet");
        g.creatures().tameAndName(5L, String.valueOf(pet.get("instanceId")), "雪狐");
        Map<String, Object> mount = g.creatures().mount(5L, String.valueOf(pet.get("instanceId")));
        assertThat(mount.get("ok")).isEqualTo(true);
        assertThat(mount.get("mountCreatureUid")).isEqualTo(pet.get("instanceId"));
    }
}
