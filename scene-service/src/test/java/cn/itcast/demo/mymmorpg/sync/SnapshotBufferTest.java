package cn.itcast.demo.mymmorpg.sync;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SnapshotBufferTest {

    @Test
    public void rewindAndLagCompensation() {
        SnapshotBuffer buf = SnapshotBuffer.forLagCompensation();
        long t0 = System.currentTimeMillis() - 200;
        buf.push(t0, 0, 0, 0, 10);
        buf.push(t0 + 100, 5, 0, 0, 10);
        SnapshotBuffer.PosSnapshot past = buf.rewindTo(t0 + 50);
        assertThat(past).isNotNull();
        assertThat(buf.validateWithLagCompensation(t0, 2, 0, 0, 120, 5)).isTrue();
        assertThat(buf.validateWithLagCompensation(t0, 5000, 0, 0, 10, 0)).isFalse();
    }
}
