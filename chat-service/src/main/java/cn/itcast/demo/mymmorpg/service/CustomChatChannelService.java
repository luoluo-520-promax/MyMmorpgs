package cn.itcast.demo.mymmorpg.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自定义聊天频道（战队/兴趣小组）+ 消息超链接解析。
 */
@Service
public class CustomChatChannelService {

    public static final int CHANNEL_CUSTOM = 5;

    private static final String KEY_META = "chat:custom:meta:";
    private static final String KEY_MEMBERS = "chat:custom:members:";
    private static final String KEY_PLAYER = "chat:custom:player:";
    private static final String KEY_MSG = "chat:custom:msg:";
    private static final Duration TTL = Duration.ofDays(90);

    private static final Pattern LINK_PATTERN = Pattern.compile(
            "\\[(item|quest|coord|player):([^\\]]+)]", Pattern.CASE_INSENSITIVE);

    private final StringRedisTemplate stringRedisTemplate;

    public CustomChatChannelService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public Map<String, Object> createChannel(long ownerId, String name, String type) {
        if (ownerId <= 0 || name == null || name.isBlank()) {
            return Map.of("ok", false, "error", "invalid_args");
        }
        String channelId = "cc-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String kind = type == null || type.isBlank() ? "INTEREST" : type.trim().toUpperCase();
        if (!kind.equals("TEAM") && !kind.equals("INTEREST") && !kind.equals("GUILD_SUB")) {
            kind = "INTEREST";
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("channelId", channelId);
        meta.put("name", name.trim());
        meta.put("type", kind);
        meta.put("ownerId", ownerId);
        meta.put("createdAtMs", System.currentTimeMillis());
        stringRedisTemplate.opsForHash().putAll(KEY_META + channelId, toStringMap(meta));
        stringRedisTemplate.expire(KEY_META + channelId, TTL);
        stringRedisTemplate.opsForSet().add(KEY_MEMBERS + channelId, String.valueOf(ownerId));
        stringRedisTemplate.expire(KEY_MEMBERS + channelId, TTL);
        stringRedisTemplate.opsForSet().add(KEY_PLAYER + ownerId, channelId);
        return Map.of("ok", true, "channel", meta, "protocolChannel", CHANNEL_CUSTOM);
    }

    public Map<String, Object> joinChannel(long playerId, String channelId) {
        if (playerId <= 0 || channelId == null || channelId.isBlank()) {
            return Map.of("ok", false, "error", "invalid_args");
        }
        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(KEY_META + channelId))) {
            return Map.of("ok", false, "error", "channel_not_found");
        }
        stringRedisTemplate.opsForSet().add(KEY_MEMBERS + channelId, String.valueOf(playerId));
        stringRedisTemplate.opsForSet().add(KEY_PLAYER + playerId, channelId);
        return Map.of("ok", true, "channelId", channelId, "playerId", playerId);
    }

    public Map<String, Object> leaveChannel(long playerId, String channelId) {
        stringRedisTemplate.opsForSet().remove(KEY_MEMBERS + channelId, String.valueOf(playerId));
        stringRedisTemplate.opsForSet().remove(KEY_PLAYER + playerId, channelId);
        return Map.of("ok", true, "left", true);
    }

    public Map<String, Object> send(long senderId, String channelId, String content) {
        if (senderId <= 0 || channelId == null || content == null || content.isBlank()) {
            return Map.of("ok", false, "error", "invalid_args");
        }
        Boolean member = stringRedisTemplate.opsForSet().isMember(KEY_MEMBERS + channelId, String.valueOf(senderId));
        if (!Boolean.TRUE.equals(member)) {
            return Map.of("ok", false, "error", "not_member");
        }
        List<Map<String, Object>> links = parseHyperlinks(content);
        String cleaned = content.trim();
        if (cleaned.length() > 512) {
            cleaned = cleaned.substring(0, 512);
        }
        String entry = senderId + "|" + System.currentTimeMillis() + "|" + cleaned;
        stringRedisTemplate.opsForList().leftPush(KEY_MSG + channelId, entry);
        stringRedisTemplate.opsForList().trim(KEY_MSG + channelId, 0, 99);
        stringRedisTemplate.expire(KEY_MSG + channelId, TTL);
        Set<String> members = stringRedisTemplate.opsForSet().members(KEY_MEMBERS + channelId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("channelId", channelId);
        out.put("senderId", senderId);
        out.put("content", cleaned);
        out.put("hyperlinks", links);
        out.put("memberCount", members == null ? 0 : members.size());
        out.put("protocolChannel", CHANNEL_CUSTOM);
        return out;
    }

    public Map<String, Object> listMyChannels(long playerId) {
        Set<String> ids = stringRedisTemplate.opsForSet().members(KEY_PLAYER + playerId);
        List<Map<String, Object>> channels = new ArrayList<>();
        if (ids != null) {
            for (String id : ids) {
                Map<Object, Object> raw = stringRedisTemplate.opsForHash().entries(KEY_META + id);
                if (raw != null && !raw.isEmpty()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    raw.forEach((k, v) -> m.put(String.valueOf(k), v));
                    channels.add(m);
                }
            }
        }
        return Map.of("ok", true, "channels", channels);
    }

    /**
     * 解析道具/任务/坐标/玩家超链接，例如 [item:1001] [quest:200] [coord:1,12.5,8.0] [player:99]
     */
    public static List<Map<String, Object>> parseHyperlinks(String content) {
        List<Map<String, Object>> links = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return links;
        }
        Matcher matcher = LINK_PATTERN.matcher(content);
        while (matcher.find()) {
            String type = matcher.group(1).toLowerCase();
            String value = matcher.group(2).trim();
            Map<String, Object> link = new LinkedHashMap<>();
            link.put("type", type);
            link.put("raw", matcher.group());
            link.put("value", value);
            if ("coord".equals(type)) {
                String[] parts = value.split(",");
                if (parts.length >= 3) {
                    link.put("mapId", parts[0].trim());
                    link.put("x", parts[1].trim());
                    link.put("y", parts[2].trim());
                }
            }
            links.add(link);
        }
        return links;
    }

    private static Map<String, String> toStringMap(Map<String, Object> src) {
        Map<String, String> out = new LinkedHashMap<>();
        src.forEach((k, v) -> out.put(k, String.valueOf(v)));
        return out;
    }
}
