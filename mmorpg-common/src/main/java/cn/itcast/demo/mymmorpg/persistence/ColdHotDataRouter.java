package cn.itcast.demo.mymmorpg.persistence;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 冷热数据路由：Redis 仅存「当前在线 + 近 10 分钟」热数据；
 * 离线公会远征伤害、深渊星数等冷数据直接走 MySQL Insert on Duplicate。
 */
@Component
public class ColdHotDataRouter {

    public enum DataTier {
        HOT_REDIS,
        COLD_MYSQL
    }

    public enum DataKind {
        SCENE_POSITION,
        ABYSS_STARS,
        GUILD_BOSS_DAMAGE,
        PLAYER_BAG,
        GENERIC
    }

    public static final long HOT_WINDOW_MS = 10L * 60 * 1000;

    private final ConcurrentHashMap<String, Long> hotUntil = new ConcurrentHashMap<>();
    private final AtomicLong hotWrites = new AtomicLong();
    private final AtomicLong coldWrites = new AtomicLong();

    public DataTier route(DataKind kind, long playerId, long nowMs) {
        if (kind == DataKind.SCENE_POSITION) {
            markHot(key(kind, playerId), nowMs);
            return DataTier.HOT_REDIS;
        }
        if (kind == DataKind.ABYSS_STARS || kind == DataKind.GUILD_BOSS_DAMAGE) {
            return DataTier.COLD_MYSQL;
        }
        String k = key(kind, playerId);
        Long until = hotUntil.get(k);
        if (until != null && nowMs < until) {
            return DataTier.HOT_REDIS;
        }
        return DataTier.COLD_MYSQL;
    }

    public void markHot(String key, long nowMs) {
        hotUntil.put(key, nowMs + HOT_WINDOW_MS);
    }

    public <T> void write(DataKind kind, long playerId, T payload, long nowMs,
                          Consumer<T> redisWriter, Consumer<T> mysqlWriter) {
        DataTier tier = route(kind, playerId, nowMs);
        if (tier == DataTier.HOT_REDIS && redisWriter != null) {
            redisWriter.accept(payload);
            hotWrites.incrementAndGet();
        } else if (mysqlWriter != null) {
            mysqlWriter.accept(payload);
            coldWrites.incrementAndGet();
        }
    }

    public String key(DataKind kind, long playerId) {
        return kind.name().toLowerCase() + ":" + playerId;
    }

    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hotKeys", hotUntil.size());
        m.put("hotWrites", hotWrites.get());
        m.put("coldWrites", coldWrites.get());
        m.put("hotWindowMs", HOT_WINDOW_MS);
        return m;
    }
}
