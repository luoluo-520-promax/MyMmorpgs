package cn.itcast.demo.mymmorpg.activity;

import cn.itcast.demo.mymmorpg.entity.ActivitySnapshot;
import cn.itcast.demo.mymmorpg.repository.ActivitySnapshotRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 活动/背包关键数据快照与版本回退：溢出差值扣除 + 补偿邮件说明。
 */
@Service
public class ActivitySnapshotManager {

    private static final Logger log = LoggerFactory.getLogger(ActivitySnapshotManager.class);
    public static final String TYPE_PROGRESS = "activity_progress";
    public static final String TYPE_TOKEN = "activity_token";
    public static final String TYPE_BAG_TOKEN = "bag_activity_token";

    private final ActivitySnapshotRepository snapshotRepository;
    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<Long, Long> activePlayerIds = new ConcurrentHashMap<>();

    public ActivitySnapshotManager(
            ActivitySnapshotRepository snapshotRepository,
            ObjectProvider<StringRedisTemplate> redisProvider,
            ObjectMapper objectMapper) {
        this.snapshotRepository = snapshotRepository;
        this.redisProvider = redisProvider;
        this.objectMapper = objectMapper;
    }

    public void trackActivePlayer(long playerId) {
        if (playerId > 0) {
            activePlayerIds.put(playerId, System.currentTimeMillis());
        }
    }

    /** 版本更新前 5 分钟：异步快照活跃玩家关键活动数据。 */
    public Map<String, Object> snapshotActivePlayers(String versionCode) {
        long now = System.currentTimeMillis();
        List<Long> playerIds = new ArrayList<>(activePlayerIds.keySet());
        int saved = 0;
        for (Long playerId : playerIds) {
            if (snapshotPlayer(playerId, versionCode, now)) {
                saved++;
            }
        }
        return Map.of("ok", true, "versionCode", versionCode, "playerCount", playerIds.size(), "saved", saved);
    }

    public boolean snapshotPlayer(long playerId, String versionCode, long nowMs) {
        try {
            Map<String, Object> progress = readProgress(playerId);
            if (!progress.isEmpty()) {
                persist(playerId, versionCode, TYPE_PROGRESS, progress, nowMs);
            }
            Map<String, Object> tokens = readTokens(playerId);
            if (!tokens.isEmpty()) {
                persist(playerId, versionCode, TYPE_TOKEN, tokens, nowMs);
            }
            return true;
        } catch (Exception e) {
            log.warn("snapshotPlayer failed playerId={}: {}", playerId, e.getMessage());
            return false;
        }
    }

    /** Admin 版本回退：超出快照阈值则差值扣除并记录补偿邮件。 */
    @Transactional
    public Map<String, Object> rollbackPlayerData(long playerId, String versionCode) {
        List<Map<String, Object>> adjustments = new ArrayList<>();
        snapshotRepository.findByPlayerIdAndVersionCodeOrderByCreatedAtMsDesc(playerId, versionCode)
                .forEach(snap -> applyRollback(playerId, snap, adjustments));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("playerId", playerId);
        out.put("versionCode", versionCode);
        out.put("adjustments", adjustments);
        out.put("compensationMailSent", !adjustments.isEmpty());
        if (!adjustments.isEmpty()) {
            out.put("mailSubject", "版本回退补偿说明");
            out.put("mailBody", "因活动配置异常已执行数据回退，详见 adjustments 字段。");
        }
        return out;
    }

    private void applyRollback(long playerId, ActivitySnapshot snap, List<Map<String, Object>> adjustments) {
        try {
            Map<String, Object> baseline = objectMapper.readValue(
                    snap.getPayloadJson(), new TypeReference<Map<String, Object>>() {});
            Map<String, Object> current = TYPE_TOKEN.equals(snap.getSnapshotType())
                    ? readTokens(playerId) : readProgress(playerId);
            for (Map.Entry<String, Object> entry : current.entrySet()) {
                long cur = toLong(entry.getValue());
                long base = toLong(baseline.get(entry.getKey()));
                if (cur > base) {
                    long delta = cur - base;
                    writeBack(playerId, snap.getSnapshotType(), entry.getKey(), base);
                    Map<String, Object> adj = new LinkedHashMap<>();
                    adj.put("field", entry.getKey());
                    adj.put("before", cur);
                    adj.put("after", base);
                    adj.put("deducted", delta);
                    adjustments.add(adj);
                }
            }
        } catch (Exception e) {
            log.warn("applyRollback failed snapId={}: {}", snap.getId(), e.getMessage());
        }
    }

    private void persist(long playerId, String versionCode, String type, Map<String, Object> payload, long nowMs)
            throws Exception {
        ActivitySnapshot snap = new ActivitySnapshot();
        snap.setPlayerId(playerId);
        snap.setVersionCode(versionCode);
        snap.setSnapshotType(type);
        snap.setPayloadJson(objectMapper.writeValueAsString(payload));
        snap.setCreatedAtMs(nowMs);
        snapshotRepository.save(snap);
    }

    private Map<String, Object> readProgress(long playerId) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            return Map.of();
        }
        try {
            Map<Object, Object> hash = redis.opsForHash().entries("player:act:" + playerId);
            Map<String, Object> out = new LinkedHashMap<>();
            hash.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Map<String, Object> readTokens(long playerId) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            return Map.of();
        }
        try {
            Map<Object, Object> hash = redis.opsForHash().entries("activity:token:" + playerId);
            Map<String, Object> out = new LinkedHashMap<>();
            hash.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private void writeBack(long playerId, String type, String field, long value) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            return;
        }
        String key = TYPE_TOKEN.equals(type) || TYPE_BAG_TOKEN.equals(type)
                ? "activity:token:" + playerId : "player:act:" + playerId;
        try {
            redis.opsForHash().put(key, field, String.valueOf(value));
        } catch (Exception ignored) {
            // 不阻断回滚主流程
        }
    }

    private static long toLong(Object v) {
        if (v == null) {
            return 0L;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public Set<Long> activePlayerIds() {
        return Set.copyOf(activePlayerIds.keySet());
    }
}
