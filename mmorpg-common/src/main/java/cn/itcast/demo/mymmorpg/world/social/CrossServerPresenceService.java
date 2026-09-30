package cn.itcast.demo.mymmorpg.world.social;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 跨服社交元数据：在线态 / 所在节点 / 显示名，补齐仅 SET 存好友 ID 的不足。
 */
@Service
public class CrossServerPresenceService {

    private static final String PRESENCE_KEY = "cross:presence:";
    private static final String RANK_KEY = "cross:rank:";
    private static final Duration TTL = Duration.ofMinutes(15);

    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final ConcurrentHashMap<Long, Presence> local = new ConcurrentHashMap<>();

    public record Presence(
            long playerId,
            String displayName,
            String nodeId,
            int sceneId,
            boolean online,
            long updatedAtMs) {
    }

    public CrossServerPresenceService(ObjectProvider<StringRedisTemplate> redisProvider) {
        this.redisProvider = redisProvider;
    }

    public CrossServerPresenceService() {
        this.redisProvider = null;
    }

    public void heartbeat(long playerId, String displayName, String nodeId, int sceneId, long nowMs) {
        Presence p = new Presence(playerId, displayName == null ? "" : displayName,
                nodeId == null ? "local" : nodeId, sceneId, true, nowMs);
        StringRedisTemplate redis = redis();
        if (redis != null) {
            try {
                String key = PRESENCE_KEY + playerId;
                redis.opsForHash().putAll(key, Map.of(
                        "displayName", p.displayName(),
                        "nodeId", p.nodeId(),
                        "sceneId", String.valueOf(sceneId),
                        "online", "1",
                        "updatedAtMs", String.valueOf(nowMs)));
                redis.expire(key, TTL);
                return;
            } catch (Exception ignored) {
                // fallback
            }
        }
        local.put(playerId, p);
    }

    public void offline(long playerId) {
        StringRedisTemplate redis = redis();
        if (redis != null) {
            try {
                redis.delete(PRESENCE_KEY + playerId);
            } catch (Exception ignored) {
                // ignore
            }
        }
        local.remove(playerId);
    }

    public Presence get(long playerId) {
        StringRedisTemplate redis = redis();
        if (redis != null) {
            try {
                Map<Object, Object> m = redis.opsForHash().entries(PRESENCE_KEY + playerId);
                if (m != null && !m.isEmpty()) {
                    return new Presence(
                            playerId,
                            String.valueOf(m.getOrDefault("displayName", "")),
                            String.valueOf(m.getOrDefault("nodeId", "")),
                            parseInt(m.get("sceneId")),
                            "1".equals(String.valueOf(m.get("online"))),
                            parseLong(m.get("updatedAtMs")));
                }
            } catch (Exception ignored) {
                // fallback
            }
        }
        return local.get(playerId);
    }

    public List<Map<String, Object>> enrichFriends(List<Long> friendIds) {
        return enrichFriends(friendIds, null);
    }

    public List<Map<String, Object>> enrichFriends(List<Long> friendIds, FriendOnlineLocalCache localCache) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (friendIds == null) {
            return out;
        }
        for (Long id : friendIds) {
            Presence p = get(id);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("friendId", id);
            boolean online;
            if (localCache != null) {
                online = localCache.isOnline(id);
            } else {
                online = p != null && p.online();
            }
            row.put("online", online);
            if (p != null) {
                row.put("displayName", p.displayName());
                row.put("nodeId", p.nodeId());
                row.put("sceneId", p.sceneId());
                row.put("updatedAtMs", p.updatedAtMs());
            }
            out.add(row);
        }
        return out;
    }

    /** 跨服排行榜分数同步（ZSET）。 */
    public void upsertRankScore(String board, long playerId, double score) {
        StringRedisTemplate redis = redis();
        if (redis != null) {
            try {
                redis.opsForZSet().add(RANK_KEY + board, String.valueOf(playerId), score);
                redis.expire(RANK_KEY + board, Duration.ofDays(30));
                return;
            } catch (Exception ignored) {
                // ignore
            }
        }
        // 本地仅作演示：用 presence map 不存排行
    }

    public List<Map<String, Object>> topRank(String board, int limit) {
        StringRedisTemplate redis = redis();
        List<Map<String, Object>> out = new ArrayList<>();
        if (redis == null) {
            return out;
        }
        try {
            var set = redis.opsForZSet().reverseRangeWithScores(RANK_KEY + board, 0, Math.max(0, limit - 1L));
            if (set == null) {
                return out;
            }
            int rank = 1;
            for (var t : set) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("rank", rank++);
                row.put("playerId", Long.parseLong(String.valueOf(t.getValue())));
                row.put("score", t.getScore());
                out.add(row);
            }
        } catch (Exception ignored) {
            // empty
        }
        return out;
    }

    private StringRedisTemplate redis() {
        return redisProvider == null ? null : redisProvider.getIfAvailable();
    }

    private static int parseInt(Object v) {
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return 0;
        }
    }

    private static long parseLong(Object v) {
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return 0L;
        }
    }
}
