package cn.itcast.demo.mymmorpg.activity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 活动善后处理器：缓冲期只出不进，逾期代币按比例转化摩拉并邮件通知。
 */
@Component
public class PostMortemProcessor {

    private static final Logger log = LoggerFactory.getLogger(PostMortemProcessor.class);
    private static final long BUFFER_MS = 3L * 24 * 60 * 60 * 1000;
    private static final int MORA_RATIO = 10;

    public record ExpiredTokenRule(long activityId, long endAtMs, int tokenItemId, int moraPerToken) {
    }

    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final ConcurrentHashMap<Long, ExpiredTokenRule> rules = new ConcurrentHashMap<>();

    public PostMortemProcessor(ObjectProvider<StringRedisTemplate> redisProvider) {
        this.redisProvider = redisProvider;
    }

    public void registerRule(ExpiredTokenRule rule) {
        if (rule != null && rule.activityId() > 0) {
            rules.put(rule.activityId(), rule);
        }
    }

    /** 缓冲期内：禁止新增代币（只出不进）。 */
    public boolean isExchangeOnlyPeriod(long activityId, long nowMs) {
        ExpiredTokenRule rule = rules.get(activityId);
        if (rule == null) {
            return false;
        }
        return nowMs >= rule.endAtMs() && nowMs < rule.endAtMs() + BUFFER_MS;
    }

    /** 缓冲期结束后扫描并回收过期代币。 */
    @Scheduled(cron = "0 30 4 * * ?")
    public void processExpiredTokensDaily() {
        long now = System.currentTimeMillis();
        Map<String, Object> summary = sweepExpiredTokens(now);
        log.info("PostMortemProcessor daily sweep: {}", summary);
    }

    public Map<String, Object> sweepExpiredTokens(long nowMs) {
        int converted = 0;
        int deletedKeys = 0;
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        for (ExpiredTokenRule rule : rules.values()) {
            if (nowMs < rule.endAtMs() + BUFFER_MS) {
                continue;
            }
            if (redis != null) {
                try {
                    Set<String> keys = redis.keys("activity:token:*");
                    if (keys != null) {
                        for (String key : keys) {
                            Map<Object, Object> tokens = redis.opsForHash().entries(key);
                            long playerId = parsePlayerId(key);
                            long mora = 0;
                            for (Map.Entry<Object, Object> e : tokens.entrySet()) {
                                if (String.valueOf(e.getKey()).contains(String.valueOf(rule.tokenItemId()))) {
                                    long count = parseLong(e.getValue());
                                    mora += (count / MORA_RATIO) * rule.moraPerToken();
                                    redis.opsForHash().delete(key, e.getKey());
                                    converted += (int) count;
                                }
                            }
                            if (mora > 0) {
                                queueCompensationMail(redis, playerId, mora, rule.activityId());
                            }
                            if (tokens.isEmpty()) {
                                redis.delete(key);
                                deletedKeys++;
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("sweepExpiredTokens failed activityId={}: {}", rule.activityId(), e.getMessage());
                }
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("convertedTokens", converted);
        out.put("deletedKeys", deletedKeys);
        out.put("mailTemplate", "《逾期代币回收说明》");
        return out;
    }

    private void queueCompensationMail(StringRedisTemplate redis, long playerId, long mora, long activityId) {
        try {
            String mailKey = "mail:pending:" + playerId;
            Map<String, String> mail = Map.of(
                    "subject", "逾期代币回收说明",
                    "body", "活动 " + activityId + " 已结束超过 3 天，未兑换代币已按 "
                            + MORA_RATIO + ":1 转化为摩拉 " + mora,
                    "mora", String.valueOf(mora));
            redis.opsForHash().putAll(mailKey, mail);
        } catch (Exception ignored) {
            // 邮件队列不可用时跳过
        }
    }

    private static long parsePlayerId(String key) {
        int idx = key.lastIndexOf(':');
        if (idx < 0) {
            return 0L;
        }
        try {
            return Long.parseLong(key.substring(idx + 1));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static long parseLong(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
