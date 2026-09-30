package cn.itcast.demo.mymmorpg.activity;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 活动善后：兑换缓冲期判定。
 */
public class PostMortemProcessorTest {

    private PostMortemProcessor processor;

    @BeforeMethod
    @SuppressWarnings("unchecked")
    public void setUp() {
        ObjectProvider<StringRedisTemplate> redis = mock(ObjectProvider.class);
        when(redis.getIfAvailable()).thenReturn(null);
        processor = new PostMortemProcessor(redis);
    }

    @Test
    public void exchangeOnlyPeriod_betweenEndAndTPlus3() {
        long end = 1_700_000_000_000L;
        processor.registerRule(new PostMortemProcessor.ExpiredTokenRule(1L, end, 7001, 10));
        assertThat(processor.isExchangeOnlyPeriod(1L, end - 1)).isFalse();
        assertThat(processor.isExchangeOnlyPeriod(1L, end)).isTrue();
        assertThat(processor.isExchangeOnlyPeriod(1L, end + 2L * 24 * 60 * 60 * 1000)).isTrue();
        assertThat(processor.isExchangeOnlyPeriod(1L, end + 3L * 24 * 60 * 60 * 1000)).isFalse();
    }

    @Test
    public void sweepWithoutRedis_stillOk() {
        long now = System.currentTimeMillis();
        processor.registerRule(new PostMortemProcessor.ExpiredTokenRule(
                9L, now - 5L * 24 * 60 * 60 * 1000, 1, 1));
        assertThat(processor.sweepExpiredTokens(now).get("ok")).isEqualTo(true);
    }
}
