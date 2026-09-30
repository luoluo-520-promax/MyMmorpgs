package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.model.PlayerActivityProgress;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.testng.annotations.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 活动进度 Redis Hash 拆分 + 旧 Key 迁移。
 */
public class ActivityPlayerProgressHashStoreTest {

    @Test
    @SuppressWarnings("unchecked")
    public void save_writesHashField() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hash = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hash);

        ActivityPlayerProgressStore store = new ActivityPlayerProgressStore(redis, new ObjectMapper());
        PlayerActivityProgress p = new PlayerActivityProgress();
        p.rechargeAmount = 100;
        store.save(5L, 99L, p);

        verify(hash).put(eq("player:act:5"), eq("99"), anyString());
        verify(redis).expire(eq("player:act:5"), eq(Duration.ofDays(120)));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void load_migratesLegacyKey() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hash = mock(HashOperations.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForHash()).thenReturn(hash);
        when(redis.opsForValue()).thenReturn(values);
        when(hash.get("player:act:3", "7")).thenReturn(null);
        String legacyJson = "{\"rechargeAmount\":50}";
        when(values.get("activity:prog:3:7")).thenReturn(legacyJson);

        ActivityPlayerProgressStore store = new ActivityPlayerProgressStore(redis, new ObjectMapper());
        PlayerActivityProgress p = store.loadOrCreate(3L, 7L);
        assertThat(p.rechargeAmount).isEqualTo(50);
        verify(hash).put("player:act:3", "7", legacyJson);
        verify(redis).delete("activity:prog:3:7");
    }
}
