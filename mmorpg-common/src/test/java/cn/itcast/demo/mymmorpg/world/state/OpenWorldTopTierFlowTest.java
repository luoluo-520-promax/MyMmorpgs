package cn.itcast.demo.mymmorpg.world.state;

import cn.itcast.demo.mymmorpg.world.level.WorldLevelManager;
import cn.itcast.demo.mymmorpg.world.boss.BossRespawnTimer;
import cn.itcast.demo.mymmorpg.world.lock.PartyEntityOwnership;
import cn.itcast.demo.mymmorpg.world.loot.LootOwnershipPolicy;
import cn.itcast.demo.mymmorpg.world.portal.PortalConfig;
import cn.itcast.demo.mymmorpg.world.portal.PortalPreloadService;
import cn.itcast.demo.mymmorpg.world.reconnect.ReconnectProtectionService;
import cn.itcast.demo.mymmorpg.world.resource.RespawnPoint;
import cn.itcast.demo.mymmorpg.world.resource.WorldResourceService;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 大世界顶尖标准 P0：WorldState / WorldLevel / Boss锁 / 采集同步 / 联机物权 / Portal 预加载。
 */
public class OpenWorldTopTierFlowTest {

    @Test
    public void worldStatePuzzleAndHostWorld() {
        WorldStateService state = new WorldStateService();
        state.bindHostWorld(new HostWorldContext(9L, 1, 5, 40, List.of(9L, 10L), true));
        assertThat(state.hostWorldOf(9L).effectiveWorldLevelFor(10L, 8)).isEqualTo(5);
        Map<String, Object> bit = state.setPuzzleBit(1, 9L, 3, true);
        assertThat(bit.get("ok")).isEqualTo(true);
        assertThat(state.getPuzzleBit(1, 9L, 3)).isTrue();
        assertThat(state.snapshot(1, 9L).get("ok")).isEqualTo(true);
    }

    @Test
    public void worldLevelScalesMonsterStats() {
        WorldLevelManager mgr = new WorldLevelManager();
        mgr.setWorldLevel(1, 0L, 6);
        var scaled = mgr.scaleForSpawn(1, 0L, 3, 100, 1000);
        assertThat(scaled.scaledAtk()).isGreaterThan(100);
        assertThat(scaled.scaledHp()).isGreaterThan(1000);
    }

    @Test
    public void bossRespawnGlobalLockPreventsDoubleKill() {
        BossRespawnTimer timer = new BossRespawnTimer();
        long now = System.currentTimeMillis();
        Map<String, Object> first = timer.markKilled("wb-1", 1L, 1, 60, now);
        Map<String, Object> second = timer.markKilled("wb-1", 2L, 2, 60, now);
        assertThat(first.get("ok")).isEqualTo(true);
        assertThat(second.get("ok")).isEqualTo(false);
        assertThat(timer.canSpawn("wb-1", now)).isFalse();
    }

    @Test
    public void gatherSyncModesWorldSharedVsPerPlayer() {
        WorldResourceService resources = new WorldResourceService();
        resources.registerPoint(new RespawnPoint(
                "shared-1", 1, 1, RespawnPoint.RespawnKind.GATHER,
                0, 0, 0, 1, 300, false, RespawnPoint.SyncMode.WORLD_SHARED));
        resources.registerPoint(new RespawnPoint(
                "solo-1", 1, 1, RespawnPoint.RespawnKind.GATHER,
                0, 0, 0, 2, 300, false, RespawnPoint.SyncMode.PER_PLAYER));
        long now = System.currentTimeMillis();
        assertThat(resources.collect("shared-1", 1L, now).get("ok")).isEqualTo(true);
        assertThat(resources.collect("shared-1", 2L, now).get("ok")).isEqualTo(false);
        assertThat(resources.collect("solo-1", 1L, now).get("ok")).isEqualTo(true);
        assertThat(resources.collect("solo-1", 2L, now).get("ok")).isEqualTo(true);
    }

    @Test
    public void partyLootAndThreatOwnership() {
        var decision = LootOwnershipPolicy.decide(
                LootOwnershipPolicy.SyncMode.PARTY_SHARED, 2L, 1L, Set.of(1L, 2L, 3L));
        assertThat(decision.allowed()).isTrue();
        assertThat(LootOwnershipPolicy.canPickup(decision, 3L)).isTrue();

        PartyEntityOwnership ownership = new PartyEntityOwnership();
        Map<String, Object> claim = ownership.tryClaim(
                1, 5001L, 2L, 1L, Set.of(1L, 2L),
                PartyEntityOwnership.ThreatPriority.HOST, Duration.ofSeconds(30));
        assertThat(claim.get("ok")).isEqualTo(true);
        assertThat(ownership.resolveThreatTarget(1, 5001L, 2L, Map.of(2L, 10L, 1L, 5L))).isEqualTo(1L);
    }

    @Test
    public void portalPreloadIssuesSeamlessTicket() {
        PortalPreloadService portals = new PortalPreloadService();
        portals.register(new PortalConfig(
                "p1", 1, 2, 1, 100f, 0f, 100f, 8f, 50f, 1f, 0f, 1f, "scene-local"));
        Map<String, Object> lease = portals.onPlayerMove(
                7L, 1, 100f, 0f, 100f, 1f, 0f, 90f, System.currentTimeMillis());
        assertThat(lease.get("ok")).isEqualTo(true);
        assertThat(lease.get("sessionTicket")).isNotNull();
        assertThat(String.valueOf(lease.get("status"))).isEqualTo("READY");
    }

    @Test
    public void reconnectProtectionThirtySeconds() {
        ReconnectProtectionService protection = new ReconnectProtectionService();
        protection.configure(30_000L);
        long now = System.currentTimeMillis();
        protection.grant(88L, now);
        assertThat(protection.isProtected(88L, now + 1_000L)).isTrue();
        assertThat(protection.isProtected(88L, now + 31_000L)).isFalse();
    }
}
