/**
 * 文件维护说明
 * 1) 文件路径：activity-service/src/main/java/cn/itcast/demo/mymmorpg/service/ActivityPlayerProgressStore.java
 * 2) 所属模块：activity-service / service
 * 3) 主要职责：封装玩家活动进度在 Redis Hash 中的读写与 TTL 管理
 * 4) 系统位置：基础设施层，被 ActivityService 在列表/详情/领奖流程中使用
 * 5) 风险提示：写 Redis 失败时静默忽略，不阻断主流程
 */
package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.model.PlayerActivityProgress;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * 玩家活动进度：优先 Redis Hash {@code player:act:{uid}} field={actId}；
 * 兼容旧 Key {@code activity:prog:{playerId}:{activityId}}。
 */
@Component
public class ActivityPlayerProgressStore {

    private static final Duration TTL = Duration.ofDays(120);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public ActivityPlayerProgressStore(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    public static String hashKey(long playerId) {
        return "player:act:" + playerId;
    }

    /** 旧版单 Key，迁移读兼容。 */
    public static String legacyRedisKey(long playerId, long activityId) {
        return "activity:prog:" + playerId + ":" + activityId;
    }

    /** @deprecated 使用 {@link #legacyRedisKey(long, long)} */
    @Deprecated
    public static String redisKey(long playerId, long activityId) {
        return legacyRedisKey(playerId, activityId);
    }

    public Optional<PlayerActivityProgress> load(long playerId, long activityId) {
        String field = String.valueOf(activityId);
        Object raw = stringRedisTemplate.opsForHash().get(hashKey(playerId), field);
        String json = raw == null ? null : String.valueOf(raw);
        if (json == null || json.isBlank()) {
            json = stringRedisTemplate.opsForValue().get(legacyRedisKey(playerId, activityId));
            if (json != null && !json.isBlank()) {
                try {
                    stringRedisTemplate.opsForHash().put(hashKey(playerId), field, json);
                    stringRedisTemplate.expire(hashKey(playerId), TTL);
                    stringRedisTemplate.delete(legacyRedisKey(playerId, activityId));
                } catch (Exception ignored) {
                    // 迁移失败不影响读
                }
            }
        }
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, PlayerActivityProgress.class));
        } catch (Exception e) {
            return Optional.of(new PlayerActivityProgress());
        }
    }

    public PlayerActivityProgress loadOrCreate(long playerId, long activityId) {
        return load(playerId, activityId).orElseGet(PlayerActivityProgress::new);
    }

    public void save(long playerId, long activityId, PlayerActivityProgress progress) {
        try {
            String json = objectMapper.writeValueAsString(progress);
            stringRedisTemplate.opsForHash().put(hashKey(playerId), String.valueOf(activityId), json);
            stringRedisTemplate.expire(hashKey(playerId), TTL);
        } catch (Exception ignored) {
            // 不阻断主流程
        }
    }
}
