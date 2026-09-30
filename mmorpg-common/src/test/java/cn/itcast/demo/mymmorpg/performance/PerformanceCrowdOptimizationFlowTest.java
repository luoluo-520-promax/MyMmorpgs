package cn.itcast.demo.mymmorpg.performance;

import cn.itcast.demo.mymmorpg.cache.RedisPositionBatchWriter;
import cn.itcast.demo.mymmorpg.ecs.SceneComponentStore;
import cn.itcast.demo.mymmorpg.ecs.SceneTickMicroPipeline;
import cn.itcast.demo.mymmorpg.sync.BroadcastImportanceFuseService;
import cn.itcast.demo.mymmorpg.aoi.AoiBroadcastStrategy;
import cn.itcast.demo.mymmorpg.world.battle.CombatEventRingBuffer;
import cn.itcast.demo.mymmorpg.world.battle.DamageEvent;
import cn.itcast.demo.mymmorpg.world.battle.DualClockService;
import cn.itcast.demo.mymmorpg.world.battle.ReactionValidator;
import cn.itcast.demo.mymmorpg.world.gameplay.OpenWorldGameplayFacade;
import cn.itcast.demo.mymmorpg.world.puzzle.TerrainStateVector;
import cn.itcast.demo.mymmorpg.world.scene.BattleScenePodAllocator;
import org.springframework.beans.factory.ObjectProvider;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 人群拥挤 / 战斗手感 / 分布式架构优化回归。
 */
public class PerformanceCrowdOptimizationFlowTest {

    @Test
    public void microPipeline_separatesMovementLogicSync() {
        SceneTickMicroPipeline pipeline = new SceneTickMicroPipeline();
        SceneComponentStore store = new SceneComponentStore();
        store.allocate(1L, 0f, 0f, 0f, 100);
        store.setVelocity(1L, 10f, 0f, 0f);

        long now = System.currentTimeMillis();
        assertThat(pipeline.tickMovement(store, now).get("pipeline")).isEqualTo("MOVEMENT");
        assertThat(pipeline.tickLogic(store.entityIds(), (id, ts) -> {}, now).get("pipeline"))
                .isEqualTo("LOGIC");
        assertThat(pipeline.tickSync(store.entityIds(), (id, ts) -> {}, now).get("pipeline"))
                .isEqualTo("SYNC");
    }

    @Test
    public void broadcastFuse_downgradesNonCombatWhenOverBandwidth() {
        BroadcastImportanceFuseService fuse = new BroadcastImportanceFuseService();
        fuse.configure(64_000L);
        fuse.recordOutboundBytes(80_000, System.currentTimeMillis());
        fuse.markCombat(99L, true);

        var nonCombat = fuse.decide(1L, 15f, AoiBroadcastStrategy.FrequencyTier.NEAR_20HZ,
                System.currentTimeMillis());
        assertThat(fuse.isFused()).isTrue();
        assertThat(nonCombat.positionOnly()).isTrue();
        assertThat(nonCombat.effectiveTier()).isEqualTo(AoiBroadcastStrategy.FrequencyTier.FAR_2HZ);

        var combat = fuse.decide(99L, 15f, AoiBroadcastStrategy.FrequencyTier.NEAR_20HZ,
                System.currentTimeMillis());
        assertThat(combat.positionOnly()).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void redisPositionWriter_skipsStationaryPlayers() {
        ObjectProvider provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        RedisPositionBatchWriter writer = new RedisPositionBatchWriter(provider);
        long now = System.currentTimeMillis();

        assertThat(writer.shouldWrite(1L, 10f, 0f, 20f, now)).isTrue();
        writer.enqueue(1L, 10f, 0f, 20f, now);
        assertThat(writer.shouldWrite(1L, 10f, 0f, 20f, now + 100)).isFalse();
        assertThat(writer.shouldWrite(1L, 10f, 0f, 20f, now + 4000)).isFalse();
        assertThat(writer.shouldWrite(1L, 11f, 0f, 20f, now + 4000)).isTrue();
    }

    @Test
    public void reactionValidator_clientTimestampBufferAllowsLatePacket() {
        ReactionValidator v = new ReactionValidator();
        long openAt = 1_000_000L;
        v.openAttackWindow("late-dodge", 1L, openAt, 200, 180, 80);
        long clientTs = openAt + 280;
        Map<String, Object> ok = v.validate(
                ReactionValidator.ReactionKind.PERFECT_DODGE, "b1", 42L,
                "late-dodge", clientTs, clientTs + 120);
        assertThat(ok.get("ok")).isEqualTo(true);
        assertThat(ok.get("clientTimestampBacked")).isEqualTo(true);
    }

    @Test
    public void terrainStateVector_optimisticReadAndDeferredWrite() {
        TerrainStateVector tsv = new TerrainStateVector();
        long now = System.currentTimeMillis();
        var scheduled = tsv.scheduleBump("region-a", 1, 2, "DESTROY_CLIFF", now);
        assertThat(scheduled.get("visualImmediate")).isEqualTo(true);
        assertThat(tsv.validateMoveRevision("region-a", 1, 2, 0L).get("optimisticRead"))
                .isEqualTo(true);
        assertThat(tsv.flushPendingWrites(now + TerrainStateVector.TERRAIN_WRITE_DELAY_MS + 1))
                .isEqualTo(1);
        assertThat(tsv.revisionOf("region-a", 1, 2)).isGreaterThan(0L);
    }

    @Test
    public void dualClock_skillCdUsesWallTimeDuringBulletTime() {
        DualClockService clock = new DualClockService();
        long now = System.currentTimeMillis();
        clock.startSkillCooldown(1L, 0, 1000, 0.1, now);
        assertThat(clock.isSkillReadyWall(1L, 0, now + 500)).isFalse();
        assertThat(clock.isSkillReadyGame(1L, 0, now + 500)).isFalse();
        assertThat(clock.isSkillReadyWall(1L, 0, now + 1100)).isTrue();
        assertThat(clock.skillRemainWallMs(1L, 0, now + 200)).isGreaterThan(700L);
    }

    @Test
    public void combatEventRingBuffer_zeroGarbagePath() {
        CombatEventRingBuffer ring = new CombatEventRingBuffer(16);
        DamageEvent e = ring.publishDamage(
                1L, 2L, 100, 80, DamageEvent.VERDICT_HIT,
                DamageEvent.ELEMENT_PYRO, 10, System.currentTimeMillis());
        assertThat(e.active()).isTrue();
        assertThat(e.finalDamage()).isEqualTo(80);
        assertThat(ring.acquireProjectileSlot()).isZero();
        for (int i = 0; i < 250; i++) {
            ring.acquireProjectileSlot();
        }
        assertThat(ring.stats().get("projectileRecycled")).isNotNull();
    }

    @Test
    public void battleScenePodAllocator_isolatesBattleFromOpenWorld() {
        BattleScenePodAllocator alloc = new BattleScenePodAllocator();
        long now = System.currentTimeMillis();
        var ow = alloc.registerOpenWorld(1, 200, now);
        var battle = alloc.allocateBattlePod(9001, false, 600_000L, now);
        assertThat(ow.sceneClass()).isEqualTo(BattleScenePodAllocator.SceneClass.OPEN_WORLD);
        assertThat(battle.sceneClass()).isEqualTo(BattleScenePodAllocator.SceneClass.BATTLE_INSTANCE);
        assertThat(battle.memorySnapshotOnly()).isTrue();
        assertThat(battle.maxPlayers()).isLessThanOrEqualTo(4);
        assertThat(alloc.admitPlayer(battle.podId())).isTrue();
    }

    @Test
    public void openWorldFacade_exposesOptimizationServices() {
        OpenWorldGameplayFacade facade = new OpenWorldGameplayFacade();
        assertThat(facade.dualClock()).isNotNull();
        assertThat(facade.combatEvents()).isNotNull();
        assertThat(facade.battleScenePods()).isNotNull();
    }
}
