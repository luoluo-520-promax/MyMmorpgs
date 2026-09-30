package cn.itcast.demo.mymmorpg.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * LocalDamageCounter 业务流程：本地累加 → 批量 ZINCRBY → 结算刷盘。
 */
public class LocalDamageCounterTest {

    private LocalDamageCounter counter;
    private StringRedisTemplate redis;

    @BeforeMethod
    @SuppressWarnings("unchecked")
    public void setUp() {
        redis = mock(StringRedisTemplate.class);
        ZSetOperations<String, String> zset = mock(ZSetOperations.class);
        when(redis.opsForZSet()).thenReturn(zset);
        when(zset.incrementScore(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyDouble())).thenReturn(1000.0);

        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(redis);
        counter = new LocalDamageCounter(provider);
    }

    @Test
    public void accumulateLocallyThenFlushToRedis() {
        String key = "guild:boss:damage:42";
        counter.accumulate(key, 101L, 500L);
        counter.accumulate(key, 101L, 300L);
        counter.accumulate(key, 102L, 200L);

        assertThat(counter.localTotal(key, 101L)).isEqualTo(800L);
        assertThat(counter.localTotal(key, 102L)).isEqualTo(200L);

        Map<String, Object> flushed = counter.flushAll();
        assertThat(flushed.get("ok")).isEqualTo(true);
        assertThat(((Number) flushed.get("flushedEntries")).intValue()).isEqualTo(2);
        assertThat(counter.stats().get("redisIncrements")).isEqualTo(2L);
    }

    @Test
    public void flushResetsPendingAfterCommit() {
        String key = "guild:boss:damage:99";
        counter.accumulate(key, 1L, 100L);
        counter.flushAll();
        counter.accumulate(key, 1L, 50L);
        assertThat(counter.localTotal(key, 1L)).isEqualTo(50L);
    }

    @Test
    public void clearRemovesPendingKey() {
        String key = "guild:boss:damage:1";
        counter.accumulate(key, 1L, 999L);
        counter.clear(key);
        assertThat(counter.localTotal(key, 1L)).isZero();
    }
}
