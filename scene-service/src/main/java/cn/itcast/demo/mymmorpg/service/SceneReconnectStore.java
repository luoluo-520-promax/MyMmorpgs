package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.config.SceneRuntimeProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 断线重连快照：优先 Redis，无 Redis 时回退进程内 Map。
 */
@Component
@EnableConfigurationProperties(SceneRuntimeProperties.class)
public class SceneReconnectStore {

    private static final Logger log = LoggerFactory.getLogger(SceneReconnectStore.class);
    private static final String KEY_PREFIX = "scene:reconnect:";

    public record Snapshot(long playerId, int sceneId, int lineId,
                           float posX, float posY, float posZ,
                           String resumeToken, long expireAtMillis) {
    }

    private final Map<Long, Snapshot> local = new ConcurrentHashMap<>();
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final SceneRuntimeProperties properties;

    @Autowired
    public SceneReconnectStore(ObjectProvider<StringRedisTemplate> redisTemplate,
                               ObjectMapper objectMapper,
                               SceneRuntimeProperties properties) {
        this.redis = redisTemplate == null ? null : redisTemplate.getIfAvailable();
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.properties = properties;
    }

    /** 单元测试：仅内存。 */
    public SceneReconnectStore(SceneRuntimeProperties properties) {
        this.redis = null;
        this.objectMapper = new ObjectMapper();
        this.properties = properties;
    }

    public String save(long playerId, int sceneId, int lineId, float x, float y, float z) {
        long ttl = Math.max(5_000L, properties.getReconnectGraceMs());
        String token = UUID.randomUUID().toString().replace("-", "");
        Snapshot snap = new Snapshot(playerId, sceneId, lineId, x, y, z,
                token, System.currentTimeMillis() + ttl);
        if (redis != null) {
            try {
                redis.opsForValue().set(KEY_PREFIX + playerId, objectMapper.writeValueAsString(snap),
                        Duration.ofMillis(ttl));
                return token;
            } catch (Exception e) {
                log.warn("Redis reconnect save failed, fallback local: {}", e.getMessage());
            }
        }
        local.put(playerId, snap);
        return token;
    }

    public Snapshot peek(long playerId) {
        Snapshot snap = read(playerId, false);
        if (snap == null) {
            return null;
        }
        if (System.currentTimeMillis() > snap.expireAtMillis()) {
            clear(playerId);
            return null;
        }
        return snap;
    }

    public Snapshot consume(long playerId, String resumeToken) {
        Snapshot snap = read(playerId, false);
        if (snap == null) {
            return null;
        }
        if (System.currentTimeMillis() > snap.expireAtMillis()) {
            clear(playerId);
            return null;
        }
        if (resumeToken == null || resumeToken.isBlank()
                || !resumeToken.trim().equals(snap.resumeToken())) {
            return null;
        }
        clear(playerId);
        return snap;
    }

    public void clear(long playerId) {
        local.remove(playerId);
        if (redis != null) {
            try {
                redis.delete(KEY_PREFIX + playerId);
            } catch (Exception ignored) {
                // ignore
            }
        }
    }

    private Snapshot read(long playerId, boolean delete) {
        if (redis != null) {
            try {
                String key = KEY_PREFIX + playerId;
                String json = delete ? redis.opsForValue().getAndDelete(key) : redis.opsForValue().get(key);
                if (json != null && !json.isBlank()) {
                    return objectMapper.readValue(json, Snapshot.class);
                }
            } catch (Exception e) {
                log.warn("Redis reconnect read failed, try local: {}", e.getMessage());
            }
        }
        return delete ? local.remove(playerId) : local.get(playerId);
    }
}
