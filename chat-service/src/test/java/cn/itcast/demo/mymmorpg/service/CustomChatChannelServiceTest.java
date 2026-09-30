package cn.itcast.demo.mymmorpg.service;

import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CustomChatChannelServiceTest {

    private CustomChatChannelService service;
    private final Map<String, Map<String, String>> hashes = new HashMap<>();
    private final Map<String, Set<String>> sets = new HashMap<>();
    private final Map<String, java.util.List<String>> lists = new HashMap<>();

    @BeforeMethod
    @SuppressWarnings("unchecked")
    public void setUp() {
        hashes.clear();
        sets.clear();
        lists.clear();
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        SetOperations<String, String> setOps = mock(SetOperations.class);
        ListOperations<String, String> listOps = mock(ListOperations.class);
        when(redis.opsForHash()).thenReturn(hashOps);
        when(redis.opsForSet()).thenReturn(setOps);
        when(redis.opsForList()).thenReturn(listOps);
        when(redis.hasKey(anyString())).thenAnswer(inv -> hashes.containsKey(inv.getArgument(0))
                || sets.containsKey(inv.getArgument(0)));
        when(redis.expire(anyString(), any(java.time.Duration.class))).thenReturn(true);
        doAnswer(inv -> {
            String key = inv.getArgument(0);
            Map<String, String> m = inv.getArgument(1);
            hashes.put(key, new HashMap<>(m));
            return null;
        }).when(hashOps).putAll(anyString(), any());
        when(hashOps.entries(anyString())).thenAnswer(inv ->
                new HashMap<>(hashes.getOrDefault(inv.getArgument(0), Map.of())));
        when(setOps.add(anyString(), anyString())).thenAnswer(inv -> {
            sets.computeIfAbsent(inv.getArgument(0), k -> new HashSet<>()).add(inv.getArgument(1));
            return 1L;
        });
        when(setOps.isMember(anyString(), anyString())).thenAnswer(inv ->
                sets.getOrDefault(inv.getArgument(0), Set.of()).contains(inv.getArgument(1)));
        when(setOps.members(anyString())).thenAnswer(inv ->
                new HashSet<>(sets.getOrDefault(inv.getArgument(0), Set.of())));
        when(setOps.remove(anyString(), any())).thenReturn(1L);
        when(listOps.leftPush(anyString(), anyString())).thenAnswer(inv -> {
            lists.computeIfAbsent(inv.getArgument(0), k -> new java.util.ArrayList<>()).add(0, inv.getArgument(1));
            return 1L;
        });
        doAnswer(inv -> null).when(listOps).trim(anyString(), any(Long.class), any(Long.class));
        service = new CustomChatChannelService(redis);
    }

    @Test
    public void createJoinSendWithHyperlinks() {
        Map<String, Object> created = service.createChannel(1L, "钓鱼兴趣组", "INTEREST");
        assertThat(created.get("ok")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> channel = (Map<String, Object>) created.get("channel");
        String channelId = String.valueOf(channel.get("channelId"));
        assertThat(service.joinChannel(2L, channelId).get("ok")).isEqualTo(true);
        Map<String, Object> sent = service.send(1L, channelId, "来组队 [item:1001] 坐标 [coord:3,10.5,20]");
        assertThat(sent.get("ok")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> links = (List<Map<String, Object>>) sent.get("hyperlinks");
        assertThat(links).hasSize(2);
        assertThat(links.get(0).get("type")).isEqualTo("item");
    }

    @Test
    public void parseHyperlinksStatic() {
        List<Map<String, Object>> links = CustomChatChannelService.parseHyperlinks(
                "任务 [quest:88] 找 [player:9]");
        assertThat(links).hasSize(2);
        assertThat(links.get(0).get("type")).isEqualTo("quest");
    }
}
