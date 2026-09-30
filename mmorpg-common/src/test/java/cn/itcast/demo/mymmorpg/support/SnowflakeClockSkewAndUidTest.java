package cn.itcast.demo.mymmorpg.support;

import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Snowflake 时钟回拨补偿与 GlobalUid 固定 worker 发号。
 */
public class SnowflakeClockSkewAndUidTest {

    @Test
    public void batchIdsUnique() {
        SnowflakeIdGenerator gen = new SnowflakeIdGenerator(7, 3);
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            assertThat(ids.add(gen.nextId())).isTrue();
        }
        assertThat(ids).hasSize(500);
    }

    @Test
    public void globalUidFacade_fixedWorker() {
        GlobalUidGenerator uid = new GlobalUidGenerator(4, 1);
        assertThat(uid.workerId()).isEqualTo(4L);
        long a = uid.nextId();
        long b = uid.nextId();
        assertThat(b).isGreaterThan(a);
    }

    @Test
    public void workerIdOutOfRange_rejected() {
        try {
            new SnowflakeIdGenerator(32, 0);
            assertThat(false).as("should throw").isTrue();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("workerId");
        }
    }
}
