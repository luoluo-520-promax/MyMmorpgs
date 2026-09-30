package cn.itcast.demo.mymmorpg.world.gameplay;

import cn.itcast.demo.mymmorpg.sync.MovementType;
import cn.itcast.demo.mymmorpg.sync.SceneMoveCmd;
import cn.itcast.demo.mymmorpg.world.puzzle.PhysicsLayerService;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** P6：垂直移动 / DoT Zone / 引导链 / CHAOS 诅咒 / 动态宝箱品质。 */
public class OpenWorldP6DepthFlowTest {

    @Test
    public void climbRequiresMeshAndConsumesStamina() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.traverse().unlock(1L, cn.itcast.demo.mymmorpg.world.traverse.TraverseModeService.Mode.CLIMB);
        SceneMoveCmd cmd = new SceneMoveCmd(400f, 30f, 200f, 5f, System.currentTimeMillis(),
                MovementType.CLIMB, "cliff-valley-north");
        Map<String, Object> r = g.movementAdmission().admit(1L, cmd, 500L, System.currentTimeMillis());
        assertThat(r.get("ok")).isEqualTo(true);
        assertThat(r.get("climbableMeshId")).isEqualTo("cliff-valley-north");
        assertThat(((Number) r.get("consumed")).floatValue()).isGreaterThan(0f);
    }

    @Test
    public void fireApplySpawnsBurnDotZoneAndTicksDamage() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long now = System.currentTimeMillis();
        Map<String, Object> apply = g.physics().apply(1, 10f, 10f, PhysicsLayerService.PhysicsKind.FIRE, 2, 5000, now);
        assertThat(apply.get("ok")).isEqualTo(true);
        assertThat(apply.get("persistentZone")).isInstanceOf(Map.class);
        Map<String, Object> tick = g.physics().tickZones(1, Map.of(99L, new float[]{10f, 10f}), now + 600);
        assertThat(((Number) tick.get("hitCount")).intValue()).isGreaterThanOrEqualTo(1);
    }

    @Test
    public void guidanceChainLeadsToChest() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long now = System.currentTimeMillis();
        Map<String, Object> start = g.guidanceChains().triggerStart(7L, "seelie-chest-1", 140f, 0f, 100f, now);
        assertThat(start.get("ok")).isEqualTo(true);
        g.guidanceChains().advance(7L, "seelie-chest-1", 145f, 2f, 110f, now + 1);
        g.guidanceChains().advance(7L, "seelie-chest-1", 148f, 4f, 115f, now + 2);
        Map<String, Object> end = g.guidanceChains().advance(7L, "seelie-chest-1", 150f, 0f, 120f, now + 3);
        assertThat(end.get("completed")).isEqualTo(true);
        assertThat(end.get("chestCollectibleId")).isEqualTo("chest-fine-1");
        Map<String, Object> particles = g.guidanceChains().refreshParticles(7L, "seelie-chest-1", now + 4);
        assertThat(particles.get("ok")).isEqualTo(false);
    }

    @Test
    public void chaosAfflictionStacksThenCleanseAnchorClears() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long t0 = 1_000_000L;
        g.regions().enterChaos("wolf-camp-valley", t0);
        Map<String, Object> early = g.regions().tickPlayerAffliction("wolf-camp-valley", 3L, t0 + 10_000);
        assertThat(early.get("afflicted")).isEqualTo(false);
        Map<String, Object> late = g.regions().tickPlayerAffliction("wolf-camp-valley", 3L, t0 + 50_000);
        assertThat(late.get("afflicted")).isEqualTo(true);
        assertThat(((Number) late.get("curseLayers")).intValue()).isGreaterThanOrEqualTo(1);
        Map<String, Object> cleanse = g.regions().interactCleanseAnchor(
                3L, "cleanse-valley-1", 200f, 0f, 200f, t0 + 51_000);
        assertThat(cleanse.get("ok")).isEqualTo(true);
        Map<String, Object> after = g.regions().tickPlayerAffliction("wolf-camp-valley", 3L, t0 + 52_000);
        assertThat(after.get("cleansed")).isEqualTo(true);
    }

    @Test
    public void dynamicLootTierUpgradeChestRewards() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> r = g.collectWithDynamicLoot(
                9L, "chest-common-1", 100f, 0f, 100f, 8, 0.9f);
        assertThat(r.get("ok")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> plans = (List<Map<String, Object>>) r.get("grantPlans");
        assertThat(plans).isNotEmpty();
        assertThat(plans.get(0).get("itemId")).isNotEqualTo("gold");
        assertThat(r.get("dynamicLoot")).isInstanceOf(Map.class);
    }
}
