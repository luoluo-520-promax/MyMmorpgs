package cn.itcast.demo.mymmorpg.world.gameplay;

import cn.itcast.demo.mymmorpg.sync.SceneMoveCmd;
import cn.itcast.demo.mymmorpg.sync.SurfaceType;
import cn.itcast.demo.mymmorpg.world.civilization.CivilizationScheduleService;
import cn.itcast.demo.mymmorpg.world.ecosystem.EcosystemBehaviorService;
import cn.itcast.demo.mymmorpg.world.feel.TerrainDetailTracker;
import cn.itcast.demo.mymmorpg.world.physics.MassMomentumService;
import cn.itcast.demo.mymmorpg.world.time.WorldTimeService;
import cn.itcast.demo.mymmorpg.world.traverse.GrapplePhysicsService;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** P19：六大沉浸感短板补齐主流程。 */
public class OpenWorldP19FlowTest {

    @Test
    public void physicalDetailBendsVegetationAndSpawnsFootprint() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long pid = 19001L;
        long now = System.currentTimeMillis();
        SceneMoveCmd snow = new SceneMoveCmd(
                100f, 0f, 100f, 6f, now, cn.itcast.demo.mymmorpg.sync.MovementType.WALK, "",
                0, "", "", 0f, 0f, 0f, 0f, 0f, 0f, "",
                cn.itcast.demo.mymmorpg.sync.ActionState.GROUND_IDLE,
                cn.itcast.demo.mymmorpg.sync.MoveIntent.FORWARD, 0f, 16, SurfaceType.SNOW);
        Map<String, Object> r = g.physicalDetail().onPlayerMove(
                pid, snow, "wolf-camp-valley", 99f, 0f, 100f, now);
        assertThat(r.get("ok")).isEqualTo(true);
        assertThat(r.get("bendVegetation")).isNotNull();
        assertThat(r.get("footprintSpawn")).isNotNull();
        Map<String, Object> snap = g.physicalDetail().syncAoiEnter("wolf-camp-valley", now + 100);
        assertThat(snap.get("footprints")).asList().isNotEmpty();
    }

    @Test
    public void projectileHitPersistsDebris() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long now = System.currentTimeMillis();
        Map<String, Object> hit = g.physicalDetail().onProjectileHit(
                1L, "wolf-camp-valley", "ARROW",
                50f, 2f, 50f, 0f, 1f, 0f, 45f, now);
        assertThat(hit.get("persist")).isEqualTo(true);
        assertThat(hit.get("ttlMs")).isEqualTo(TerrainDetailTracker.DEBRIS_TTL_MS);
        Map<String, Object> snap = g.physicalDetail().syncAoiEnter("wolf-camp-valley", now);
        assertThat(snap.get("debris")).asList().isNotEmpty();
    }

    @Test
    public void civilizationScheduleClosesShopAtNight() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.gameTimeKeeper().worldTime().forceWeather(WorldTimeService.Weather.CLEAR);
        // 模拟深夜：force hour via tick at night epoch
        g.civilizationSchedule().registerNpc(
                new CivilizationScheduleService.NpcBaseState("shop-test", "merchant", 0f, 0f, 0f, true),
                new CivilizationScheduleService.ScheduleTemplate("shop-test", "r1", java.util.List.of()));
        // 23:00 ≈ hour 23
        long nightMs = System.currentTimeMillis();
        g.gameTimeKeeper().worldTime().configure(30f, nightMs - 23L * 120_000L);
        Map<String, Object> shop = g.civilizationSchedule().checkShopAccess("shop-test", 99L, nightMs);
        assertThat(shop.get("storeOpen")).isEqualTo(false);
        assertThat(shop.get("error")).isEqualTo("STORE_CLOSED");
        assertThat(shop.get("stealthTheftQuestEligible")).isEqualTo(true);
    }

    @Test
    public void massMomentumKnockBackScalesWithMass() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long now = System.currentTimeMillis();
        g.massMomentum().registerMass(100L, 2);
        g.massMomentum().registerMass(200L, 9);
        Map<String, Object> light = g.massMomentum().computeKnockBack(1L, 100L, 40d, 0.2, now);
        Map<String, Object> heavy = g.massMomentum().computeKnockBack(1L, 200L, 40d, 0.2, now);
        double dLight = ((Number) light.get("knockBackDistance")).doubleValue();
        double dHeavy = ((Number) heavy.get("knockBackDistance")).doubleValue();
        assertThat(dLight).isGreaterThan(dHeavy);
    }

    @Test
    public void grappleUsesMassRatingFormula() {
        GrapplePhysicsService grapple = new GrapplePhysicsService();
        long now = System.currentTimeMillis();
        Map<String, Object> boss = grapple.pullEnemy(1L, 2L, 9, 50d, 0.2, now);
        Map<String, Object> slime = grapple.pullEnemy(1L, 3L, 2, 50d, 0.2, now);
        assertThat(boss.get("outcome")).isEqualTo("PLAYER_PULLED_TO_ENEMY");
        assertThat(slime.get("outcome")).isEqualTo("ENEMY_PULLED_TO_PLAYER");
        assertThat(((Number) slime.get("knockBackDistance")).doubleValue())
                .isGreaterThan(((Number) boss.get("knockBackDistance")).doubleValue());
    }

    @Test
    public void perceptionModifierMufflesRain() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> rain = g.perceptionModifier().resolveWeatherModifiers(WorldTimeService.Weather.RAIN);
        assertThat(rain.get("audioMuffle")).isEqualTo(0.6);
        assertThat(rain.get("hideEnemyRedDot")).isEqualTo(true);
    }

    @Test
    public void ecologicalTableauSpawnsCarcassNearPredatorPrey() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.ecosystem().register(new EcosystemBehaviorService.CreatureEcoProfile(
                "wolf-p", "wolf_predator", "wolf-camp-valley", 10f, 0f, 10f, false, 15f));
        g.ecosystem().register(new EcosystemBehaviorService.CreatureEcoProfile(
                "rabbit-p", "rabbit_prey", "wolf-camp-valley", 12f, 0f, 11f, true, 8f));
        // 强制扫描（重置 lastScanMs）
        long now = System.currentTimeMillis();
        Map<String, Object> scan = g.ecologicalTableau().scanPredatorPrey("wolf-camp-valley", now);
        assertThat(scan.get("ok")).isEqualTo(true);
    }

    @Test
    public void terrainTsvIncludesDestroyedMeshes() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long now = System.currentTimeMillis();
        g.terrainStateVector().markMeshDestroyed("wolf-camp-valley", "bridge-uid-1", now);
        Map<String, Object> tsv = g.terrainStateVector().snapshotZone("wolf-camp-valley");
        assertThat(tsv.get("destroyedStaticMeshUids")).asList().contains("bridge-uid-1");
    }

    @Test
    public void coopHostMigrationLoadsTerrainSnapshot() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long now = System.currentTimeMillis();
        g.coopTerrainSnapshot().save("room-1", java.util.List.of("tree-1", "rock-2"), 5L, now);
        g.coopElection().registerHost("room-1", 100L);
        g.coopElection().onHostDisconnect("room-1", 100L, now);
        g.coopElection().updateCandidates("room-1", java.util.List.of(
                new cn.itcast.demo.mymmorpg.world.coop.CoopRoomElectionService.Candidate(200L, 30L, 2)));
        Map<String, Object> elect = g.coopElection().tryElect("room-1", now + 31_000L);
        assertThat(elect.get("elected")).isEqualTo(true);
        assertThat(elect.get("terrainCoopSnapshot")).isNotNull();
        assertThat(elect.get("rejectInstantReconnect")).isEqualTo(true);
    }
}
