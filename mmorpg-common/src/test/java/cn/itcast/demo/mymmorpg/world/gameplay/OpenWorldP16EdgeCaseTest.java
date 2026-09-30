package cn.itcast.demo.mymmorpg.world.gameplay;

import cn.itcast.demo.mymmorpg.sync.NetworkEmulatorProxy;
import cn.itcast.demo.mymmorpg.sync.SceneMoveCmd;
import cn.itcast.demo.mymmorpg.sync.ServerShadowService;
import cn.itcast.demo.mymmorpg.world.content.OpenWorldConfigPatchService;
import cn.itcast.demo.mymmorpg.world.economy.AuctionHouseService;
import cn.itcast.demo.mymmorpg.world.ecosystem.EcoGraphService;
import cn.itcast.demo.mymmorpg.world.puzzle.PhysicsAuthorityService;
import cn.itcast.demo.mymmorpg.world.puzzle.TerrainTopologyGraph;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** P16 边界与负路径：权威校验失败、资源不足、阈值未达、配置未就绪等。 */
public class OpenWorldP16EdgeCaseTest {

    @Test
    public void sceneMoveCmdPhysicsHashDefaultsEmpty() {
        SceneMoveCmd cmd = SceneMoveCmd.walk(1f, 2f, 3f, 8f, 100L);
        assertThat(cmd.physicsStateHash()).isEmpty();
    }

    @Test
    public void physicsHashSkippedWithinInterval() {
        PhysicsAuthorityService auth = new PhysicsAuthorityService();
        long t0 = 10_000L;
        auth.recordExpected(1L, new PhysicsAuthorityService.ExpectedPhysics(
                1f, 0f, 0f, 1f, 0f, 1f, 0f, t0));
        Map<String, Object> first = auth.validateHash(1L, "h", 1f, 0f, 0f, 1f, 0f, 1f, 0f, t0 + 10);
        // 间隔内第二次应跳过
        Map<String, Object> second = auth.validateHash(1L, "h", 1f, 0f, 0f, 1f, 0f, 1f, 0f, t0 + 100);
        assertThat(second.get("skipped")).isEqualTo(true);
        assertThat(first.get("ok")).isEqualTo(true);
    }

    @Test
    public void destroyCliffRejectsWithoutHeavyStamina() {
        TerrainTopologyGraph g = new TerrainTopologyGraph();
        g.registerCliff(new TerrainTopologyGraph.CliffNode("c1", 1, 5f, 0f, 5f, 2f));
        Map<String, Object> r = g.validateDestroyCliff(
                9L, "c1", 0f, 0f, 0f, 1f, 0f, 1f,
                TerrainTopologyGraph.DestroyTool.HEAVY_ATTACK, 30, 1L);
        assertThat(r.get("ok")).isEqualTo(false);
        assertThat(r.get("error")).isEqualTo("heavy_stamina_exhausted");
    }

    @Test
    public void destroyCliffRejectsWithoutBomb() {
        TerrainTopologyGraph g = new TerrainTopologyGraph();
        g.registerCliff(new TerrainTopologyGraph.CliffNode("c2", 1, 5f, 0f, 5f, 2f));
        Map<String, Object> r = g.validateDestroyCliff(
                9L, "c2", 0f, 0f, 0f, 1f, 0f, 1f,
                TerrainTopologyGraph.DestroyTool.BOMB, 10, 1L);
        assertThat(r.get("ok")).isEqualTo(false);
        assertThat(r.get("error")).isEqualTo("bomb_exhausted");
    }

    @Test
    public void ecoRippleRejectedWhenAboveThreshold() {
        EcoGraphService eco = new EcoGraphService();
        eco.setStock("r1", "boar", EcoGraphService.DEFAULT_STOCK);
        Map<String, Object> r = eco.rippleImbalance("r1", "boar", 100L);
        assertThat(r.get("ok")).isEqualTo(false);
        assertThat(r.get("error")).isEqualTo("not_below_threshold");
    }

    @Test
    public void hostCannotClaimHelperBadge() {
        OpenWorldGameplayFacade g = new OpenWorldGameplayFacade();
        g.coopNarrative().registerRoom("room-x", 1L, 2L);
        Map<String, Object> r = g.coopNarrative().recordCoopAssist("room-x", 1L, "boss", 1L);
        assertThat(r.get("ok")).isEqualTo(false);
        assertThat(r.get("error")).isEqualTo("guest_required");
    }

    @Test
    public void auctionOutlierCooldownBlocksNextList() {
        AuctionHouseService ah = new AuctionHouseService();
        for (long p : List.of(100L, 110L, 105L, 108L, 102L)) {
            ah.recordTradePrice("item-z", p);
        }
        long now = 1_000L;
        Map<String, Object> first = ah.listBuyout(3L, "item-z", 1, 9_000L, now, "local", false, "扫货套利");
        assertThat(first.get("ok")).isEqualTo(true);
        Map<String, Object> second = ah.listBuyout(3L, "item-z", 1, 120L, now + 1_000L, "local", false, null);
        assertThat(second.get("ok")).isEqualTo(false);
        assertThat(second.get("error")).isEqualTo("outlier_cooldown");
    }

    @Test
    public void publishStagingFailsWhenEmpty() {
        OpenWorldConfigPatchService cfg = new OpenWorldConfigPatchService();
        Map<String, Object> r = cfg.publishStaging(1L);
        assertThat(r.get("ok")).isEqualTo(false);
        assertThat(r.get("error")).isEqualTo("staging_empty");
    }

    @Test
    public void stageRequiresGitCommitSha() {
        OpenWorldConfigPatchService cfg = new OpenWorldConfigPatchService();
        Map<String, Object> r = cfg.stagePatch("c1", "ABYSS", Map.of("a", 1), "", null);
        assertThat(r.get("ok")).isEqualTo(false);
        assertThat(r.get("error")).isEqualTo("git_commit_sha_required");
    }

    @Test
    public void reconnectWithinProtectUsesStaticResume() {
        ServerShadowService shadow = new ServerShadowService();
        long now = 50_000L;
        shadow.markDisconnect(7L, now - 5_000L);
        Map<String, Object> r = shadow.recoverWithFastForward(7L, now, List.of());
        assertThat(r.get("mode")).isEqualTo("STATIC_RESUME");
        assertThat(r.get("fastForwardApplied")).isEqualTo(false);
        assertThat(r.get("kicked")).isEqualTo(false);
    }

    @Test
    public void networkEmulatorStatsAccumulate() {
        NetworkEmulatorProxy net = new NetworkEmulatorProxy();
        net.force(NetworkEmulatorProxy.FaultKind.DROP_ACK, "a", null);
        net.force(NetworkEmulatorProxy.FaultKind.DELAY_ACK, "b", Map.of());
        net.force(NetworkEmulatorProxy.FaultKind.PASSTHROUGH, "c", Map.of());
        Map<String, Object> stats = net.stats();
        assertThat(stats.get("dropped")).isEqualTo(1);
        assertThat(stats.get("delayed")).isEqualTo(1);
        assertThat(stats.get("passed")).isEqualTo(1);
    }

    @Test
    public void spawnWeightDropsWhenPreyScarce() {
        EcoGraphService eco = new EcoGraphService();
        eco.setStock("r1", "boar", 2);
        eco.setStock("r1", "wolf", 20);
        Map<String, Object> hint = eco.spawnWeightHint("r1", "wolf");
        assertThat(((Number) hint.get("spawnWeight")).doubleValue()).isLessThan(1.0);
    }
}
