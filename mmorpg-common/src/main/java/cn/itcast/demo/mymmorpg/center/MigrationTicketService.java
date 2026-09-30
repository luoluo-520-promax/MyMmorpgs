package cn.itcast.demo.mymmorpg.center;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 跨节点迁移会话票据：优先 Redis 持久化（跨进程），无 Redis 时回退进程内 Map。
 * 支持无缝交接扩展字段：速度/朝向/zoneId/seamless。
 */
@Component
public class MigrationTicketService {

    private static final Logger log = LoggerFactory.getLogger(MigrationTicketService.class);
    private static final String KEY_PREFIX = "center:migration:ticket:";
    private static final long TTL_MS = 120_000L;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TicketPayload(
            long playerId, int sceneId, int lineId, int entryId,
            float posX, float posY, float posZ, long expireAtMillis,
            float velocityX, float velocityZ, float facingYaw,
            boolean seamless, int zoneId) {

        public TicketPayload(long playerId, int sceneId, int lineId, int entryId,
                             float posX, float posY, float posZ, long expireAtMillis) {
            this(playerId, sceneId, lineId, entryId, posX, posY, posZ, expireAtMillis,
                    0f, 0f, 0f, false, 0);
        }
    }

    private final Map<String, TicketPayload> localTickets = new ConcurrentHashMap<>();
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public MigrationTicketService(ObjectProvider<StringRedisTemplate> redisTemplate,
                                  ObjectMapper objectMapper) {
        this.redis = redisTemplate == null ? null : redisTemplate.getIfAvailable();
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    /** 单元测试：仅内存。 */
    public MigrationTicketService() {
        this.redis = null;
        this.objectMapper = new ObjectMapper();
    }

    public String issue(long playerId, int sceneId, int lineId, int entryId,
                        float posX, float posY, float posZ) {
        return issue(playerId, sceneId, lineId, entryId, posX, posY, posZ,
                0f, 0f, 0f, false, 0);
    }

    public String issueSeamless(long playerId, int sceneId, int lineId, int entryId,
                                float posX, float posY, float posZ,
                                float velocityX, float velocityZ, float facingYaw, int zoneId) {
        return issue(playerId, sceneId, lineId, entryId, posX, posY, posZ,
                velocityX, velocityZ, facingYaw, true, zoneId);
    }

    public String issue(long playerId, int sceneId, int lineId, int entryId,
                        float posX, float posY, float posZ,
                        float velocityX, float velocityZ, float facingYaw,
                        boolean seamless, int zoneId) {
        String ticket = UUID.randomUUID().toString().replace("-", "");
        TicketPayload payload = new TicketPayload(playerId, sceneId, lineId, entryId,
                posX, posY, posZ, System.currentTimeMillis() + TTL_MS,
                velocityX, velocityZ, facingYaw, seamless, zoneId);
        if (redis != null) {
            try {
                redis.opsForValue().set(KEY_PREFIX + ticket, objectMapper.writeValueAsString(payload),
                        Duration.ofMillis(TTL_MS));
                return ticket;
            } catch (Exception e) {
                log.warn("Redis ticket issue failed, fallback local: {}", e.getMessage());
            }
        }
        localTickets.put(ticket, payload);
        return ticket;
    }

    public TicketPayload consume(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return null;
        }
        String id = ticket.trim();
        if (redis != null) {
            try {
                String key = KEY_PREFIX + id;
                String json = redis.opsForValue().getAndDelete(key);
                if (json != null && !json.isBlank()) {
                    TicketPayload payload = objectMapper.readValue(json, TicketPayload.class);
                    if (System.currentTimeMillis() > payload.expireAtMillis()) {
                        return null;
                    }
                    return payload;
                }
            } catch (Exception e) {
                log.warn("Redis ticket consume failed, try local: {}", e.getMessage());
            }
        }
        TicketPayload payload = localTickets.remove(id);
        if (payload == null) {
            return null;
        }
        if (System.currentTimeMillis() > payload.expireAtMillis()) {
            return null;
        }
        return payload;
    }

    /**
     * 延长迁移票据 TTL（不消费）。过期或不存在返回 null。
     */
    public TicketPayload renew(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return null;
        }
        String id = ticket.trim();
        long newExpire = System.currentTimeMillis() + TTL_MS;
        if (redis != null) {
            try {
                String key = KEY_PREFIX + id;
                String json = redis.opsForValue().get(key);
                if (json != null && !json.isBlank()) {
                    TicketPayload old = objectMapper.readValue(json, TicketPayload.class);
                    if (System.currentTimeMillis() > old.expireAtMillis()) {
                        redis.delete(key);
                        return null;
                    }
                    TicketPayload renewed = new TicketPayload(
                            old.playerId(), old.sceneId(), old.lineId(), old.entryId(),
                            old.posX(), old.posY(), old.posZ(), newExpire,
                            old.velocityX(), old.velocityZ(), old.facingYaw(),
                            old.seamless(), old.zoneId());
                    redis.opsForValue().set(key, objectMapper.writeValueAsString(renewed), Duration.ofMillis(TTL_MS));
                    return renewed;
                }
            } catch (Exception e) {
                log.warn("Redis ticket renew failed, try local: {}", e.getMessage());
            }
        }
        TicketPayload old = localTickets.get(id);
        if (old == null || System.currentTimeMillis() > old.expireAtMillis()) {
            localTickets.remove(id);
            return null;
        }
        TicketPayload renewed = new TicketPayload(
                old.playerId(), old.sceneId(), old.lineId(), old.entryId(),
                old.posX(), old.posY(), old.posZ(), newExpire,
                old.velocityX(), old.velocityZ(), old.facingYaw(),
                old.seamless(), old.zoneId());
        localTickets.put(id, renewed);
        return renewed;
    }
}
