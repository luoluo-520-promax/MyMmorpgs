package cn.itcast.demo.mymmorpg.persistence;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 历史数据冷热分离：Redis 保留热数据，超期自动标记 OSS 归档；支持分片表路由。
 */
@Component
public class HistoricalDataRetention {

    public static final long HOT_REPLAY_MS = 3L * 24 * 60 * 60 * 1000;
    public static final long COLD_ARCHIVE_MS = 7L * 24 * 60 * 60 * 1000;
    public static final int SHARD_COUNT = 256;

    private final ConcurrentHashMap<String, Long> hotUntil = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> archivedUri = new ConcurrentHashMap<>();
    private final AtomicLong archivedCount = new AtomicLong();
    private final AtomicLong evictedHot = new AtomicLong();

    public void markHot(String key, long nowMs) {
        hotUntil.put(key, nowMs + HOT_REPLAY_MS);
    }

    /**
     * 超过热数据窗口则返回 OSS URI 并移出热区。
     */
    public Map<String, Object> archiveIfCold(String key, long nowMs, int entryCount) {
        Long until = hotUntil.get(key);
        if (until == null) {
            return Map.of("ok", false, "error", "not_tracked");
        }
        if (nowMs < until) {
            return Map.of("ok", false, "error", "still_hot", "hotUntilMs", until);
        }
        if (nowMs - (until - HOT_REPLAY_MS) < COLD_ARCHIVE_MS) {
            return Map.of("ok", false, "error", "not_expired_for_archive");
        }
        String uri = "oss://cold/" + shardTable(key) + "/" + key + ".pb";
        archivedUri.put(key, uri);
        hotUntil.remove(key);
        archivedCount.incrementAndGet();
        evictedHot.incrementAndGet();
        return Map.of("ok", true, "key", key, "uri", uri, "entries", entryCount,
                "shardTable", shardTable(key), "format", "protobuf");
    }

    public String shardTable(String key) {
        int shard = Math.floorMod(key == null ? 0 : key.hashCode(), SHARD_COUNT);
        return "player_chronicle_" + shard;
    }

    public String shardTable(long playerId) {
        int shard = Math.floorMod(playerId, SHARD_COUNT);
        return "guild_boss_damage_" + shard;
    }

    public boolean isArchived(String key) {
        return archivedUri.containsKey(key);
    }

    public String archivedUri(String key) {
        return archivedUri.get(key);
    }

    public List<String> listHotKeys() {
        return new ArrayList<>(hotUntil.keySet());
    }

    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hotKeys", hotUntil.size());
        m.put("archivedKeys", archivedUri.size());
        m.put("archivedCount", archivedCount.get());
        m.put("evictedHot", evictedHot.get());
        m.put("shardCount", SHARD_COUNT);
        m.put("hotReplayDays", 3);
        m.put("coldArchiveDays", 7);
        return m;
    }
}
