package cn.itcast.demo.mymmorpg.rogue;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 肉鸽运行时：本地缓存 + Redis 持久化（TTL），支持重启续玩与多实例共享。
 */
@Component
public class RogueManager {

    private static final Logger log = LoggerFactory.getLogger(RogueManager.class);
    private static final String REDIS_KEY = "rogue:runtime:";
    private static final Duration TTL = Duration.ofHours(6);

    private final ConcurrentHashMap<Long, RogueRuntime> byPlayer = new ConcurrentHashMap<>();
    private StringRedisTemplate redis;
    private ObjectMapper objectMapper;

    public RogueManager() {
    }

    @Autowired
    public void bindPersistence(ObjectProvider<StringRedisTemplate> redisProvider, ObjectMapper objectMapper) {
        this.redis = redisProvider.getIfAvailable();
        this.objectMapper = objectMapper;
    }

    public RogueRuntime get(long playerId) {
        RogueRuntime local = byPlayer.get(playerId);
        if (local != null) {
            return local;
        }
        RogueRuntime fromRedis = loadFromRedis(playerId);
        if (fromRedis != null) {
            byPlayer.put(playerId, fromRedis);
        }
        return fromRedis;
    }

    public void put(RogueRuntime runtime) {
        byPlayer.put(runtime.getPlayerId(), runtime);
        saveToRedis(runtime);
    }

    /** 运行时字段变更后刷回 Redis。 */
    public void touch(RogueRuntime runtime) {
        if (runtime == null) {
            return;
        }
        byPlayer.put(runtime.getPlayerId(), runtime);
        saveToRedis(runtime);
    }

    public RogueRuntime remove(long playerId) {
        RogueRuntime removed = byPlayer.remove(playerId);
        deleteFromRedis(playerId);
        return removed;
    }

    public boolean isActive(long playerId) {
        return get(playerId) != null;
    }

    private void saveToRedis(RogueRuntime runtime) {
        if (redis == null || objectMapper == null || runtime == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(Snapshot.from(runtime));
            redis.opsForValue().set(REDIS_KEY + runtime.getPlayerId(), json, TTL);
        } catch (Exception e) {
            log.warn("肉鸽进度写入 Redis 失败 playerId={}", runtime.getPlayerId(), e);
        }
    }

    private RogueRuntime loadFromRedis(long playerId) {
        if (redis == null || objectMapper == null) {
            return null;
        }
        try {
            String json = redis.opsForValue().get(REDIS_KEY + playerId);
            if (json == null || json.isBlank()) {
                return null;
            }
            Snapshot snap = objectMapper.readValue(json, Snapshot.class);
            return snap == null ? null : snap.toRuntime();
        } catch (Exception e) {
            log.warn("肉鸽进度读取 Redis 失败 playerId={}", playerId, e);
            return null;
        }
    }

    private void deleteFromRedis(long playerId) {
        if (redis == null) {
            return;
        }
        try {
            redis.delete(REDIS_KEY + playerId);
        } catch (Exception e) {
            log.warn("肉鸽进度删除 Redis 失败 playerId={}", playerId, e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Snapshot {
        public long playerId;
        public int rogueId;
        public int difficulty;
        public int floor;
        public int wave;
        public int roomId;
        public int currency;
        public long battleId;
        public int talentPoints;
        public List<Integer> blessings = new ArrayList<>();
        public Map<Integer, Integer> talents = new HashMap<>();

        static Snapshot from(RogueRuntime r) {
            Snapshot s = new Snapshot();
            s.playerId = r.getPlayerId();
            s.rogueId = r.getRogueId();
            s.difficulty = r.getDifficulty();
            s.floor = r.getFloor();
            s.wave = r.getWave();
            s.roomId = r.getRoomId();
            s.currency = r.getCurrency();
            s.battleId = r.getBattleId();
            s.talentPoints = r.getTalentPoints();
            s.blessings = new ArrayList<>(r.getBlessings());
            s.talents = new HashMap<>(r.getTalents());
            return s;
        }

        RogueRuntime toRuntime() {
            RogueRuntime r = new RogueRuntime(playerId, rogueId, difficulty);
            r.restoreState(floor, wave, roomId, currency, battleId, blessings, talentPoints, talents);
            return r;
        }
    }
}
