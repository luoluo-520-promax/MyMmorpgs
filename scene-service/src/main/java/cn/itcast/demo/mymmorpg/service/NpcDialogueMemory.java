package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.ai.npc.NpcEmotionVector;
import cn.itcast.demo.mymmorpg.ai.npc.NpcLongTermMemory;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NPC 短期会话记忆与情绪：优先 Redis 跨节点同步，离线后 TTL 保留；无 Redis 时回退进程内存。
 * 同时写入 {@link NpcLongTermMemory} 形成跨会话情感关系。
 */
@Component
public class NpcDialogueMemory {

    public enum Mood { NEUTRAL, FRIENDLY, CURIOUS, WORRIED, EXCITED }

    public record Turn(String role, String text, long atMs) {
    }

    public record Session(
            long playerId,
            String npcId,
            Mood mood,
            Deque<Turn> turns,
            long lastActiveMs) {
    }

    private static final Logger log = LoggerFactory.getLogger(NpcDialogueMemory.class);
    private static final int MAX_TURNS = 8;
    private static final long SESSION_TTL_MS = 15 * 60_000L;
    private static final String REDIS_KEY = "scene:npc:mood:";

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final NpcLongTermMemory longTermMemory = new NpcLongTermMemory();
    private StringRedisTemplate redis;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public void bindPersistence(ObjectProvider<StringRedisTemplate> redisProvider, ObjectMapper objectMapper) {
        this.redis = redisProvider.getIfAvailable();
        if (objectMapper != null) {
            this.objectMapper = objectMapper;
        }
    }

    public Session getOrCreate(long playerId, String npcId, long nowMs) {
        String key = key(playerId, npcId);
        Session cur = load(key, nowMs);
        if (cur != null && nowMs - cur.lastActiveMs() > SESSION_TTL_MS) {
            sessions.remove(key);
            deleteRedis(key);
            cur = null;
        }
        if (cur == null) {
            cur = new Session(playerId, npcId, Mood.NEUTRAL, new ArrayDeque<>(), nowMs);
            persist(key, cur);
        }
        return cur;
    }

    public void remember(long playerId, String npcId, String userMsg, String reply, long nowMs) {
        Session s = getOrCreate(playerId, npcId, nowMs);
        Mood mood = inferMood(userMsg, s.mood());
        Deque<Turn> turns = new ArrayDeque<>(s.turns());
        turns.addLast(new Turn("user", truncate(userMsg), nowMs));
        turns.addLast(new Turn("npc", truncate(reply), nowMs));
        while (turns.size() > MAX_TURNS) {
            turns.removeFirst();
        }
        Session next = new Session(playerId, npcId, mood, turns, nowMs);
        persist(key(playerId, npcId), next);
        NpcEmotionVector.InteractionKind kind = NpcEmotionVector.inferFromText(userMsg);
        longTermMemory.interact(playerId, npcId, kind, truncate(userMsg), 0.35);
    }

    public NpcLongTermMemory longTermMemory() {
        return longTermMemory;
    }

    public Map<String, Object> bondSnapshot(long playerId, String npcId) {
        return longTermMemory.toMap(playerId, npcId);
    }

    public List<Map<String, Object>> recentTurns(long playerId, String npcId, long nowMs) {
        Session s = getOrCreate(playerId, npcId, nowMs);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Turn t : s.turns()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("role", t.role());
            row.put("text", t.text());
            row.put("atMs", t.atMs());
            out.add(row);
        }
        return out;
    }

    public Mood mood(long playerId, String npcId, long nowMs) {
        return getOrCreate(playerId, npcId, nowMs).mood();
    }

    public String moodPrefix(Mood mood) {
        return switch (mood) {
            case FRIENDLY -> "（微笑）";
            case CURIOUS -> "（歪头）";
            case WORRIED -> "（皱眉）";
            case EXCITED -> "（兴奋）";
            default -> "";
        };
    }

    public void clear(long playerId, String npcId) {
        String key = key(playerId, npcId);
        sessions.remove(key);
        deleteRedis(key);
    }

    private Session load(String key, long nowMs) {
        if (redis != null) {
            try {
                String json = redis.opsForValue().get(REDIS_KEY + key);
                if (json != null && !json.isBlank()) {
                    StoredSession stored = objectMapper.readValue(json, StoredSession.class);
                    Deque<Turn> turns = new ArrayDeque<>();
                    if (stored.turns != null) {
                        for (StoredTurn t : stored.turns) {
                            turns.addLast(new Turn(t.role, t.text, t.atMs));
                        }
                    }
                    Mood mood = Mood.NEUTRAL;
                    try {
                        mood = Mood.valueOf(stored.mood);
                    } catch (Exception ignored) {
                    }
                    Session s = new Session(stored.playerId, stored.npcId, mood, turns, stored.lastActiveMs);
                    sessions.put(key, s);
                    return s;
                }
            } catch (Exception e) {
                log.warn("NPC 记忆 Redis 读取失败 key={}", key, e);
            }
        }
        return sessions.get(key);
    }

    private void persist(String key, Session s) {
        sessions.put(key, s);
        if (redis != null) {
            try {
                List<StoredTurn> turns = new ArrayList<>();
                for (Turn t : s.turns()) {
                    turns.add(new StoredTurn(t.role(), t.text(), t.atMs()));
                }
                StoredSession stored = new StoredSession(s.playerId(), s.npcId(), s.mood().name(), turns, s.lastActiveMs());
                redis.opsForValue().set(REDIS_KEY + key, objectMapper.writeValueAsString(stored),
                        Duration.ofMillis(SESSION_TTL_MS));
            } catch (Exception e) {
                log.warn("NPC 记忆 Redis 写入失败 key={}", key, e);
            }
        }
    }

    private void deleteRedis(String key) {
        if (redis != null) {
            try {
                redis.delete(REDIS_KEY + key);
            } catch (Exception e) {
                log.warn("NPC 记忆 Redis 删除失败 key={}", key, e);
            }
        }
    }

    private static Mood inferMood(String message, Mood prev) {
        if (message == null || message.isBlank()) {
            return prev == null ? Mood.NEUTRAL : prev;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("谢谢") || lower.contains("喜欢") || lower.contains("好")) {
            return Mood.FRIENDLY;
        }
        if (lower.contains("?") || lower.contains("？") || lower.contains("为什么") || lower.contains("什么")) {
            return Mood.CURIOUS;
        }
        if (lower.contains("危险") || lower.contains("死") || lower.contains("boss")) {
            return Mood.WORRIED;
        }
        if (lower.contains("！") || lower.contains("!") || lower.contains("太棒")) {
            return Mood.EXCITED;
        }
        return prev == null ? Mood.NEUTRAL : prev;
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 200 ? s : s.substring(0, 200);
    }

    private static String key(long playerId, String npcId) {
        return playerId + ":" + (npcId == null ? "guide" : npcId);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class StoredSession {
        public long playerId;
        public String npcId;
        public String mood;
        public List<StoredTurn> turns;
        public long lastActiveMs;

        public StoredSession() {
        }

        public StoredSession(long playerId, String npcId, String mood, List<StoredTurn> turns, long lastActiveMs) {
            this.playerId = playerId;
            this.npcId = npcId;
            this.mood = mood;
            this.turns = turns;
            this.lastActiveMs = lastActiveMs;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class StoredTurn {
        public String role;
        public String text;
        public long atMs;

        public StoredTurn() {
        }

        public StoredTurn(String role, String text, long atMs) {
            this.role = role;
            this.text = text;
            this.atMs = atMs;
        }
    }
}
