package cn.itcast.demo.mymmorpg.world.gameplay;

import cn.itcast.demo.mymmorpg.sync.SceneMoveCmd;
import cn.itcast.demo.mymmorpg.sync.SurfaceType;
import cn.itcast.demo.mymmorpg.world.feel.PhysicalDetailService;
import cn.itcast.demo.mymmorpg.world.physics.GrabThrowService;
import cn.itcast.demo.mymmorpg.world.physics.MassMomentumService;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** P19 边界与防刷校验。 */
public class OpenWorldP19EdgeCaseTest {

    @Test
    public void bendVegetationRespectsCooldown() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long pid = 1L;
        long now = System.currentTimeMillis();
        SceneMoveCmd cmd = SceneMoveCmd.walk(1f, 0f, 1f, 5f, now);
        Map<String, Object> first = g.physicalDetail().onPlayerMove(pid, cmd, "z1", 0f, 0f, 0f, now);
        Map<String, Object> second = g.physicalDetail().onPlayerMove(pid, cmd, "z1", 0f, 0f, 0f, now + 100);
        assertThat(first.get("bendVegetation")).isNotNull();
        assertThat(second.get("bendSkipped")).isEqualTo(true);
    }

    @Test
    public void plainSurfaceSkipsFootprint() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        SceneMoveCmd cmd = new SceneMoveCmd(
                1f, 0f, 1f, 5f, System.currentTimeMillis(),
                cn.itcast.demo.mymmorpg.sync.MovementType.WALK, "",
                0, "", "", 0f, 0f, 0f, 0f, 0f, 0f, "",
                cn.itcast.demo.mymmorpg.sync.ActionState.GROUND_IDLE,
                cn.itcast.demo.mymmorpg.sync.MoveIntent.FORWARD, 0f, 16, SurfaceType.PLAIN);
        Map<String, Object> r = g.physicalDetail().onPlayerMove(
                1L, cmd, "z1", 0f, 0f, 0f, System.currentTimeMillis());
        assertThat(r.get("footprintSpawn")).isNull();
    }

    @Test
    public void physicsPredictionSoftPullsOverThreshold() {
        MassMomentumService mass = new MassMomentumService();
        Map<String, Object> r = mass.validatePrediction(
                1L, 0f, 0f, 0f, 0.5f, 0f, 0f, System.currentTimeMillis());
        assertThat(r.get("softPullback")).isEqualTo(true);
        assertThat(((Number) r.get("deviationM")).doubleValue()).isGreaterThan(MassMomentumService.PREDICT_DEVIATION_METERS);
    }

    @Test
    public void grabThrowRejectsDoubleGrab() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.grabThrow().register(new GrabThrowService.GrabbableEntity(
                "box-1", 1f, 0f, 1f, 2, 3f, true));
        long now = System.currentTimeMillis();
        Map<String, Object> first = g.grabThrow().tryGrab(1L, 1f, 0f, 1f, now);
        Map<String, Object> second = g.grabThrow().tryGrab(1L, 1f, 0f, 1f, now);
        assertThat(first.get("ok")).isEqualTo(true);
        assertThat(second.get("error")).isEqualTo("already_holding");
    }

    @Test
    public void ecologicalTableauScanCooldown() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long now = System.currentTimeMillis();
        g.ecologicalTableau().scanPredatorPrey("wolf-camp-valley", now);
        Map<String, Object> again = g.ecologicalTableau().scanPredatorPrey("wolf-camp-valley", now + 1000);
        assertThat(again.get("skipped")).isEqualTo(true);
    }

    @Test
    public void statusOverviewIncludesP19() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> status = g.statusOverview();
        assertThat(status.get("p19")).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> p19 = (Map<String, Object>) status.get("p19");
        assertThat(p19.get("microPhysics")).isEqualTo(true);
        assertThat(p19.get("footprintTtlMs")).isEqualTo(PhysicalDetailService.BEND_COOLDOWN_MS > 0
                ? cn.itcast.demo.mymmorpg.world.feel.TerrainDetailTracker.FOOTPRINT_TTL_MS : 0);
    }
}
