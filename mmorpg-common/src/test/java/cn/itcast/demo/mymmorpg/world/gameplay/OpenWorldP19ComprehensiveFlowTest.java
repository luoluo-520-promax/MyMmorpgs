package cn.itcast.demo.mymmorpg.world.gameplay;

import cn.itcast.demo.mymmorpg.sync.SceneMoveCmd;
import cn.itcast.demo.mymmorpg.sync.SurfaceType;
import cn.itcast.demo.mymmorpg.world.civilization.CivilizationScheduleService;
import cn.itcast.demo.mymmorpg.world.coop.CoopRoomElectionService;
import cn.itcast.demo.mymmorpg.world.ecosystem.EcosystemBehaviorService;
import cn.itcast.demo.mymmorpg.world.feel.TerrainDetailTracker;
import cn.itcast.demo.mymmorpg.world.physics.GrabThrowService;
import cn.itcast.demo.mymmorpg.world.time.GameTimeKeeper;
import cn.itcast.demo.mymmorpg.world.time.WorldTimeService;
import cn.itcast.demo.mymmorpg.world.traverse.GrapplePhysicsService;
import cn.itcast.demo.mymmorpg.world.traverse.VehicleCombatService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P19 六大沉浸感短板 — 全链路综合业务流程测试。
 * 覆盖：微观物理 / 文明作息 / 质量动量 / 感官干扰 / 生态纪录片 / 联机物理快照。
 */
public class OpenWorldP19ComprehensiveFlowTest {

    private OpenWorldGameplayFacade g;
    private static final String REGION = "wolf-camp-valley";
    private static final String ROOM = "coop-p19-room";
    private long now;

    @BeforeMethod
    public void setUp() {
        g = new OpenWorldGameplayFacade();
        now = System.currentTimeMillis();
    }

    // ── 一、微观物理反馈全链路 ──────────────────────────────────────

    @Test
    public void microPhysics_fullPlayerJourney_grassSnowMudFootprintsAndDebris() {
        long player = 190100L;
        int i = 0;
        for (SurfaceType surface : List.of(SurfaceType.GRASS, SurfaceType.SNOW, SurfaceType.MUD)) {
            long t = now + i * 600L; // 超过 BEND_COOLDOWN_MS
            SceneMoveCmd cmd = walkOn(player, 100f + surface.ordinal(), surface);
            Map<String, Object> move = g.physicalDetail().onPlayerMove(
                    player, cmd, REGION, cmd.targetX() - 1f, 0f, cmd.targetZ(), t);
            assertThat(move.get("ok")).isEqualTo(true);
            assertThat(move.get("bendVegetation")).isNotNull();
            assertThat(move.get("footprintSpawn")).isNotNull();
            @SuppressWarnings("unchecked")
            Map<String, Object> fp = (Map<String, Object>) move.get("footprintSpawn");
            assertThat(fp.get("surfaceType")).isEqualTo(surface.name());
            i++;
        }
        Map<String, Object> arrow = g.physicalDetail().onProjectileHit(
                player, REGION, "ARROW", 105f, 1.5f, 105f, 0f, 1f, 0f, 90f, now);
        Map<String, Object> spell = g.physicalDetail().onProjectileHit(
                player, REGION, "SPELL_BOLT", 106f, 0.5f, 106f, 1f, 0f, 0f, 0f, now);
        assertThat(arrow.get("persist")).isEqualTo(true);
        assertThat(spell.get("persist")).isEqualTo(true);

        Map<String, Object> aoi = g.physicalDetail().syncAoiEnter(REGION, now + 500);
        assertThat(aoi.get("footprints")).asList().hasSizeGreaterThanOrEqualTo(3);
        assertThat(aoi.get("debris")).asList().hasSizeGreaterThanOrEqualTo(2);
        assertThat(aoi.get("footprintTtlMs")).isEqualTo(TerrainDetailTracker.FOOTPRINT_TTL_MS);
        assertThat(aoi.get("debrisTtlMs")).isEqualTo(TerrainDetailTracker.DEBRIS_TTL_MS);
    }

    @Test
    public void microPhysics_bendCooldownPreventsSpam() {
        long player = 190101L;
        SceneMoveCmd cmd = walkOn(player, 50f, SurfaceType.GRASS);
        Map<String, Object> first = g.physicalDetail().onPlayerMove(
                player, cmd, REGION, 49f, 0f, 50f, now);
        Map<String, Object> spam = g.physicalDetail().onPlayerMove(
                player, cmd, REGION, 49f, 0f, 50f, now + 50);
        assertThat(first.get("bendVegetation")).isNotNull();
        assertThat(spam.get("bendSkipped")).isEqualTo(true);
        assertThat(spam.get("bendCooldownRemainMs")).isNotNull();
    }

    @Test
    public void microPhysics_footprintBumpsTerrainRevision() {
        long player = 190102L;
        SceneMoveCmd cmd = walkOn(player, 200f, SurfaceType.SNOW);
        Map<String, Object> move = g.physicalDetail().onPlayerMove(
                player, cmd, REGION, 199f, 0f, 200f, now);
        @SuppressWarnings("unchecked")
        Map<String, Object> fp = (Map<String, Object>) move.get("footprintSpawn");
        assertThat(fp.get("stateRevision")).isNotNull();
        assertThat(((Number) fp.get("stateRevision")).longValue()).isGreaterThan(0L);
        Map<String, Object> tsv = g.terrainStateVector().snapshotZone(REGION);
        assertThat(tsv.get("mutations")).asList().isNotEmpty();
    }

    // ── 二、文明时态引擎 ────────────────────────────────────────────

    @Test
    public void civilization_npcDayNightScheduleAndRainShelter() {
        Map<String, Object> guardDay = g.civilizationSchedule().resolveNpcState("npc-guard-valley", now);
        assertThat(guardDay.get("ok")).isEqualTo(true);
        assertThat(guardDay.get("action")).isIn("PATROL", "SLEEP", "SEEK_SHELTER", "IDLE");

        g.gameTimeKeeper().worldTime().forceWeather(WorldTimeService.Weather.RAIN);
        Map<String, Object> guardRain = g.civilizationSchedule().resolveNpcState("npc-guard-valley", now);
        assertThat(guardRain.get("action")).isEqualTo("SEEK_SHELTER");
        assertThat(guardRain.get("weatherOverride")).isEqualTo(true);

        Map<String, Object> shopNight = g.civilizationSchedule().checkShopAccess(
                "npc-shop-valley", 1L, now);
        // 种子 NPC 存在；若当前为营业时段则 storeOpen=true
        assertThat(shopNight.get("ok")).isEqualTo(true);
    }

    @Test
    public void civilization_storeClosedAtNightEnablesStealthQuest() {
        g.civilizationSchedule().registerNpc(
                new CivilizationScheduleService.NpcBaseState("shop-night-test", "merchant", 0f, 0f, 0f, true),
                new CivilizationScheduleService.ScheduleTemplate("shop-night-test", REGION, List.of()));
        // 强制时钟到 23:00
        long nightEpoch = now - 23L * GameTimeKeeper.REAL_MS_PER_GAME_HOUR;
        g.gameTimeKeeper().worldTime().configure(30f, nightEpoch);
        Map<String, Object> access = g.civilizationSchedule().checkShopAccess("shop-night-test", 42L, now);
        assertThat(access.get("storeOpen")).isEqualTo(false);
        assertThat(access.get("error")).isEqualTo("STORE_CLOSED");
        assertThat(access.get("stealthTheftQuestEligible")).isEqualTo(true);
    }

    @Test
    public void civilization_gameClockOneHourEqualsTwoRealMinutes() {
        Map<String, Object> clock = g.gameTimeKeeper().view(now);
        assertThat(clock.get("realMsPerGameHour")).isEqualTo(GameTimeKeeper.REAL_MS_PER_GAME_HOUR);
        assertThat(clock.get("epochVersion")).isNotNull();
    }

    // ── 三、质量-动量继承引擎 ────────────────────────────────────────

    @Test
    public void massMomentum_knockBackInverselyProportionalToMass() {
        g.massMomentum().registerMass(301L, 2);
        g.massMomentum().registerMass(302L, 10);
        Map<String, Object> light = g.massMomentum().computeKnockBack(1L, 301L, 50d, 0.1, now);
        Map<String, Object> heavy = g.massMomentum().computeKnockBack(1L, 302L, 50d, 0.1, now);
        double dLight = num(light, "knockBackDistance");
        double dHeavy = num(heavy, "knockBackDistance");
        assertThat(dLight).isGreaterThan(dHeavy * 3);
        assertThat(light.get("event")).isEqualTo("PREDICTED_PHYSICS_DELTA");
    }

    @Test
    public void massMomentum_clientDeviationOverThresholdSoftPullsBack() {
        Map<String, Object> ok = g.massMomentum().validatePrediction(
                1L, 10f, 0f, 10f, 10.1f, 0f, 10.1f, now);
        assertThat(ok.get("softPullback")).isEqualTo(false);

        Map<String, Object> cheat = g.massMomentum().validatePrediction(
                1L, 0f, 0f, 0f, 1f, 0f, 0f, now);
        assertThat(cheat.get("softPullback")).isEqualTo(true);
    }

    @Test
    public void massMomentum_grappleBossVsSlimeDifferentOutcomes() {
        Map<String, Object> boss = g.grapplePhysics().pullEnemy(1L, 100L, 9, 60d, 0.2, now);
        Map<String, Object> slime = g.grapplePhysics().pullEnemy(1L, 101L, 2, 60d, 0.2, now);
        assertThat(boss.get("outcome")).isEqualTo("PLAYER_PULLED_TO_ENEMY");
        assertThat(slime.get("outcome")).isEqualTo("ENEMY_PULLED_TO_PLAYER");
        assertThat(num(slime, "knockBackDistance")).isGreaterThan(num(boss, "knockBackDistance"));
    }

    @Test
    public void massMomentum_vehicleMassAffectsEjectDistance() {
        g.vehicleCombat().register(new VehicleCombatService.VehicleCombatDef(
                "light-cart", List.of("ram"), 50f, 2));
        g.vehicleCombat().register(new VehicleCombatService.VehicleCombatDef(
                "heavy-siege", List.of("cannon"), 200f, 10));
        g.vehicleCombat().board(1L, "light-cart");
        g.vehicleCombat().board(2L, "heavy-siege");
        Map<String, Object> lightHit = g.vehicleCombat().applyPoiseDamage(1L, 80f, 10f, 0.1);
        Map<String, Object> heavyHit = g.vehicleCombat().applyPoiseDamage(2L, 80f, 10f, 0.1);
        assertThat(num(lightHit, "knockBackDistance")).isGreaterThan(num(heavyHit, "knockBackDistance"));
    }

    @Test
    public void massMomentum_grabThrowBreaksDoorByMass() {
        g.grabThrow().register(new GrabThrowService.GrabbableEntity(
                "heavy-barrel", 1f, 0f, 1f, 8, 3f, true));
        g.grabThrow().tryGrab(1L, 1f, 0f, 1f, now);
        Map<String, Object> thr = g.grabThrow().throwHeld(
                1L, 0f, 0.3f, 1f, 15f, 20f, 0f, 20f, now);
        assertThat(thr.get("canBreakDoor")).isEqualTo(true);
        assertThat(thr.get("parabolaServerValidated")).isEqualTo(true);
        assertThat(((Number) thr.get("doorBreakDamage")).intValue()).isGreaterThan(15);
    }

    // ── 四、感官干扰系统 ────────────────────────────────────────────

    @Test
    public void perception_heavyRainMufflesAndBlurs() {
        Map<String, Object> rain = g.perceptionModifier().resolveWeatherModifiers(WorldTimeService.Weather.RAIN);
        assertThat(rain.get("audioMuffle")).isEqualTo(0.6);
        assertThat(rain.get("visualBlur")).isEqualTo(0.3);
        assertThat(rain.get("hideEnemyRedDot")).isEqualTo(true);
    }

    @Test
    public void perception_caveEchoDepthByDelay() {
        Map<String, Object> shallow = g.perceptionModifier().resolveCaveAcoustics("CAVE", 120);
        Map<String, Object> deep = g.perceptionModifier().resolveCaveAcoustics("CAVE", 350);
        assertThat(shallow.get("caveDepthHint")).isEqualTo("SHALLOW");
        assertThat(deep.get("caveDepthHint")).isEqualTo("DEEP");
        assertThat(deep.get("environmentAudioProfile")).isEqualTo("CAVE_REVERB");
    }

    @Test
    public void perception_eyeAdaptationReducesSensitivity() {
        Map<String, Object> adapt = g.perceptionModifier().eyeAdaptation(1L, true, now);
        assertThat(adapt.get("event")).isEqualTo("EYE_ADAPTATION_CURVE");
        assertThat(adapt.get("moveSensitivityMul")).isEqualTo(0.7f);
        assertThat(adapt.get("durationMs")).isEqualTo(2500L);
    }

    @Test
    public void perception_combinedPlayerProfile() {
        Map<String, Object> profile = g.perceptionModifier().resolveForPlayer(
                1L, REGION, WorldTimeService.Weather.THUNDER, "CAVE", 280, true, now);
        assertThat(profile.get("ok")).isEqualTo(true);
        assertThat(profile.get("audioMuffle")).isEqualTo(0.6);
        assertThat(profile.get("cave")).isNotNull();
        assertThat(profile.get("eyeAdaptation")).isNotNull();
    }

    @Test
    public void perception_heartbeatCarriesEnvironmentAudio() {
        Map<String, Object> hb = g.clientPredict().heartbeatWithEnvironment(1L, "CAVE", 220, now);
        assertThat(hb.get("environmentAudioProfile")).isEqualTo("CAVE_REVERB");
        assertThat(hb.get("caveDepthHint")).isEqualTo("DEEP");
        assertThat(hb.get("serverEchoValidated")).isEqualTo(true);
    }

    // ── 五、生态纪录片引擎 ──────────────────────────────────────────

    @Test
    public void ecological_predatorPreyScanMaySpawnCarcass() {
        g.ecosystem().register(new EcosystemBehaviorService.CreatureEcoProfile(
                "wolf-scan", "wolf_predator", REGION, 10f, 0f, 10f, false, 15f));
        g.ecosystem().register(new EcosystemBehaviorService.CreatureEcoProfile(
                "rabbit-scan", "rabbit_prey", REGION, 11f, 0f, 10.5f, true, 8f));
        Map<String, Object> scan = g.ecologicalTableau().scanPredatorPrey(REGION, now);
        assertThat(scan.get("ok")).isEqualTo(true);
        // 30% 概率，多次扫描至少验证流程不报错
        Map<String, Object> rescan = g.ecologicalTableau().scanPredatorPrey(REGION, now + 700_000L);
        assertThat(rescan.get("skipped")).isNull();
    }

    @Test
    public void ecological_hiddenDigSiteAfterThreePasses() {
        long player = 190200L;
        Map<String, Object> last = Map.of();
        for (int i = 0; i < 3; i++) {
            last = g.ecologicalTableau().onPassLandmarkTree(
                    player, "ancient-oak", 300f, 0f, 300f, 15, 100 + i, now + i * 86_400_000L);
        }
        // 第 3 次路过即触发隐藏挖掘点
        assertThat(last.get("event")).isEqualTo("HIDDEN_NARRATIVE");
        assertThat(last.get("noQuestTracker")).isEqualTo(true);
        assertThat(last.get("noStatReward")).isEqualTo(true);
        assertThat(last.get("passCount")).isEqualTo(3);

        Map<String, Object> dig = g.ecologicalTableau().excavate(player, now);
        assertThat(dig.get("ok")).isEqualTo(true);
        assertThat(dig.get("item")).isEqualTo("landscape_postcard");
        Map<String, Object> again = g.ecologicalTableau().excavate(player, now);
        assertThat(again.get("error")).isEqualTo("already_excavated");
    }

    @Test
    public void ecological_weatherMemorialTriggersOnce() {
        Map<String, Object> first = g.ecologicalTableau().triggerWeatherMemorial(
                WorldTimeService.Weather.RAIN, now);
        Map<String, Object> second = g.ecologicalTableau().triggerWeatherMemorial(
                WorldTimeService.Weather.RAIN, now);
        assertThat(first.get("cutscene")).isEqualTo(true);
        assertThat(first.get("chronicleAchievement")).isEqualTo("season_first_rain");
        assertThat(second.get("skipped")).isEqualTo(true);
    }

    // ── 六、联机物理快照增强 ─────────────────────────────────────────

    @Test
    public void coopTerrain_destroyedMeshesPersistInTsv() {
        g.terrainStateVector().markMeshDestroyed(REGION, "bridge-east", now);
        g.terrainStateVector().markMeshDestroyed(REGION, "tree-north-3", now);
        Map<String, Object> tsv = g.terrainStateVector().snapshotZone(REGION);
        assertThat(tsv.get("destroyedStaticMeshUids")).asList()
                .containsExactlyInAnyOrder("bridge-east", "tree-north-3");
    }

    @Test
    public void coopTerrain_hostMigrationLoadsSnapshotAndBlocksInstantReconnect() {
        g.coopTerrainSnapshot().save(ROOM, List.of("bridge-east", "wall-cracked"), 42L, now);
        g.coopElection().registerHost(ROOM, 1000L);
        g.coopElection().onHostDisconnect(ROOM, 1000L, now);
        g.coopElection().updateCandidates(ROOM, List.of(
                new CoopRoomElectionService.Candidate(2000L, 25L, 1)));
        Map<String, Object> elect = g.coopElection().tryElect(ROOM, now + 31_000L);
        assertThat(elect.get("elected")).isEqualTo(true);
        assertThat(elect.get("rejectInstantReconnect")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> recompose = (Map<String, Object>) elect.get("worldRecompose");
        assertThat(recompose.get("uiHint")).isEqualTo("世界重组中，请稍候...");
        assertThat(recompose.get("destroyedCount")).isEqualTo(2);
        assertThat(recompose.get("meshesCulled")).isEqualTo(true);
    }

    @Test
    public void coopTerrain_incrementalMeshDestroyUpdatesSnapshot() {
        g.coopTerrainSnapshot().addDestroyedMesh(ROOM, "rock-1", 1L, now);
        g.coopTerrainSnapshot().addDestroyedMesh(ROOM, "rock-2", 2L, now + 100);
        Map<String, Object> view = g.coopTerrainSnapshot().loadView(ROOM, now + 200);
        assertThat(view.get("destroyedStaticMeshUids")).asList()
                .containsExactly("rock-1", "rock-2");
    }

    // ── 门面与状态快照 ───────────────────────────────────────────────

    @Test
    public void facade_statusOverviewContainsP19Block() {
        Map<String, Object> status = g.statusOverview();
        assertThat(status.get("p19")).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> p19 = (Map<String, Object>) status.get("p19");
        assertThat(p19.get("microPhysics")).isEqualTo(true);
        assertThat(p19.get("civilizationSchedule")).isEqualTo(true);
        assertThat(p19.get("massMomentum")).isEqualTo(true);
        assertThat(p19.get("coopTerrainSnapshot")).isEqualTo(true);
    }

    // ── helpers ─────────────────────────────────────────────────────

    private static SceneMoveCmd walkOn(long player, float x, SurfaceType surface) {
        return new SceneMoveCmd(
                x, 0f, x, 6f, System.currentTimeMillis(),
                cn.itcast.demo.mymmorpg.sync.MovementType.WALK, "",
                0, "", "", 0f, 0f, 0f, 0f, 0f, 0f, "",
                cn.itcast.demo.mymmorpg.sync.ActionState.GROUND_IDLE,
                cn.itcast.demo.mymmorpg.sync.MoveIntent.FORWARD, 0f, 16, surface);
    }

    private static double num(Map<String, Object> m, String key) {
        return ((Number) m.get(key)).doubleValue();
    }
}
