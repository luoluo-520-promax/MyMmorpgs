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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 草稿版本管理与审核流：DRAFT → SUBMITTED → APPROVED → PUBLISHED（可 REJECTED）。
 */
@Component
@ConditionalOnProperty(name = "spring.application.name", havingValue = "admin-service")
public class AiDraftVersionStore {

    public enum Status { DRAFT, SUBMITTED, APPROVED, REJECTED, PUBLISHED }

    private static final Logger log = LoggerFactory.getLogger(AiDraftVersionStore.class);
    private static final String REDIS_KEY = "admin:ai:draft-ver:";

    private final ConcurrentHashMap<String, DraftVersion> memory = new ConcurrentHashMap<>();
    private StringRedisTemplate redis;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public void bindPersistence(ObjectProvider<StringRedisTemplate> redisProvider, ObjectMapper objectMapper) {
        this.redis = redisProvider.getIfAvailable();
        if (objectMapper != null) {
            this.objectMapper = objectMapper;
        }
    }

    public DraftVersion create(Long adminUserId, String domain, String documentJson, String promptVersion) {
        String draftId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        DraftVersion v = new DraftVersion(draftId, 1, adminUserId, domain,
                documentJson == null ? "{}" : documentJson,
                promptVersion == null ? "" : promptVersion,
                Status.DRAFT.name(), null, System.currentTimeMillis(), List.of());
        persist(v);
        return v;
    }

    public Optional<DraftVersion> submit(String draftId, Long adminUserId) {
        return transition(draftId, adminUserId, Status.DRAFT, Status.SUBMITTED, null);
    }

    public Optional<DraftVersion> approve(String draftId, Long reviewerId, String note) {
        return transition(draftId, reviewerId, Status.SUBMITTED, Status.APPROVED, note);
    }

    public Optional<DraftVersion> reject(String draftId, Long reviewerId, String note) {
        return transition(draftId, reviewerId, Status.SUBMITTED, Status.REJECTED, note);
    }

    public Optional<DraftVersion> publish(String draftId, Long adminUserId) {
        return transition(draftId, adminUserId, Status.APPROVED, Status.PUBLISHED, null);
    }

    public Optional<DraftVersion> revise(String draftId, Long adminUserId, String documentJson) {
        Optional<DraftVersion> cur = get(draftId);
        if (cur.isEmpty() || !cur.get().adminUserId().equals(adminUserId)) {
            return Optional.empty();
        }
        DraftVersion old = cur.get();
        List<Map<String, Object>> history = new ArrayList<>(old.history() == null ? List.of() : old.history());
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("version", old.version());
        snap.put("status", old.status());
        snap.put("documentJson", old.documentJson());
        snap.put("atMs", System.currentTimeMillis());
        history.add(snap);
        DraftVersion next = new DraftVersion(old.draftId(), old.version() + 1, old.adminUserId(), old.domain(),
                documentJson == null ? old.documentJson() : documentJson, old.promptVersion(),
                Status.DRAFT.name(), null, System.currentTimeMillis(), history);
        persist(next);
        return Optional.of(next);
    }

    public Optional<DraftVersion> get(String draftId) {
        if (draftId == null || draftId.isBlank()) {
            return Optional.empty();
        }
        if (redis != null) {
            try {
                String json = redis.opsForValue().get(REDIS_KEY + draftId);
                if (json != null && !json.isBlank()) {
                    return Optional.of(objectMapper.readValue(json, DraftVersion.class));
                }
            } catch (Exception e) {
                log.warn("草稿版本 Redis 读取失败 draftId={}", draftId, e);
            }
        }
        return Optional.ofNullable(memory.get(draftId));
    }

    private Optional<DraftVersion> transition(String draftId, Long actorId, Status from, Status to, String note) {
        Optional<DraftVersion> cur = get(draftId);
        if (cur.isEmpty() || actorId == null) {
            return Optional.empty();
        }
        DraftVersion old = cur.get();
        if (!from.name().equals(old.status())) {
            return Optional.empty();
        }
        List<Map<String, Object>> history = new ArrayList<>(old.history() == null ? List.of() : old.history());
        Map<String, Object> hop = new LinkedHashMap<>();
        hop.put("from", from.name());
        hop.put("to", to.name());
        hop.put("actorId", actorId);
        hop.put("note", note == null ? "" : note);
        hop.put("atMs", System.currentTimeMillis());
        history.add(hop);
        DraftVersion next = new DraftVersion(old.draftId(), old.version(), old.adminUserId(), old.domain(),
                old.documentJson(), old.promptVersion(), to.name(), note, System.currentTimeMillis(), history);
        persist(next);
        return Optional.of(next);
    }

    private void persist(DraftVersion v) {
        memory.put(v.draftId(), v);
        if (redis != null) {
            try {
                redis.opsForValue().set(REDIS_KEY + v.draftId(), objectMapper.writeValueAsString(v),
                        Duration.ofDays(30));
            } catch (Exception e) {
                log.warn("草稿版本 Redis 写入失败 draftId={}", v.draftId(), e);
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DraftVersion(
            String draftId,
            int version,
            Long adminUserId,
            String domain,
            String documentJson,
            String promptVersion,
            String status,
            String reviewNote,
            long updatedAtMs,
            List<Map<String, Object>> history) {
    }
}
