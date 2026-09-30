package cn.itcast.demo.mymmorpg.cache;

import cn.itcast.demo.mymmorpg.entity.MonsterConfig;
import cn.itcast.demo.mymmorpg.service.ConfigQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 缓存预热：无 Redis 时本地双重检测回填。
 */
public class CacheWarmUpServiceTest {

    private CacheWarmUpService service;

    @BeforeMethod
    @SuppressWarnings("unchecked")
    public void setUp() {
        ConfigQueryService configQuery = mock(ConfigQueryService.class);
        when(configQuery.listAllMonsters()).thenReturn(List.of(new MonsterConfig()));
        ObjectProvider<StringRedisTemplate> redis = mock(ObjectProvider.class);
        when(redis.getIfAvailable()).thenReturn(null);
        service = new CacheWarmUpService(configQuery, redis, new ObjectMapper());
    }

    @Test
    public void warmUpForVersion_fillsDefaultHotKeysLocally() {
        Map<String, Object> result = service.warmUpForVersion("2.6.0", null);
        assertThat(result.get("ok")).isEqualTo(true);
        assertThat((List<?>) result.get("warmed")).hasSize(3);
        assertThat(service.fillHotKey("abyss:floor:config:12")).isTrue();
    }
}
