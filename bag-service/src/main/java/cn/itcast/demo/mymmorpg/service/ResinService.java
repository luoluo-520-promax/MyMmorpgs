package cn.itcast.demo.mymmorpg.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 体力（Resin）服务：自然恢复 + 每日购买次数上限，驱动副本产出节流。
 */
@Service
public class ResinService {

    public static final int MAX_RESIN = 160;
    public static final long REGEN_INTERVAL_MS = 8 * 60_000L; // 约 8 分钟回 1 点
    public static final int DAILY_BUY_LIMIT = 6;
    public static final int BUY_AMOUNT = 60;

    private static final String KEY_CUR = "resin:cur:";
    private static final String KEY_TS = "resin:ts:";
    private static final String KEY_BUY = "resin:buy:";

    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final Map<Long, int[]> localCur = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Long, Long> localTs = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Integer> localBuy = new java.util.concurrent.ConcurrentHashMap<>();

    public ResinService(ObjectProvider<StringRedisTemplate> redisProvider) {
        this.redisProvider = redisProvider;
    }

    public Map<String, Object> status(long playerId) {
        regen(playerId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("playerId", playerId);
        out.put("current", getCur(playerId));
        out.put("max", MAX_RESIN);
        out.put("nextRegenMs", nextRegenMs(playerId));
        out.put("buyCountToday", getBuyCount(playerId));
        out.put("buyLimit", DAILY_BUY_LIMIT);
        return out;
    }

    public Map<String, Object> consume(long playerId, int amount) {
        if (playerId <= 0 || amount <= 0) {
            return Map.of("ok", false, "error", "invalid_args");
        }
        regen(playerId);
        int cur = getCur(playerId);
        if (cur < amount) {
            return Map.of("ok", false, "error", "resin_not_enough", "current", cur);
        }
        setCur(playerId, cur - amount);
        return status(playerId);
    }

    public Map<String, Object> buy(long playerId) {
        if (playerId <= 0) {
            return Map.of("ok", false, "error", "invalid_player");
        }
        regen(playerId);
        int bought = getBuyCount(playerId);
        if (bought >= DAILY_BUY_LIMIT) {
            return Map.of("ok", false, "error", "buy_limit", "buyCountToday", bought);
        }
        int cur = getCur(playerId);
        int next = Math.min(MAX_RESIN, cur + BUY_AMOUNT);
        setCur(playerId, next);
        setBuyCount(playerId, bought + 1);
        Map<String, Object> out = new LinkedHashMap<>(status(playerId));
        out.put("bought", BUY_AMOUNT);
        return out;
    }

    private void regen(long playerId) {
        long now = System.currentTimeMillis();
        long last = getTs(playerId);
        int cur = getCur(playerId);
        if (last <= 0) {
            setCur(playerId, MAX_RESIN);
            setTs(playerId, now);
            return;
        }
        if (cur >= MAX_RESIN) {
            setTs(playerId, now);
            return;
        }
        long elapsed = Math.max(0L, now - last);
        int gained = (int) (elapsed / REGEN_INTERVAL_MS);
        if (gained <= 0) {
            return;
        }
        int next = Math.min(MAX_RESIN, cur + gained);
        setCur(playerId, next);
        setTs(playerId, last + gained * REGEN_INTERVAL_MS);
    }

    private long nextRegenMs(long playerId) {
        int cur = getCur(playerId);
        if (cur >= MAX_RESIN) {
            return 0L;
        }
        long last = getTs(playerId);
        long nextAt = last + REGEN_INTERVAL_MS;
        return Math.max(0L, nextAt - System.currentTimeMillis());
    }

    private int getCur(long playerId) {
        StringRedisTemplate redis = redis();
        if (redis != null) {
            String v = redis.opsForValue().get(KEY_CUR + playerId);
            if (v != null) {
                return Integer.parseInt(v);
            }
            redis.opsForValue().set(KEY_CUR + playerId, String.valueOf(MAX_RESIN), Duration.ofDays(30));
            return MAX_RESIN;
        }
        return localCur.computeIfAbsent(playerId, id -> new int[]{MAX_RESIN})[0];
    }

    private void setCur(long playerId, int value) {
        StringRedisTemplate redis = redis();
        if (redis != null) {
            redis.opsForValue().set(KEY_CUR + playerId, String.valueOf(value), Duration.ofDays(30));
            return;
        }
        localCur.put(playerId, new int[]{value});
    }

    private long getTs(long playerId) {
        StringRedisTemplate redis = redis();
        if (redis != null) {
            String v = redis.opsForValue().get(KEY_TS + playerId);
            return v == null ? 0L : Long.parseLong(v);
        }
        return localTs.getOrDefault(playerId, 0L);
    }

    private void setTs(long playerId, long ts) {
        StringRedisTemplate redis = redis();
        if (redis != null) {
            redis.opsForValue().set(KEY_TS + playerId, String.valueOf(ts), Duration.ofDays(30));
            return;
        }
        localTs.put(playerId, ts);
    }

    private int getBuyCount(long playerId) {
        String day = LocalDate.now().toString();
        String key = day + ":" + playerId;
        StringRedisTemplate redis = redis();
        if (redis != null) {
            String v = redis.opsForValue().get(KEY_BUY + key);
            return v == null ? 0 : Integer.parseInt(v);
        }
        return localBuy.getOrDefault(key, 0);
    }

    private void setBuyCount(long playerId, int count) {
        String day = LocalDate.now().toString();
        String key = day + ":" + playerId;
        StringRedisTemplate redis = redis();
        if (redis != null) {
            redis.opsForValue().set(KEY_BUY + key, String.valueOf(count), Duration.ofDays(2));
            return;
        }
        localBuy.put(key, count);
    }

    private StringRedisTemplate redis() {
        return redisProvider == null ? null : redisProvider.getIfAvailable();
    }
}
