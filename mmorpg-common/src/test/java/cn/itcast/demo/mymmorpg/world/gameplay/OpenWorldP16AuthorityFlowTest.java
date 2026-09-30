package cn.itcast.demo.mymmorpg.world.gameplay;

import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.sync.NetworkEmulatorProxy;
import cn.itcast.demo.mymmorpg.sync.ServerShadowService;
import cn.itcast.demo.mymmorpg.world.battle.CombatAssistService;
import cn.itcast.demo.mymmorpg.world.battle.HitFeedbackService;
import cn.itcast.demo.mymmorpg.world.battle.PrePlaybackService;
import cn.itcast.demo.mymmorpg.world.content.OpenWorldConfigPatchService;
import cn.itcast.demo.mymmorpg.world.explore.CollectibleService;
import cn.itcast.demo.mymmorpg.world.puzzle.PhysicsAuthorityService;
import cn.itcast.demo.mymmorpg.world.puzzle.TerrainTopologyGraph;
import cn.itcast.demo.mymmorpg.world.traverse.StaminaConsumeService;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** P16：物理权威 / 生态图 / 多端瞄准 / 热力调度 / 联机叙事 / 伙伴幽灵 / 混沌重连 / 拍卖稳定 / 预测补偿 / 配置双缓冲。 */
public class OpenWorldP16AuthorityFlowTest {

    @Test
    public void physicsHashSoftPullbackWhenGravityTampered() {
        PhysicsAuthorityService auth = new PhysicsAuthorityService();
        long pid = 1601L;
        long now = 1_000L;
        auth.recordExpected(pid, new PhysicsAuthorityService.ExpectedPhysics(
                5f, 0f, 2f, 1.0f, 0f, 1f, 0f, now));
        String goodHash = PhysicsAuthorityService.computeHash(5f, 0f, 2f, 1.0f, 0f, 1f, 0f);
        Map<String, Object> ok = auth.validateHash(pid, goodHash, 5f, 0f, 2f, 1.0f, 0f, 1f, 0f, now + 600);
        assertThat(ok.get("softPullback")).isEqualTo(false);

        Map<String, Object> bad = auth.validateHash(pid, "deadbeef", 5f, 0f, 2f, 3.5f, 0f, 1f, 0f, now + 1200);
        assertThat(bad.get("softPullback")).isEqualTo(true);
        assertThat(bad.get("correctGravity")).isEqualTo(1.0f);
    }

    @Test
    public void terrainDestroyCliffRequiresRayAndConsumesBomb() {
        TerrainTopologyGraph g = new TerrainTopologyGraph();
        g.registerCliff(new TerrainTopologyGraph.CliffNode("cliff-a", 1, 10f, 0f, 10f, 2f));
        g.grantBomb(42L, 1);
        Map<String, Object> miss = g.validateDestroyCliff(
                42L, "cliff-a", 0f, 0f, 0f, 0f, 0f, -1f,
                TerrainTopologyGraph.DestroyTool.BOMB, 10, 100L);
        assertThat(miss.get("ok")).isEqualTo(false);

        Map<String, Object> hit = g.validateDestroyCliff(
                42L, "cliff-a", 0f, 0f, 0f, 1f, 0f, 1f,
                TerrainTopologyGraph.DestroyTool.BOMB, 10, 100L);
        assertThat(hit.get("ok")).isEqualTo(true);
        assertThat(hit.get("causalChain")).isEqualTo(true);
        assertThat(g.bombCount(42L)).isEqualTo(0);
    }

    @Test
    public void ecoGraphRippleShiftsTugOfWar() {
        OpenWorldGameplayFacade facade = new OpenWorldGameplayFacade();
        facade.ecoGraph().setStock("wolf-camp-valley", "boar", 2);
        Map<String, Object> ripple = facade.ecoGraph().rippleImbalance("wolf-camp-valley", "boar", 2000L);
        assertThat(ripple.get("ok")).isEqualTo(true);
        assertThat(ripple.get("hungryPredators")).asList().contains("wolf");
        assertThat(ripple.get("tugOfWarSeed")).isNotNull();
    }

    @Test
    public void mobileHitboxScaleAndAimAssist() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.combatAssist().registerDevice(77L, CombatAssistService.DeviceType.MOBILE);
        assertThat(g.combatAssist().hitboxScale(77L)).isEqualTo(1.15f);
        Map<String, Object> aim = g.combatAssist().predictiveAimAssist(77L, 0f, 20f, 1f, 0f, 12f);
        assertThat(aim.get("suggestedTargetAngle")).isNotNull();
        assertThat(aim.get("serverDoesNotForceAim")).isEqualTo(true);
    }

    @Test
    public void coldSpotLootLeverAndBalancer() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> dens = g.explorationBalancer().heatDensityCoefficient("r1", 0, 0, 3000L);
        assertThat(dens.get("coldSpot")).isEqualTo(true);
        assertThat(dens.get("heatDensityCoefficient")).isEqualTo(1.5);
        assertThat(dens.get("rarityBoostLabel")).isEqualTo("稀有度提升");
        assertThat(dens.get("msgId")).isEqualTo(MessageId.EXPLORE_LOOT_RARITY_BOOST_SC_NOTIFY);

        CollectibleService.DynamicLootTier tier = new CollectibleService.DynamicLootTier(4, 0.5f, 1.5);
        assertThat(tier.countMultiplier()).isGreaterThan(new CollectibleService.DynamicLootTier(4, 0.5f).countMultiplier());

        Map<String, Object> bal = g.explorationBalancer().balance(
                List.of(Map.of("regionId", "r1", "gridX", 0, "gridZ", 0)), 3000L);
        assertThat(bal.get("compensateCount")).isEqualTo(1);
    }

    @Test
    public void coopNarrativeGuestGetsWorldShiftShield() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.coopNarrative().registerRoom("room-1", 1L, 2L);
        Map<String, Object> host = g.coopNarrative().onStoryInstanceStart("room-1", 1L, "story-a", 4000L);
        assertThat(host.get("loadDialogueUi")).isEqualTo(true);
        Map<String, Object> guest = g.coopNarrative().onStoryInstanceStart("room-1", 2L, "story-a", 4000L);
        assertThat(guest.get("effect")).isEqualTo("WORLD_SHIFT_SHIELD");
        assertThat(guest.get("loadDialogueUi")).isEqualTo(false);
        Map<String, Object> badge = g.coopNarrative().recordCoopAssist("room-1", 2L, "boss-a", 4100L);
        assertThat(badge.get("badge")).isEqualTo("助人之证");
    }

    @Test
    public void companionGhostPathAndEncourageBuff() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        StaminaConsumeService stam = g.stamina();
        stam.consumeAllowNegative(88L, 200f); // 耗尽
        g.companionGhost().spawn(88L, 88001L, 0f, 0f, 0f);
        Map<String, Object> path = g.companionGhost().issueFollowPath(88001L, 10f, 0f, 10f, 5000L);
        assertThat(path.get("followPathSmoothing")).isEqualTo(true);
        assertThat(path.get("msgId")).isEqualTo(MessageId.COMPANION_PATH_NODES_SC_NOTIFY);
        Map<String, Object> climb = g.companionGhost().consumeTraverse(
                88001L, cn.itcast.demo.mymmorpg.sync.MovementType.CLIMB, 20f, 5100L);
        assertThat(climb.get("encourage")).isEqualTo(true);
        assertThat(climb.get("buff")).isEqualTo("COMPANION_ENCOURAGE");
    }

    @Test
    public void networkEmulatorAndShadowFastForward() {
        NetworkEmulatorProxy net = new NetworkEmulatorProxy();
        var dropped = net.force(NetworkEmulatorProxy.FaultKind.DROP_ACK, "move-1", Map.of("ack", true));
        assertThat(dropped.delivered()).isFalse();
        var delayed = net.force(NetworkEmulatorProxy.FaultKind.DELAY_ACK, "move-2", Map.of("ack", true));
        assertThat(delayed.delayMs()).isEqualTo(2_000L);

        ServerShadowService shadow = new ServerShadowService();
        long now = 10_000L;
        shadow.markDisconnect(99L, now - 40_000L);
        Map<String, Object> recover = shadow.recoverWithFastForward(99L, now, List.of(
                new ServerShadowService.PredictedActionFrame("a1", "DODGE", 1f, 0f, 1f, 8f, now - 200)));
        assertThat(recover.get("fastForwardApplied")).isEqualTo(true);
        assertThat(recover.get("kicked")).isEqualTo(false);
        assertThat(recover.get("mode")).isEqualTo("FAST_FORWARD_RECONCILE");
    }

    @Test
    public void auctionPriceStabilityAndCrossShardFee() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        for (long p : List.of(100L, 110L, 105L, 108L, 102L)) {
            g.auctionHouse().recordTradePrice("relic_blank", p);
        }
        Map<String, Object> reject = g.auctionHouse().listBuyout(
                1L, "relic_blank", 1, 10_000L, 6000L, "shard-a", true, null);
        assertThat(reject.get("ok")).isEqualTo(false);
        assertThat(reject.get("error")).isEqualTo("price_reason_required");

        Map<String, Object> ok = g.auctionHouse().listBuyout(
                1L, "relic_blank", 1, 10_000L, 6000L, "shard-a", true, "稀有胚子跨服寄售");
        assertThat(ok.get("ok")).isEqualTo(true);
        assertThat(ok.get("feeRate")).isEqualTo(0.10);
        assertThat(ok.get("crossShard")).isEqualTo(true);
    }

    @Test
    public void softRollbackEmitsCompensateEffectAndVerdict() {
        PrePlaybackService pb = new PrePlaybackService();
        long now = 7000L;
        pb.onActionStart(5L, "slash-1", now);
        Map<String, Object> soft = pb.confirmOrRollback(5L, "slash-1", now, now + 10, false, true);
        assertThat(soft.get("predictionVerdict")).isEqualTo("SOFT_ROLLBACK");
        assertThat(soft.get("compensateEffectId")).isEqualTo("scratch_spark");

        HitFeedbackService hf = new HitFeedbackService();
        Map<String, Object> fb = hf.buildFeedback(5L, 6L, 10, false, 0.5f,
                HitFeedbackService.PredictionVerdict.SOFT_ROLLBACK, null, 1.15f);
        assertThat(fb.get("predictionVerdict")).isEqualTo("SOFT_ROLLBACK");
        assertThat(fb.get("compensateEffectId")).isEqualTo("scratch_spark");
        assertThat(fb.get("hitBoxScale")).isEqualTo(1.15f);
    }

    @Test
    public void configDoubleBufferKeepsInstanceSnapshot() {
        OpenWorldConfigPatchService cfg = new OpenWorldConfigPatchService();
        cfg.upsert("cell-1", "ABYSS", Map.of("hp", 100));
        Map<String, Object> snap = cfg.snapshotForInstance("inst-1", 8000L);
        assertThat(snap.get("ok")).isEqualTo(true);

        Map<String, Object> staged = cfg.stagePatch("cell-1", "ABYSS", Map.of("hp", 999),
                "abcdef0123456789", "cfg-abyss-v2");
        assertThat(staged.get("staged")).isEqualTo(true);
        cfg.publishStaging(8100L);

        Map<String, Object> fromSnap = cfg.resolveForInstance("inst-1", "cell-1");
        assertThat(fromSnap.get("fromSnapshot")).isEqualTo(true);
        assertThat(((Map<?, ?>) fromSnap.get("payload")).get("hp")).isEqualTo(100);

        Map<String, Object> current = cfg.getCell("cell-1");
        assertThat(current.get("payload")).isEqualTo(Map.of("hp", 999));
    }

    @Test
    public void facadeStatusExposesP16Flags() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        Map<String, Object> status = g.statusOverview();
        assertThat(status.get("p16")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> p16 = (Map<String, Object>) status.get("p16");
        assertThat(p16.get("physicsAuthority")).isEqualTo(true);
        assertThat(p16.get("ecoGraph")).isEqualTo(true);
    }
}
