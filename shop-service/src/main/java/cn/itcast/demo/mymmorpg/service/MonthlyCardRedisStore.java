package cn.itcast.demo.mymmorpg.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/**
 * 月卡剩余天数 Redis 存储：key {@code pass:mc:days:{playerId}}。
 * 每日零点扣 1；激活/续费写天数；剩余 &gt; 180 拒绝续费。
 */
@Component
public class MonthlyCardRedisStore {

    private static final Logger log = LoggerFactory.getLogger(MonthlyCardRedisStore.class);
    public static final String KEY_PREFIX = "pass:mc:days:";
    public static final int MAX_REMAINING_FOR_RENEW = 180;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectProvider<PassService> passService;

    public MonthlyCardRedisStore(StringRedisTemplate stringRedisTemplate,
                                 ObjectProvider<PassService> passService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.passService = passService;
    }

    public static String key(long playerId) {
        return KEY_PREFIX + playerId;
    }

    public int remainingDays(long playerId) {
        String raw = stringRedisTemplate.opsForValue().get(key(playerId));
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * @return true 成功；false 因剩余天数 &gt; 180 拒绝
     */
    public boolean activateOrRenew(long playerId, int days) {
        int add = Math.max(1, days);
        int cur = remainingDays(playerId);
        if (cur > MAX_REMAINING_FOR_RENEW) {
            return false;
        }
        int next = cur + add;
        stringRedisTemplate.opsForValue().set(key(playerId), String.valueOf(next), Duration.ofDays(400));
        return true;
    }

    /** 每日零点（Asia/Shanghai）全量扣 1 天。 */
    @Scheduled(cron = "${game.pass.monthly-card.deduct-cron:0 0 0 * * *}", zone = "${game.pass.monthly-card.timezone:Asia/Shanghai}")
    public void deductOneDayAtMidnight() {
        Set<String> keys = stringRedisTemplate.keys(KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return;
        }
        int deducted = 0;
        for (String k : keys) {
            Long left = stringRedisTemplate.opsForValue().decrement(k);
            if (left == null || left <= 0) {
                stringRedisTemplate.delete(k);
                long playerId = parsePlayerId(k);
                if (playerId > 0) {
                    PassService pass = passService.getIfAvailable();
                    if (pass != null) {
                        pass.onMonthlyCardDaysExhausted(playerId);
                    }
                }
            } else {
                deducted++;
            }
        }
        log.info("monthly card daily deduct done activeKeys≈{}", deducted);
    }

    private static long parsePlayerId(String redisKey) {
        if (redisKey == null || !redisKey.startsWith(KEY_PREFIX)) {
            return 0L;
        }
        try {
            return Long.parseLong(redisKey.substring(KEY_PREFIX.length()));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
