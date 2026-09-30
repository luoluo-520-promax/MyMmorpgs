package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.config.SessionLoginProperties;
import cn.itcast.demo.mymmorpg.rpc.PlayerOnlineKeys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 在线玩家会话：Redis Hash {@code online:user:{id}} + 节点索引 + 兼容旧键。
 */
@Service
@EnableConfigurationProperties(SessionLoginProperties.class)
public class PlayerSessionService {

    private static final String KEY_ACCOUNT_PLAYER = "auth:account:player:";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;
    private final String defaultNodeId;
    private final SessionLoginProperties sessionLoginProperties;

    public PlayerSessionService(
            StringRedisTemplate redisTemplate,
            SessionLoginProperties sessionLoginProperties,
            @Value("${game.center.local-node-id:local}") String defaultNodeId) {
        this.redisTemplate = redisTemplate;
        this.sessionLoginProperties = sessionLoginProperties;
        this.defaultNodeId = defaultNodeId == null || defaultNodeId.isBlank() ? "local" : defaultNodeId;
        long hours = sessionLoginProperties.getTokenTtlHours() <= 0 ? 24 : Math.max(24, sessionLoginProperties.getTokenTtlHours());
        this.ttl = Duration.ofHours(hours);
    }

    public void markOnline(long playerId, String sessionMarker) {
        markOnline(playerId, OnlinePresence.of(sessionMarker, defaultNodeId, 0, null, null, null));
    }

    public void markOnline(long playerId, OnlinePresence presence) {
        if (playerId <= 0) {
            return;
        }
        OnlinePresence p = presence == null ? OnlinePresence.of("unknown", defaultNodeId, 0, null, null, null) : presence;
        long now = System.currentTimeMillis();
        String oldNode = field(playerId, "node_id");
        Map<String, String> hash = new HashMap<>();
        hash.put("session_id", nullToEmpty(p.sessionId()));
        hash.put("node_id", nullToEmpty(p.nodeId(), defaultNodeId));
        hash.put("scene_id", String.valueOf(p.sceneId()));
        hash.put("login_time", String.valueOf(p.loginTimeMs() > 0 ? p.loginTimeMs() : now));
        hash.put("last_active", String.valueOf(now));
        hash.put("last_heartbeat", String.valueOf(now));
        hash.put("client_ip", nullToEmpty(p.clientIp()));
        hash.put("device_id", nullToEmpty(p.deviceId()));
        hash.put("client_type", nullToEmpty(p.clientType()));
        hash.put("online_duration", String.valueOf(Math.max(0L, now - (p.loginTimeMs() > 0 ? p.loginTimeMs() : now))));
        redisTemplate.opsForHash().putAll(PlayerOnlineKeys.userHash(playerId), hash);
        redisTemplate.expire(PlayerOnlineKeys.userHash(playerId), ttl);
        // 兼容旧查询
        redisTemplate.opsForValue().set(PlayerOnlineKeys.key(playerId), nullToEmpty(p.sessionId(), "online"), ttl);
        redisTemplate.opsForSet().add(PlayerOnlineKeys.KEY_ONLINE_SET, Long.toString(playerId));
        String nodeId = nullToEmpty(p.nodeId(), defaultNodeId);
        if (oldNode != null && !oldNode.isBlank() && !oldNode.equals(nodeId)) {
            redisTemplate.opsForSet().remove(PlayerOnlineKeys.nodeSet(oldNode), Long.toString(playerId));
        }
        redisTemplate.opsForSet().add(PlayerOnlineKeys.nodeSet(nodeId), Long.toString(playerId));
        // Bitmap 快速判定（低 24 位偏移，碰撞可接受于运营粗判）
        int offset = (int) (playerId & 0xFFFFFF);
        try {
            redisTemplate.opsForValue().setBit(PlayerOnlineKeys.ONLINE_BITMAP, offset, true);
        } catch (Exception ignored) {
            // optional
        }
        publishPresence(playerId, true);
    }

    public void touchHeartbeat(long playerId) {
        if (playerId <= 0 || !isOnline(playerId)) {
            return;
        }
        long now = System.currentTimeMillis();
        String loginTime = field(playerId, "login_time");
        long loginMs = parseLong(loginTime, now);
        redisTemplate.opsForHash().put(PlayerOnlineKeys.userHash(playerId), "last_heartbeat", String.valueOf(now));
        redisTemplate.opsForHash().put(PlayerOnlineKeys.userHash(playerId), "last_active", String.valueOf(now));
        redisTemplate.opsForHash().put(PlayerOnlineKeys.userHash(playerId), "online_duration",
                String.valueOf(Math.max(0L, now - loginMs)));
        redisTemplate.expire(PlayerOnlineKeys.userHash(playerId), ttl);
        redisTemplate.expire(PlayerOnlineKeys.key(playerId), ttl);
    }

    public void updateScene(long playerId, int sceneId, String nodeId) {
        if (playerId <= 0 || !isOnline(playerId)) {
            return;
        }
        String oldNode = field(playerId, "node_id");
        String newNode = nodeId == null || nodeId.isBlank() ? defaultNodeId : nodeId;
        redisTemplate.opsForHash().put(PlayerOnlineKeys.userHash(playerId), "scene_id", String.valueOf(sceneId));
        redisTemplate.opsForHash().put(PlayerOnlineKeys.userHash(playerId), "node_id", newNode);
        if (oldNode != null && !oldNode.equals(newNode)) {
            redisTemplate.opsForSet().remove(PlayerOnlineKeys.nodeSet(oldNode), Long.toString(playerId));
            redisTemplate.opsForSet().add(PlayerOnlineKeys.nodeSet(newNode), Long.toString(playerId));
        }
        touchHeartbeat(playerId);
    }

    /** 记录账号当前绑定角色，供重复登录踢线定位 playerId。 */
    public void bindAccountPlayer(long accountId, long playerId) {
        if (accountId <= 0 || playerId <= 0) {
            return;
        }
        redisTemplate.opsForValue().set(KEY_ACCOUNT_PLAYER + accountId, Long.toString(playerId), ttl);
    }

    public Long findBoundPlayerId(long accountId) {
        if (accountId <= 0) {
            return null;
        }
        String v = redisTemplate.opsForValue().get(KEY_ACCOUNT_PLAYER + accountId);
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void clearAccountPlayer(long accountId) {
        if (accountId <= 0) {
            return;
        }
        redisTemplate.delete(KEY_ACCOUNT_PLAYER + accountId);
    }

    public void markOffline(long playerId) {
        if (playerId <= 0) {
            return;
        }
        String nodeId = field(playerId, "node_id");
        redisTemplate.delete(PlayerOnlineKeys.userHash(playerId));
        redisTemplate.delete(PlayerOnlineKeys.key(playerId));
        redisTemplate.opsForSet().remove(PlayerOnlineKeys.KEY_ONLINE_SET, Long.toString(playerId));
        if (nodeId != null && !nodeId.isBlank()) {
            redisTemplate.opsForSet().remove(PlayerOnlineKeys.nodeSet(nodeId), Long.toString(playerId));
        }
        int offset = (int) (playerId & 0xFFFFFF);
        try {
            redisTemplate.opsForValue().setBit(PlayerOnlineKeys.ONLINE_BITMAP, offset, false);
        } catch (Exception ignored) {
            // optional
        }
        publishPresence(playerId, false);
    }

    public boolean isOnline(long playerId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PlayerOnlineKeys.userHash(playerId)))
                || Boolean.TRUE.equals(redisTemplate.hasKey(PlayerOnlineKeys.key(playerId)));
    }

    public boolean isOnlineFast(long playerId) {
        try {
            Boolean bit = redisTemplate.opsForValue().getBit(PlayerOnlineKeys.ONLINE_BITMAP, playerId & 0xFFFFFF);
            if (Boolean.TRUE.equals(bit)) {
                return true;
            }
        } catch (Exception ignored) {
            // fall through
        }
        return isOnline(playerId);
    }

    public Map<String, String> getOnlineFields(long playerId) {
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(PlayerOnlineKeys.userHash(playerId));
        Map<String, String> out = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (Map.Entry<Object, Object> e : raw.entrySet()) {
            out.put(String.valueOf(e.getKey()), e.getValue() == null ? "" : String.valueOf(e.getValue()));
        }
        return out;
    }

    public Set<String> listOnlinePlayerIds() {
        Set<String> ids = redisTemplate.opsForSet().members(PlayerOnlineKeys.KEY_ONLINE_SET);
        return ids == null ? Set.of() : ids;
    }

    public Set<String> listOnlineOnNode(String nodeId) {
        Set<String> ids = redisTemplate.opsForSet().members(PlayerOnlineKeys.nodeSet(nodeId));
        return ids == null ? Set.of() : ids;
    }

    public long countOnline() {
        Long size = redisTemplate.opsForSet().size(PlayerOnlineKeys.KEY_ONLINE_SET);
        return size == null ? 0 : size;
    }

    public long countOnlineOnNode(String nodeId) {
        Long size = redisTemplate.opsForSet().size(PlayerOnlineKeys.nodeSet(nodeId));
        return size == null ? 0 : size;
    }

    /**
     * 扫描超时心跳的僵尸会话并清理，返回清理数量。
     */
    public int sweepStaleSessions() {
        long timeoutMs = sessionLoginProperties.getOnlineHeartbeatTimeoutSeconds() * 1000L;
        long now = System.currentTimeMillis();
        int cleaned = 0;
        for (String id : listOnlinePlayerIds()) {
            long playerId;
            try {
                playerId = Long.parseLong(id);
            } catch (NumberFormatException e) {
                continue;
            }
            String hb = field(playerId, "last_heartbeat");
            long last = parseLong(hb, 0L);
            if (last > 0 && now - last > timeoutMs) {
                markOffline(playerId);
                cleaned++;
            }
        }
        return cleaned;
    }

    private void publishPresence(long playerId, boolean online) {
        try {
            redisTemplate.convertAndSend(PlayerOnlineKeys.ONLINE_CHANNEL,
                    playerId + ":" + (online ? "1" : "0"));
        } catch (Exception ignored) {
            // optional
        }
    }

    private String field(long playerId, String name) {
        Object v = redisTemplate.opsForHash().get(PlayerOnlineKeys.userHash(playerId), name);
        return v == null ? null : String.valueOf(v);
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }

    private static String nullToEmpty(String v, String def) {
        return v == null || v.isBlank() ? def : v;
    }

    private static long parseLong(String v, long def) {
        if (v == null || v.isBlank()) {
            return def;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** 在线态写入参数。 */
    public record OnlinePresence(
            String sessionId,
            String nodeId,
            int sceneId,
            long loginTimeMs,
            String clientIp,
            String deviceId,
            String clientType) {

        public static OnlinePresence of(String sessionId, String nodeId, int sceneId,
                                        String clientIp, String deviceId, String clientType) {
            return new OnlinePresence(sessionId, nodeId, sceneId, System.currentTimeMillis(),
                    clientIp, deviceId, clientType);
        }
    }
}
