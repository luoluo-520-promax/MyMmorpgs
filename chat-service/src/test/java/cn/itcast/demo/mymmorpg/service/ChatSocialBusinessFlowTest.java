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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 聊天社交新流程：自定义频道建群→入群→超链接发言→举报升骚扰分→自动禁言→拉黑。
 */
public class ChatSocialBusinessFlowTest {

    private CustomChatChannelService channels;
    private ChatHarassmentService harassment;
    private ChatReportService reports;
    private final Map<String, String> kv = new HashMap<>();
    private final Map<String, Map<String, String>> hashes = new HashMap<>();
    private final Map<String, Set<String>> sets = new HashMap<>();

    @BeforeMethod
    @SuppressWarnings("unchecked")
    public void setUp() {
        kv.clear();
        hashes.clear();
        sets.clear();
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        SetOperations<String, String> setOps = mock(SetOperations.class);
        ListOperations<String, String> listOps = mock(ListOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(redis.opsForHash()).thenReturn(hashOps);
        when(redis.opsForSet()).thenReturn(setOps);
        when(redis.opsForList()).thenReturn(listOps);
        when(redis.hasKey(anyString())).thenAnswer(inv -> {
            String k = inv.getArgument(0);
            return hashes.containsKey(k) || sets.containsKey(k) || kv.containsKey(k);
        });
        when(redis.expire(anyString(), any(java.time.Duration.class))).thenReturn(true);

        doAnswer(inv -> {
            String key = inv.getArgument(0);
            Map<String, String> m = inv.getArgument(1);
            hashes.put(key, new HashMap<>(m));
            return null;
        }).when(hashOps).putAll(anyString(), any());
        when(hashOps.entries(anyString())).thenAnswer(inv ->
                new HashMap<>(hashes.getOrDefault((String) inv.getArgument(0), Map.of())));

        // 与 CustomChatChannelServiceTest 相同的 stub 方式（已验证可用）
        when(setOps.add(anyString(), anyString())).thenAnswer(inv -> {
            sets.computeIfAbsent(inv.getArgument(0), k -> new HashSet<>()).add(inv.getArgument(1));
            return 1L;
        });
        when(setOps.isMember(anyString(), anyString())).thenAnswer(inv ->
                sets.getOrDefault((String) inv.getArgument(0), Set.of()).contains(inv.getArgument(1)));
        when(setOps.members(anyString())).thenAnswer(inv ->
                new HashSet<>(sets.getOrDefault((String) inv.getArgument(0), Set.of())));
        when(setOps.remove(anyString(), anyString())).thenAnswer(inv -> {
            Set<String> s = sets.get(inv.getArgument(0));
            if (s == null) {
                return 0L;
            }
            return s.remove((String) inv.getArgument(1)) ? 1L : 0L;
        });

        when(listOps.leftPush(anyString(), anyString())).thenReturn(1L);
        doAnswer(inv -> null).when(listOps).trim(anyString(), any(Long.class), any(Long.class));

        when(ops.get(anyString())).thenAnswer(inv -> kv.get(inv.getArgument(0)));
        when(ops.increment(anyString(), anyLong())).thenAnswer(inv -> {
            String k = inv.getArgument(0);
            long d = inv.getArgument(1);
            long v = Long.parseLong(kv.getOrDefault(k, "0")) + d;
            kv.put(k, String.valueOf(v));
            return v;
        });
        doAnswer(inv -> {
            kv.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(ops).set(anyString(), anyString(), any(java.time.Duration.class));

        channels = new CustomChatChannelService(redis);
        harassment = new ChatHarassmentService(redis);
        reports = new ChatReportService(harassment);
    }

    @Test
    public void customChannelHyperlinkReportBlockFlow() {
        Map<String, Object> created = channels.createChannel(1L, "战队突击队", "TEAM");
        assertThat(created.get("ok")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> channel = (Map<String, Object>) created.get("channel");
        String channelId = String.valueOf(channel.get("channelId"));

        assertThat(channels.joinChannel(2L, channelId).get("ok")).isEqualTo(true);
        assertThat(channels.joinChannel(3L, channelId).get("ok")).isEqualTo(true);

        Map<String, Object> sent = channels.send(1L, channelId,
                "集合副本 [quest:1001] 坐标 [coord:12,33.5,8] 带上 [item:88001]");
        assertThat(sent)
                .as("send result should be ok, actual=%s", sent)
                .containsEntry("ok", true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> links = (List<Map<String, Object>>) sent.get("hyperlinks");
        assertThat(links).hasSize(3);
        assertThat(links.stream().map(l -> l.get("type"))).containsExactlyInAnyOrder("quest", "coord", "item");

        Map<String, Object> mine = channels.listMyChannels(2L);
        assertThat(((List<?>) mine.get("channels"))).isNotEmpty();

        for (int i = 0; i < 4; i++) {
            assertThat(reports.report(2L, 9L, 5, "骚扰内容" + i, "abuse").get("ok")).isEqualTo(true);
        }
        Map<String, Object> last = reports.report(3L, 9L, 5, "继续骚扰", "abuse");
        @SuppressWarnings("unchecked")
        Map<String, Object> harass = (Map<String, Object>) last.get("harassment");
        assertThat((Long) harass.get("score")).isGreaterThanOrEqualTo(20L);
        assertThat(harass.get("autoMuted")).isEqualTo(true);

        assertThat(harassment.block(2L, 9L).get("ok")).isEqualTo(true);
        assertThat(harassment.isBlocked(2L, 9L)).isTrue();
        assertThat(channels.leaveChannel(3L, channelId).get("left")).isEqualTo(true);
    }
}
