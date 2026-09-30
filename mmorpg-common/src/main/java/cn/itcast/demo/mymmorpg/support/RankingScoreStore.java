package cn.itcast.demo.mymmorpg.support;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 排行榜 Redis ZSET：{@code rank:level} / {@code rank:power}。
 */
@Component
public class RankingScoreStore {

    public static final String KEY_LEVEL = "rank:level";
    public static final String KEY_POWER = "rank:power";

    private StringRedisTemplate redis;

    public RankingScoreStore() {
    }

    @Autowired
    public void bindRedis(ObjectProvider<StringRedisTemplate> redisProvider) {
        this.redis = redisProvider.getIfAvailable();
    }

    public void updateLevel(long playerId, int level) {
        zAdd(KEY_LEVEL, playerId, level);
    }

    public void updatePower(long playerId, int power) {
        zAdd(KEY_POWER, playerId, power);
    }

    public void updatePlayer(long playerId, int level, int power) {
        updateLevel(playerId, level);
        updatePower(playerId, power);
    }

    public List<Entry> top(String key, int limit) {
        if (redis == null || limit <= 0) {
            return List.of();
        }
        try {
            Set<ZSetOperations.TypedTuple<String>> tuples =
                    redis.opsForZSet().reverseRangeWithScores(key, 0, limit - 1L);
            if (tuples == null || tuples.isEmpty()) {
                return List.of();
            }
            List<Entry> out = new ArrayList<>();
            for (ZSetOperations.TypedTuple<String> t : tuples) {
                if (t.getValue() == null) {
                    continue;
                }
                long playerId = Long.parseLong(t.getValue());
                double score = t.getScore() == null ? 0 : t.getScore();
                out.add(new Entry(playerId, score));
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    public boolean available() {
        return redis != null;
    }

    private void zAdd(String key, long playerId, double score) {
        if (redis == null || playerId <= 0) {
            return;
        }
        try {
            redis.opsForZSet().add(key, String.valueOf(playerId), score);
        } catch (Exception ignored) {
            // 排行写失败不影响主流程
        }
    }

    public record Entry(long playerId, double score) {
    }
}
