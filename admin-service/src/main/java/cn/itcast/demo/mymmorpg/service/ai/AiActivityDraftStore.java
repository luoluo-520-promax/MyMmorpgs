package cn.itcast.demo.mymmorpg.service.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 活动草稿二次确认 token：优先 Redis（多实例共享），无 Redis 时回退进程内存。
 */
@Component
@ConditionalOnProperty(name = "spring.application.name", havingValue = "admin-service")
public class AiActivityDraftStore {

    private static final Logger log = LoggerFactory.getLogger(AiActivityDraftStore.class);
    private static final String REDIS_KEY = "admin:ai:activity-draft:";

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private StringRedisTemplate redis;
    private ObjectMapper objectMapper = new ObjectMapper();

    public AiActivityDraftStore() {
    }

    @Autowired
    public void bindPersistence(ObjectProvider<StringRedisTemplate> redisProvider, ObjectMapper objectMapper) {
        this.redis = redisProvider.getIfAvailable();
        if (objectMapper != null) {
            this.objectMapper = objectMapper;
        }
    }

    public String put(Long adminUserId, String documentJson, String resultHash, long ttlMs) {
        purgeExpired();
        String token = UUID.randomUUID().toString().replace("-", "");
        Entry entry = new Entry(adminUserId, documentJson, resultHash, System.currentTimeMillis() + ttlMs);
        if (redis != null) {
            try {
                redis.opsForValue().set(REDIS_KEY + token, objectMapper.writeValueAsString(entry),
                        Duration.ofMillis(Math.max(1000L, ttlMs)));
                return token;
            } catch (Exception e) {
                log.warn("AI 草稿写入 Redis 失败，回退内存 token={}", token, e);
            }
        }
        entries.put(token, entry);
        return token;
    }

    public Optional<Entry> consume(String token, Long adminUserId) {
        purgeExpired();
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Entry entry = null;
        if (redis != null) {
            try {
                String key = REDIS_KEY + token;
                String json = redis.opsForValue().get(key);
                redis.delete(key);
                if (json != null && !json.isBlank()) {
                    entry = objectMapper.readValue(json, Entry.class);
                }
            } catch (Exception e) {
                log.warn("AI 草稿读取 Redis 失败 token={}", token, e);
            }
        }
        if (entry == null) {
            entry = entries.remove(token);
        }
        if (entry == null || entry.expired()) {
            return Optional.empty();
        }
        if (!entry.adminUserId.equals(adminUserId)) {
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Entry>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().expiresAtMs <= now) {
                it.remove();
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(Long adminUserId, String documentJson, String resultHash, long expiresAtMs) {
        boolean expired() {
            return System.currentTimeMillis() > expiresAtMs;
        }
    }
}
