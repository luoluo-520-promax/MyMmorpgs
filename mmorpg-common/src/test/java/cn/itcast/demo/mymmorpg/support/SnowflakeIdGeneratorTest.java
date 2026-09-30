package cn.itcast.demo.mymmorpg.support;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SnowflakeIdGeneratorTest {

    @Test
    public void idsAreMonotonicAndUnique() {
        SnowflakeIdGenerator gen = new SnowflakeIdGenerator(1, 1);
        long a = gen.nextId();
        long b = gen.nextId();
        assertThat(b).isGreaterThan(a);
    }
}
