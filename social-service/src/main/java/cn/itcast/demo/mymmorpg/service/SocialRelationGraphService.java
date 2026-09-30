package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.PlayerFriend;
import cn.itcast.demo.mymmorpg.repository.PlayerFriendRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 统一社交关系图谱：好友边 + 黑名单 + 亲密度（Redis 边属性，好友边复用 player_friend）。
 */
@Service
public class SocialRelationGraphService {

    private static final String KEY_INTIMACY = "social:intimacy:";
    private static final String KEY_BLOCK = "social:block:";
    private static final Duration INTIMACY_TTL = Duration.ofDays(365);

    private final PlayerFriendRepository playerFriendRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final SocialEventPublisher socialEventPublisher;
    private final SocialAchievementService socialAchievementService;

    public SocialRelationGraphService(PlayerFriendRepository playerFriendRepository,
                                      StringRedisTemplate stringRedisTemplate,
                                      SocialEventPublisher socialEventPublisher,
                                      SocialAchievementService socialAchievementService) {
        this.playerFriendRepository = playerFriendRepository;
        this.stringRedisTemplate = stringRedisTemplate;
        this.socialEventPublisher = socialEventPublisher;
        this.socialAchievementService = socialAchievementService;
    }

    @Transactional
    public Map<String, Object> addFriend(long playerId, long friendId) {
        if (playerId <= 0 || friendId <= 0 || playerId == friendId) {
            return Map.of("ok", false, "error", "invalid_args");
        }
        if (isBlockedEither(playerId, friendId)) {
            return Map.of("ok", false, "error", "blocked");
        }
        if (!playerFriendRepository.existsByPlayerIdAndFriendId(playerId, friendId)) {
            PlayerFriend a = new PlayerFriend();
            a.setPlayerId(playerId);
            a.setFriendId(friendId);
            a.setCreatedAt(System.currentTimeMillis());
            playerFriendRepository.save(a);
        }
        if (!playerFriendRepository.existsByPlayerIdAndFriendId(friendId, playerId)) {
            PlayerFriend b = new PlayerFriend();
            b.setPlayerId(friendId);
            b.setFriendId(playerId);
            b.setCreatedAt(System.currentTimeMillis());
            playerFriendRepository.save(b);
        }
        bumpIntimacy(playerId, friendId, 1);
        socialEventPublisher.publish("FRIEND_ADDED", playerId, friendId, "");
        socialAchievementService.onCounter(playerId, "friend_count", friendCount(playerId));
        socialAchievementService.onCounter(friendId, "friend_count", friendCount(friendId));
        return Map.of("ok", true, "playerId", playerId, "friendId", friendId,
                "intimacy", getIntimacy(playerId, friendId));
    }

    @Transactional
    public Map<String, Object> removeFriend(long playerId, long friendId) {
        playerFriendRepository.deleteByPlayerIdAndFriendId(playerId, friendId);
        playerFriendRepository.deleteByPlayerIdAndFriendId(friendId, playerId);
        stringRedisTemplate.delete(intimacyKey(playerId, friendId));
        return Map.of("ok", true, "removed", true);
    }

    public Map<String, Object> block(long playerId, long targetId) {
        if (playerId <= 0 || targetId <= 0 || playerId == targetId) {
            return Map.of("ok", false, "error", "invalid_args");
        }
        stringRedisTemplate.opsForSet().add(KEY_BLOCK + playerId, String.valueOf(targetId));
        removeFriend(playerId, targetId);
        socialEventPublisher.publish("FRIEND_BLOCKED", playerId, targetId, "");
        return Map.of("ok", true, "blocked", targetId);
    }

    public Map<String, Object> unblock(long playerId, long targetId) {
        stringRedisTemplate.opsForSet().remove(KEY_BLOCK + playerId, String.valueOf(targetId));
        return Map.of("ok", true, "unblocked", targetId);
    }

    public Map<String, Object> listBlocked(long playerId) {
        Set<String> raw = stringRedisTemplate.opsForSet().members(KEY_BLOCK + playerId);
        return Map.of("ok", true, "blocked", raw == null ? List.of() : List.copyOf(raw));
    }

    public Map<String, Object> addIntimacy(long a, long b, int delta) {
        if (a <= 0 || b <= 0 || a == b) {
            return Map.of("ok", false, "error", "invalid_args");
        }
        if (!playerFriendRepository.existsByPlayerIdAndFriendId(a, b)) {
            return Map.of("ok", false, "error", "not_friend");
        }
        long value = bumpIntimacy(a, b, Math.max(1, delta));
        return Map.of("ok", true, "intimacy", value);
    }

    public long getIntimacy(long a, long b) {
        String raw = stringRedisTemplate.opsForValue().get(intimacyKey(a, b));
        if (raw == null) {
            return 0L;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public Map<String, Object> graphOf(long playerId) {
        List<Map<String, Object>> friends = new ArrayList<>();
        for (PlayerFriend f : playerFriendRepository.findByPlayerId(playerId)) {
            Long fid = f.getFriendId();
            if (fid == null) {
                continue;
            }
            Map<String, Object> edge = new LinkedHashMap<>();
            edge.put("friendId", fid);
            edge.put("intimacy", getIntimacy(playerId, fid));
            edge.put("blocked", isBlocked(playerId, fid));
            friends.add(edge);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("playerId", playerId);
        out.put("friendCount", friends.size());
        out.put("friends", friends);
        out.put("blocked", listBlocked(playerId).get("blocked"));
        return out;
    }

    public boolean isBlocked(long playerId, long targetId) {
        return Boolean.TRUE.equals(
                stringRedisTemplate.opsForSet().isMember(KEY_BLOCK + playerId, String.valueOf(targetId)));
    }

    public boolean isBlockedEither(long a, long b) {
        return isBlocked(a, b) || isBlocked(b, a);
    }

    private long friendCount(long playerId) {
        return playerFriendRepository.findByPlayerId(playerId).size();
    }

    private long bumpIntimacy(long a, long b, int delta) {
        String key = intimacyKey(a, b);
        Long v = stringRedisTemplate.opsForValue().increment(key, delta);
        stringRedisTemplate.expire(key, INTIMACY_TTL);
        return v == null ? 0L : v;
    }

    private static String intimacyKey(long a, long b) {
        long lo = Math.min(a, b);
        long hi = Math.max(a, b);
        return KEY_INTIMACY + lo + ":" + hi;
    }
}
