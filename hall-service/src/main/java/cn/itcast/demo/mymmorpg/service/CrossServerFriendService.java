package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.world.social.CrossServerPresenceService;
import cn.itcast.demo.mymmorpg.world.social.FriendOnlineLocalCache;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 跨服好友：存 Redis SET，并叠加在线态/节点/排行榜同步，便于合服/跨服社交。
 */
@Service
public class CrossServerFriendService {

    private static final String KEY = "cross:friend:";
    private static final Duration TTL = Duration.ofDays(90);

    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final CrossServerPresenceService presenceService;
    private final FriendOnlineLocalCache friendOnlineLocalCache;
    private final ConcurrentHashMap<Long, Set<Long>> local = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public CrossServerFriendService(ObjectProvider<StringRedisTemplate> redisProvider,
                                    ObjectProvider<CrossServerPresenceService> presenceProvider,
                                    ObjectProvider<FriendOnlineLocalCache> cacheProvider) {
        this.redisProvider = redisProvider;
        CrossServerPresenceService p = presenceProvider == null ? null : presenceProvider.getIfAvailable();
        this.presenceService = p != null ? p : new CrossServerPresenceService(redisProvider);
        this.friendOnlineLocalCache = cacheProvider == null ? null : cacheProvider.getIfAvailable();
    }

    /** 单测便捷构造。 */
    public CrossServerFriendService(ObjectProvider<StringRedisTemplate> redisProvider) {
        this.redisProvider = redisProvider;
        this.presenceService = new CrossServerPresenceService(redisProvider);
        this.friendOnlineLocalCache = null;
    }

    public Map<String, Object> addFriend(long playerId, long friendId) {
        if (playerId <= 0 || friendId <= 0 || playerId == friendId) {
            return Map.of("ok", false, "error", "invalid_args");
        }
        add(playerId, friendId);
        add(friendId, playerId);
        return Map.of("ok", true, "playerId", playerId, "friendId", friendId);
    }

    public Map<String, Object> removeFriend(long playerId, long friendId) {
        remove(playerId, friendId);
        remove(friendId, playerId);
        return Map.of("ok", true);
    }

    public List<Long> listFriends(long playerId) {
        StringRedisTemplate redis = redis();
        if (redis != null) {
            Set<String> members = redis.opsForSet().members(KEY + playerId);
            if (members == null || members.isEmpty()) {
                return List.of();
            }
            List<Long> out = new ArrayList<>();
            for (String m : members) {
                out.add(Long.parseLong(m));
            }
            return out;
        }
        return List.copyOf(local.getOrDefault(playerId, Set.of()));
    }

    public Map<String, Object> toView(long playerId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("playerId", playerId);
        List<Long> friends = listFriends(playerId);
        m.put("friends", friends);
        m.put("friendsDetailed", presence().enrichFriends(friends, friendOnlineLocalCache));
        m.put("storage", "redis_cross_server");
        return m;
    }

    public Map<String, Object> presenceHeartbeat(long playerId, String displayName, String nodeId, int sceneId) {
        presence().heartbeat(playerId, displayName, nodeId, sceneId, System.currentTimeMillis());
        return Map.of("ok", true, "playerId", playerId);
    }

    public List<Map<String, Object>> topRank(String board, int limit) {
        return presence().topRank(board, limit);
    }

    public void upsertRank(String board, long playerId, double score) {
        presence().upsertRankScore(board, playerId, score);
    }

    private CrossServerPresenceService presence() {
        if (presenceService != null) {
            return presenceService;
        }
        return new CrossServerPresenceService(redisProvider);
    }

    private void add(long playerId, long friendId) {
        StringRedisTemplate redis = redis();
        if (redis != null) {
            String key = KEY + playerId;
            redis.opsForSet().add(key, String.valueOf(friendId));
            redis.expire(key, TTL);
            return;
        }
        local.computeIfAbsent(playerId, id -> ConcurrentHashMap.newKeySet()).add(friendId);
    }

    private void remove(long playerId, long friendId) {
        StringRedisTemplate redis = redis();
        if (redis != null) {
            redis.opsForSet().remove(KEY + playerId, String.valueOf(friendId));
            return;
        }
        Set<Long> set = local.get(playerId);
        if (set != null) {
            set.remove(friendId);
        }
    }

    private StringRedisTemplate redis() {
        return redisProvider == null ? null : redisProvider.getIfAvailable();
    }
}
