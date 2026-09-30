package cn.itcast.demo.mymmorpg.world.scene;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SceneInstancePoolTest {

    @Test
    public void allocateHeartbeatAndReclaimExpired() {
        SceneInstancePool pool = new SceneInstancePool("node-a");
        long now = 1_000_000L;
        SceneInstancePool.SceneInstance inst = pool.allocate(101, 40, 5_000L, now, "node-a");
        assertThat(inst.status()).isEqualTo(SceneInstancePool.Status.ALLOCATED);
        assertThat(inst.leaseId()).isNotBlank();
        assertThat(inst.expireAtMs()).isEqualTo(now + 5_000L);
        assertThat(pool.listActive()).hasSize(1);

        SceneInstancePool.SceneInstance active = pool.heartbeat(inst.instanceId(), now + 1_000L, 10_000L);
        assertThat(active).isNotNull();
        assertThat(active.status()).isEqualTo(SceneInstancePool.Status.ACTIVE);
        assertThat(active.expireAtMs()).isEqualTo(now + 1_000L + 10_000L);

        assertThat(pool.release(inst.instanceId())).isTrue();
        int reclaimed = pool.reclaimExpired(now + 2_000L);
        assertThat(reclaimed).isEqualTo(1);
        assertThat(pool.listActive()).isEmpty();
        assertThat(pool.stats().get("reclaimedTotal")).isEqualTo(1);
    }

    @Test
    public void reclaimWhenLeaseExpiresWithoutRelease() {
        SceneInstancePool pool = new SceneInstancePool();
        long now = 2_000_000L;
        SceneInstancePool.SceneInstance inst = pool.allocate(7, 10, 3_000L, now, "n1");
        assertThat(pool.reclaimExpired(now + 1_000L)).isZero();
        assertThat(pool.reclaimExpired(now + 3_000L)).isEqualTo(1);
        assertThat(pool.get(inst.instanceId())).isNull();
    }
}
