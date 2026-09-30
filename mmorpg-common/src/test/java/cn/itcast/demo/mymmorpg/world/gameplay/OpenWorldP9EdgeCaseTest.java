package cn.itcast.demo.mymmorpg.world.gameplay;

import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.world.battle.PoiseService;
import cn.itcast.demo.mymmorpg.world.content.RareEliteSpawnService;
import cn.itcast.demo.mymmorpg.world.explore.RegionAwakeningService;
import cn.itcast.demo.mymmorpg.world.progression.ConstellationService;
import cn.itcast.demo.mymmorpg.world.sideplay.HomelandGuardService;
import cn.itcast.demo.mymmorpg.world.traverse.FallAttackValidator;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** P9 边界与负反馈规避：锁定上限、硬直封锁、偷取上限、Buff 叠层、材料不足等。 */
public class OpenWorldP9EdgeCaseTest {

    @Test
    public void constellationRejectsWithoutMaterial() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> r = g.constellation().unlock(1L, "char-warden", 1);
        assertThat(r.get("ok")).isEqualTo(false);
        assertThat(r.get("error")).isEqualTo("insufficient_constellation_mat");
        assertThat(r.get("msgId")).isEqualTo(ConstellationService.MSG_CONSTELLATION_UNLOCK_SC_RSP);
        assertThat(MessageId.CONSTELLATION_UNLOCK_CS_REQ).isEqualTo(1210);
        assertThat(MessageId.ATTRIBUTE_RECALC_SC_NOTIFY).isEqualTo(1212);
    }

    @Test
    public void fallAttackBlockedDuringLandingLag() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long now = 20_000_000L;
        Map<String, Object> first = g.fallAttack().validateFallHeavy(
                2L, 8f, -4f, false, 50, 1, 0, 0, 0, now);
        assertThat(first.get("ok")).isEqualTo(true);
        Map<String, Object> second = g.fallAttack().validateFallHeavy(
                2L, 20f, -10f, true, 200, 1, 0, 0, 0, now + 10);
        assertThat(second.get("ok")).isEqualTo(false);
        assertThat(second.get("error")).isEqualTo("landing_lag");
        assertThat(second.get("forceAnim")).isEqualTo("LANDING_LAG");
        assertThat(MessageId.ACTION_FALL_HEAVY_CS_REQ).isEqualTo(212);
    }

    @Test
    public void rareEliteDoesNotDoubleSpawnInSameWindow() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long now = 21_000_000L;
        Map<String, Object> last = Map.of();
        for (int i = 0; i < RareEliteSpawnService.KILL_THRESHOLD; i++) {
            last = g.rareElites().recordKill("9_9", now + i);
        }
        assertThat(last.get("spawned")).isEqualTo(true);
        Map<String, Object> again = g.rareElites().recordKill("9_9", now + 100);
        assertThat(again.get("spawned")).isNull();
        assertThat(again.get("killsInWindow")).isEqualTo(RareEliteSpawnService.KILL_THRESHOLD + 1);
        assertThat(g.rareElites().gridStateOf("9_9"))
                .isEqualTo(RareEliteSpawnService.STATE_GRID_INFESTATION);
    }

    @Test
    public void homelandStealCapAndGuardCoinCheck() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> noCoin = g.homelandGuard().installGuard(3L, "p1", 10);
        assertThat(noCoin.get("ok")).isEqualTo(false);
        assertThat(noCoin.get("error")).isEqualTo("insufficient_homeland_coin");

        g.homelandGuard().markReady("c1", 200);
        Map<String, Object> steal1 = g.homelandGuard().steal(4L, "open", "c1", 22_000_000L);
        assertThat(steal1.get("amount")).isEqualTo(40); // 20%
        Map<String, Object> steal2 = g.homelandGuard().steal(4L, "open", "c1", 22_000_001L);
        // remain 160, cap 20% of remaining = 32
        assertThat(steal2.get("amount")).isEqualTo(32);
        assertThat(HomelandGuardService.STEAL_MAX_RATIO).isEqualTo(0.20);
    }

    @Test
    public void regionBuffStackCapIsThree() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long pid = 55L;
        long now = 23_000_000L;
        // 注册临时区域并拉满探索
        for (String region : java.util.List.of("r-a", "r-b", "r-c", "r-d")) {
            g.regionProgress().register(new cn.itcast.demo.mymmorpg.world.explore.RegionProgressService.RegionMeta(
                    region, region, 1, 1, 1, 1, java.util.List.of()));
            g.regionProgress().markWaypoint(pid, region, "w");
            g.regionProgress().markCollectible(pid, region, "c");
            g.regionProgress().markPuzzle(pid, region, "p");
            g.regionProgress().markWorldQuest(pid, region, "q");
            Map<String, Object> a = g.regionAwakening().checkAndAwaken(pid, "p", region, now);
            assertThat(a.get("awakened")).isEqualTo(true);
        }
        assertThat(g.regionAwakening().activeBuffs(pid, now)).hasSize(RegionAwakeningService.MAX_REGION_BUFFS);
        assertThat(g.regionAwakening().totalAttrBonus(pid, now))
                .isEqualTo(RegionAwakeningService.ATTR_BONUS * RegionAwakeningService.MAX_REGION_BUFFS);
    }

    @Test
    public void ultimateSuperArmorStillTakesDamageFlag() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.poise().initEntity(8L, 100, 5);
        g.poise().enterUltimate(8L, 24_000_000L);
        Map<String, Object> hit = g.poise().applyPoiseDamage(9L, 8L, 80, 24_000_100L);
        assertThat(hit.get("superArmor")).isEqualTo(true);
        assertThat(hit.get("damageTaken")).isEqualTo(true);
        assertThat(hit.get("interrupted")).isEqualTo(false);
        assertThat(hit.get("stagger_level")).isEqualTo(PoiseService.StaggerLevel.NONE.code());
        assertThat(MessageId.HIT_CONFIRM_SC_NOTIFY).isEqualTo(214);
        assertThat(MessageId.EXECUTION_TRIGGER_SC_NOTIFY).isEqualTo(216);
    }

    @Test
    public void skillWithoutConstellationUsesBaseTable() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> cast = g.skillCast().resolveCast(10L, "char-warden", "skill-evil-warding", 1L);
        assertThat(cast.get("source")).isEqualTo("skill_table");
        assertThat(cast.get("cooldownMs")).isEqualTo(12_000);
        assertThat(cast.get("projectileCount")).isEqualTo(1);
        assertThat(cast.get("hitCount")).isEqualTo(1);
    }

    @Test
    public void fallDamageScalesWithHeight() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> low = g.fallAttack().validateFallHeavy(
                11L, 5f, -3.1f, true, 100, 1, 0, 0, 0, 25_000_000L);
        Map<String, Object> high = g.fallAttack().validateFallHeavy(
                12L, 20f, -3.1f, true, 100, 1, 0, 0, 0, 25_000_000L);
        assertThat(((Number) high.get("damage")).intValue())
                .isGreaterThan(((Number) low.get("damage")).intValue());
        assertThat(((Number) high.get("damageMultiplier")).doubleValue())
                .isEqualTo(1.0 + 20.0 / 10.0);
        assertThat(FallAttackValidator.MIN_FALL_ATTACK_HEIGHT_M).isEqualTo(5f);
    }
}
