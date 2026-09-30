package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.port.PlayerCachePort;
import cn.itcast.demo.mymmorpg.port.PlayerNotificationPort;
import cn.itcast.demo.mymmorpg.support.ChatPolicy;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 公会 ↔ 聊天 roster 同步：创建 channel 键、加入/换会/退会维护 SET。
 */
public class ChatGuildRosterFlowTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private SetOperations<String, String> setOps;
    private ChatService chatService;
    private final ConcurrentHashMap<String, String> kv = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> sets = new ConcurrentHashMap<>();

    @BeforeMethod
    @SuppressWarnings("unchecked")
    public void setUp() {
        kv.clear();
        sets.clear();
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        setOps = mock(SetOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.opsForSet()).thenReturn(setOps);
        when(valueOps.get(anyString())).thenAnswer(inv -> kv.get(inv.getArgument(0)));
        doAnswer(inv -> {
            kv.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(valueOps).set(anyString(), anyString());
        when(setOps.add(anyString(), any())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            sets.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add((String) inv.getArgument(1));
            return 1L;
        });
        when(setOps.remove(anyString(), any())).thenAnswer(inv -> {
            Set<String> s = sets.get(inv.getArgument(0));
            if (s == null) {
                return 0L;
            }
            return s.remove(inv.getArgument(1)) ? 1L : 0L;
        });
        when(redis.delete(anyString())).thenAnswer(inv -> kv.remove(inv.getArgument(0)) != null);

        chatService = new ChatService(
                mock(PlayerCachePort.class),
                mock(ChatAsyncDbService.class),
                redis,
                mock(ChatPolicy.class),
                mock(ChatEventPublisher.class),
                mock(PlayerNotificationPort.class));
    }

    @Test
    public void guildCreatedAndMemberMoveAcrossGuilds() {
        chatService.onGuildCreated("1001");
        assertThat(kv.get("chat:guild:1001")).isEqualTo("1");

        chatService.setPlayerGuild(7L, "1001");
        assertThat(kv.get("chat:player:guild:7")).isEqualTo("1001");
        assertThat(sets.get("chat:guild:roster:1001")).contains("7");

        chatService.setPlayerGuild(7L, "1002");
        assertThat(kv.get("chat:player:guild:7")).isEqualTo("1002");
        assertThat(sets.get("chat:guild:roster:1001")).doesNotContain("7");
        assertThat(sets.get("chat:guild:roster:1002")).contains("7");

        chatService.setPlayerGuild(7L, null);
        assertThat(kv.containsKey("chat:player:guild:7")).isFalse();
        assertThat(sets.get("chat:guild:roster:1002")).doesNotContain("7");
    }
}
