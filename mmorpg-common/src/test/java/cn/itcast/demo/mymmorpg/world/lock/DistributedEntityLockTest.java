package cn.itcast.demo.mymmorpg.world.lock;

import org.testng.annotations.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

public class DistributedEntityLockTest {

    @Test
    public void exclusiveOwnership() {
        DistributedEntityLock lock = new DistributedEntityLock();
        var a = lock.tryAcquire(1, 100L, 9L, Duration.ofSeconds(5));
        var b = lock.tryAcquire(1, 100L, 10L, Duration.ofSeconds(5));
        assertThat(a.acquired()).isTrue();
        assertThat(b.acquired()).isFalse();
        assertThat(lock.release(a.lockKey(), a.token())).isTrue();
        var c = lock.tryAcquire(1, 100L, 10L, Duration.ofSeconds(5));
        assertThat(c.acquired()).isTrue();
    }
}
