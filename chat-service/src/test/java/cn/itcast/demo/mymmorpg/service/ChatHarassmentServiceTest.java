package cn.itcast.demo.mymmorpg.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ChatHarassmentServiceTest {

    private ChatHarassmentService service;
    private final Map<String, String> store = new HashMap<>();

    @BeforeMethod
    @SuppressWarnings("unchecked")
    public void setUp() {
        store.clear();
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenAnswer(inv -> store.get(inv.getArgument(0)));
        when(ops.increment(anyString(), anyLong())).thenAnswer(inv -> {
            String k = inv.getArgument(0);
            long delta = inv.getArgument(1);
            long v = Long.parseLong(store.getOrDefault(k, "0")) + delta;
            store.put(k, String.valueOf(v));
            return v;
        });
        doAnswer(inv -> {
            store.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(ops).set(anyString(), anyString(), any(java.time.Duration.class));
        when(redis.expire(anyString(), any(java.time.Duration.class))).thenReturn(true);
        org.springframework.data.redis.core.SetOperations<String, String> setOps =
                mock(org.springframework.data.redis.core.SetOperations.class);
        when(redis.opsForSet()).thenReturn(setOps);
        when(setOps.add(anyString(), anyString())).thenReturn(1L);
        when(setOps.members(anyString())).thenReturn(java.util.Set.of("9"));
        when(setOps.isMember(anyString(), anyString())).thenReturn(true);
        service = new ChatHarassmentService(redis);
    }

    @Test
    public void reportHitsAccumulateAndAutoMute() {
        for (int i = 0; i < 4; i++) {
            service.recordReportHit(7L, "abuse");
        }
        Map<String, Object> last = service.recordReportHit(7L, "abuse");
        assertThat((Long) last.get("score")).isGreaterThanOrEqualTo(20L);
        assertThat(last.get("autoMuted")).isEqualTo(true);
    }

    @Test
    public void blockList() {
        assertThat(service.block(1L, 9L).get("ok")).isEqualTo(true);
        assertThat(service.isBlocked(1L, 9L)).isTrue();
    }
}
