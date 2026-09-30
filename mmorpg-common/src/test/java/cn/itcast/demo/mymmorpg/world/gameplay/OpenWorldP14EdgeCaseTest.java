package cn.itcast.demo.mymmorpg.world.gameplay;

import cn.itcast.demo.mymmorpg.sync.MovementType;
import cn.itcast.demo.mymmorpg.sync.SceneMoveCmd;
import cn.itcast.demo.mymmorpg.sync.ServerShadowService;
import cn.itcast.demo.mymmorpg.sync.SnapshotBuffer;
import cn.itcast.demo.mymmorpg.world.battle.CombatAssistService;
import cn.itcast.demo.mymmorpg.world.endgame.RogueFateCardService;
import cn.itcast.demo.mymmorpg.world.explore.CollectibleService;
import cn.itcast.demo.mymmorpg.world.explore.RegionImpactService;
import cn.itcast.demo.mymmorpg.world.puzzle.DeterministicMutationService;
import cn.itcast.demo.mymmorpg.world.puzzle.WorldMutabilityService;
import cn.itcast.demo.mymmorpg.world.social.PhantomBorrowService;
import cn.itcast.demo.mymmorpg.world.social.SocialTokenService;
import cn.itcast.demo.mymmorpg.world.traverse.TraverseMomentumService;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** P14 边界与负路径：七大短板补齐的异常分支与一致性校验。 */
public class OpenWorldP14EdgeCaseTest {

    @Test
    public void serverShadowRejectsTeleportButAllowsLaggyMove() {
        SnapshotBuffer buf = SnapshotBuffer.forLagCompensation();
        long t0 = System.currentTimeMillis() - 400;
        buf.push(t0, 0f, 0f, 0f, 12f);
        buf.push(t0 + 200, 6f, 0f, 0f, 12f);
        assertThat(buf.validateWithLagCompensation(t0 + 200, 8f, 0f, 0f, 12f, 3f)).isTrue();
        assertThat(buf.validateWithLagCompensation(t0 + 200, 500f, 0f, 0f, 12f, 3f)).isFalse();
    }

    @Test
    public void sceneMoveCmdBackwardCompatibleWithoutInheritedVelocity() {
        SceneMoveCmd cmd = SceneMoveCmd.walk(1f, 2f, 3f, 8f, 100L);
        assertThat(cmd.inheritedVelocityX()).isEqualTo(0f);
        assertThat(cmd.inheritedVelocityY()).isEqualTo(0f);
        assertThat(cmd.inheritedVelocityZ()).isEqualTo(0f);
    }

    @Test
    public void traverseMomentumSwingDamageCapsAtReasonableMultiplier() {
        TraverseMomentumService m = new TraverseMomentumService();
        double mult = m.swingAttackDamageMultiplier(40f, 8f);
        assertThat(mult).isGreaterThan(1.0);
        assertThat(mult).isLessThanOrEqualTo(2.5);
    }

    @Test
    public void terrainClientCacheHitSkipsServerRoundTrip() {
        WorldMutabilityService.TerrainInteractionTracker tracker =
                new WorldMutabilityService.TerrainInteractionTracker();
        long now = System.currentTimeMillis();
        tracker.tryConsume("r1", 10, 20, WorldMutabilityService.TerrainKind.BOUNCE_MUSHROOM, 60_000L, now);
        long until = now + 50_000L;
        Map<String, Object> cached = tracker.clientCacheCheck("r1", 10, 20, until, now + 1000);
        assertThat(cached.get("clientCacheHit")).isEqualTo(true);
        assertThat(cached.get("error")).isEqualTo("TERRAIN_EXHAUSTED");
    }

    @Test
    public void deterministicMutationSameSeedProducesSameFragmentCount() {
        DeterministicMutationService svc = new DeterministicMutationService();
        long now = 1_700_000_000_000L;
        Map<String, Object> a = svc.issueLocalPlayback("tree-fixed", now, 120_000L);
        Map<String, Object> b = svc.issueLocalPlayback("tree-fixed", now, 120_000L);
        assertThat(a.get("fragmentCount")).isEqualTo(b.get("fragmentCount"));
        assertThat(a.get("seed")).isEqualTo(b.get("seed"));
    }

    @Test
    public void optimisticTerrainRollbackWhenInvalid() {
        DeterministicMutationService svc = new DeterministicMutationService();
        Map<String, Object> r = svc.optimisticTerrain("pool-1", false, System.currentTimeMillis());
        assertThat(r.get("smoothRevert")).isEqualTo(true);
        assertThat(r.get("rollbackAnimMs")).isEqualTo(1000);
    }

    @Test
    public void vitalitySurveyNotFoundAfterComplete() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long playerId = 901L;
        Map<String, Object> daily = g.explorationVitality().refreshDaily(playerId, "wolf-camp-valley", System.currentTimeMillis());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> points = (List<Map<String, Object>>) daily.get("surveyPoints");
        String pointId = String.valueOf(points.get(0).get("pointId"));
        assertThat(g.explorationVitality().completeSurvey(playerId, pointId, System.currentTimeMillis()).get("ok"))
                .isEqualTo(true);
        assertThat(g.explorationVitality().completeSurvey(playerId, pointId, System.currentTimeMillis()).get("error"))
                .isEqualTo("survey_not_found");
    }

    @Test
    public void oculiResonanceRequiresSeventyPercentNotHundred() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        for (int i = 0; i < 9; i++) {
            g.collectibles().register(new CollectibleService.CollectibleDef(
                    "oc-test-" + i, "瞳" + i, CollectibleService.Tier.OCULUS,
                    1, 100f + i, 10f, 100f, 5f, "oculus_fragment", 1, 1, 5));
        }
        long playerId = 902L;
        for (int i = 0; i < 7; i++) {
            g.collectibles().collect(playerId, "oc-test-" + i, 100f + i, 10f, 100f);
        }
        Map<String, Object> resonance = g.oculiResonance().evaluateResonance(playerId, 1, System.currentTimeMillis());
        assertThat(resonance.get("resonanceTriggered")).isEqualTo(true);
        assertThat(resonance.get("event")).isEqualTo("OCULI_RESONANCE_PULSE");

        for (int i = 7; i < 9; i++) {
            g.collectibles().collect(playerId, "oc-test-" + i, 100f + i, 10f, 100f);
        }
        Map<String, Object> full = g.oculiResonance().evaluateResonance(playerId, 1, System.currentTimeMillis() + 1);
        assertThat(full.get("resonanceTriggered")).isEqualTo(false);
    }

    @Test
    public void phantomBorrowRequiresThreeFails() {
        PhantomBorrowService phantom = new PhantomBorrowService();
        phantom.recordPhantom("puzzle-x", 100L, List.of("PRESS", "ROTATE", "ACTIVATE"));
        long playerId = 903L;
        assertThat(phantom.onPuzzleFail(playerId, "puzzle-x").get("borrowAvailable")).isEqualTo(false);
        phantom.onPuzzleFail(playerId, "puzzle-x");
        assertThat(phantom.onPuzzleFail(playerId, "puzzle-x").get("borrowAvailable")).isEqualTo(true);
        Map<String, Object> borrow = phantom.borrowPhantom(playerId, "puzzle-x", 100L);
        assertThat(borrow.get("event")).isEqualTo("PHANTOM_BORROW_EXECUTE");
    }

    @Test
    public void socialTokenInsufficientBlocksCosmetic() {
        SocialTokenService token = new SocialTokenService();
        assertThat(token.redeemCosmetic(1L, "wing_cosmetic").get("error")).isEqualTo("insufficient_tokens");
        token.grantAssist(1L, 2L, "ascension_help");
        assertThat(token.balanceOf(1L)).isEqualTo(SocialTokenService.TOKEN_PER_ASSIST);
    }

    @Test
    public void coopCampRejectsUnsafeRegion() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> fail = g.coopCamp().establish(
                1L, List.of(), "wolf-camp-valley", false, 0f, 0f, 0f, System.currentTimeMillis());
        assertThat(fail.get("error")).isEqualTo("region_not_safe");
    }

    @Test
    public void macroComboRateLimited() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        long playerId = 904L;
        long now = System.currentTimeMillis();
        assertThat(g.buildRecommend().saveMacroCombo(playerId, 0, List.of("SKILL_A", "SKILL_B"), now).get("ok"))
                .isEqualTo(true);
        assertThat(g.buildRecommend().triggerMacro(playerId, 0, now).get("ok")).isEqualTo(true);
        Map<String, Object> limited = g.buildRecommend().triggerMacro(playerId, 0, now + 500);
        assertThat(limited.get("error")).isEqualTo("macro_rate_limited");
    }

    @Test
    public void pcDeviceGetsLootBonusMobileGetsSoftLock() {
        CombatAssistService assist = new CombatAssistService();
        Map<String, Object> pc = assist.registerDevice(1L, CombatAssistService.DeviceType.PC);
        assertThat(pc.get("lootWeightBonus")).isEqualTo(0.05f);
        assist.registerDevice(2L, CombatAssistService.DeviceType.MOBILE);
        Map<String, Object> lock = assist.resolveSoftLock(2L, 0f, 10f, 15f);
        assertThat(lock.get("softLockEnabled")).isEqualTo(true);
        assertThat((Float) lock.get("correctionDeg")).isLessThanOrEqualTo(15f);
    }

    @Test
    public void fateEchoExchangeAndBossAwakenOnRegionSafe() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        RegionImpactService regions = g.regions();
        regions.register(new RegionImpactService.RegionProfile(
                "rogue-region", "肉鸽区", 1, 5, List.of()));
        regions.forceSafety("rogue-region", RegionImpactService.RegionSafety.CHAOS);
        for (int i = 0; i < 5; i++) {
            regions.addTidePurify("rogue-region", 30, System.currentTimeMillis());
        }
        Map<String, Object> start = g.rogueFate().startWithRegion(905L, 1, "rogue-region", System.currentTimeMillis());
        assertThat(start.get("ok")).isEqualTo(true);
        Map<String, Object> settle = g.rogueFate().settleClear(905L, true, System.currentTimeMillis());
        assertThat(settle.get("fateEchoEarned")).isEqualTo(10);
        Map<String, Object> exchange = g.rogueFate().exchangeFateEcho(905L, "CRIT_RATE", 10);
        assertThat(exchange.get("ok")).isEqualTo(true);
    }

    @Test
    public void softRollbackPreservesPositionFlag() {
        ServerShadowService shadow = new ServerShadowService();
        long ts = System.currentTimeMillis();
        shadow.recordPosition(1L, ts, 0f, 0f, 0f, 10f);
        Map<String, Object> decision = shadow.softRollbackDecision(
                1L, ts, 2f, 0f, 0f, 10f, false, true);
        assertThat(decision.get("keepVisuals")).isEqualTo(true);
        assertThat(decision.get("rubberBand")).isEqualTo(false);
        assertThat(decision.get("rollbackDamage")).isEqualTo(true);
    }

    @Test
    public void glideToGrappleTransitionPreservesPartialMomentum() {
        TraverseMomentumService m = new TraverseMomentumService();
        var v = new TraverseMomentumService.VelocityVector(12f, 5f, 8f, 14f);
        var after = m.inheritOnTransition(MovementType.GRAPPLE, MovementType.WALK, v);
        assertThat(after.vy()).isEqualTo(2.5f);
    }
}
