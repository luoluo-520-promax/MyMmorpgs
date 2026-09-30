package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.PlayerFriend;
import cn.itcast.demo.mymmorpg.repository.PlayerFriendRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 异步社交：借用好友角色助战（支援快照 + 每日次数/冷却 + 报酬与统计）。
 * 扩展：多助战阵容槽位、结算后感谢/评价。
 */
@Service
public class FriendAssistService {

    private static final String KEY_OFFER = "assist:offer:";
    private static final String KEY_LINEUPS = "assist:lineups:";
    private static final String KEY_BORROW = "assist:borrow:";
    private static final String KEY_DAILY = "assist:daily:";
    private static final String KEY_COOLDOWN = "assist:cd:";
    private static final String KEY_STATS = "assist:stats:";
    private static final String KEY_REWARD = "assist:reward:";
    private static final String KEY_THANKS = "assist:thanks:";
    private static final int DAILY_LIMIT = 3;
    private static final int MAX_LINEUPS = 3;
    private static final Duration OFFER_TTL = Duration.ofDays(7);
    private static final Duration BORROW_TTL = Duration.ofHours(4);
    private static final Duration COOLDOWN = Duration.ofHours(2);
    private static final int REWARD_COIN = 100;

    private final PlayerFriendRepository playerFriendRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final SocialEventPublisher socialEventPublisher;

    public FriendAssistService(PlayerFriendRepository playerFriendRepository,
                               StringRedisTemplate stringRedisTemplate,
                               ObjectMapper objectMapper,
                               SocialEventPublisher socialEventPublisher) {
        this.playerFriendRepository = playerFriendRepository;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.socialEventPublisher = socialEventPublisher;
    }

    /** 兼容旧接口：写入默认槽位 0 */
    public Map<String, Object> offerAssist(long ownerId, Map<String, Object> characterSnapshot) {
        return offerAssist(ownerId, 0, characterSnapshot);
    }

    /**
     * 设置助战阵容槽位（0..MAX_LINEUPS-1），同时刷新默认 offer 指向该快照。
     */
    public Map<String, Object> offerAssist(long ownerId, int slot, Map<String, Object> characterSnapshot) {
        if (ownerId <= 0) {
            return Map.of("ok", false, "error", "invalid_owner");
        }
        if (slot < 0 || slot >= MAX_LINEUPS) {
            return Map.of("ok", false, "error", "invalid_slot", "maxSlots", MAX_LINEUPS);
        }
        Map<String, Object> snap = characterSnapshot == null ? new LinkedHashMap<>() : new LinkedHashMap<>(characterSnapshot);
        snap.putIfAbsent("ownerId", ownerId);
        snap.put("slot", slot);
        snap.put("offeredAtMs", System.currentTimeMillis());
        try {
            Map<String, Object> lineups = loadLineups(ownerId);
            lineups.put(String.valueOf(slot), snap);
            stringRedisTemplate.opsForValue().set(KEY_LINEUPS + ownerId,
                    objectMapper.writeValueAsString(lineups), OFFER_TTL);
            stringRedisTemplate.opsForValue().set(KEY_OFFER + ownerId, objectMapper.writeValueAsString(snap), OFFER_TTL);
        } catch (Exception e) {
            return Map.of("ok", false, "error", "serialize_failed");
        }
        return Map.of("ok", true, "ownerId", ownerId, "slot", slot, "offer", snap, "maxSlots", MAX_LINEUPS);
    }

    public Map<String, Object> listLineups(long ownerId) {
        Map<String, Object> lineups = loadLineups(ownerId);
        List<Map<String, Object>> slots = new ArrayList<>();
        for (int i = 0; i < MAX_LINEUPS; i++) {
            Object raw = lineups.get(String.valueOf(i));
            if (raw instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> snap = (Map<String, Object>) m;
                slots.add(snap);
            }
        }
        return Map.of("ok", true, "ownerId", ownerId, "lineups", slots, "maxSlots", MAX_LINEUPS);
    }

    public List<Map<String, Object>> listOffers(long playerId) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (playerId <= 0) {
            return out;
        }
        for (PlayerFriend f : playerFriendRepository.findByPlayerId(playerId)) {
            Long friendId = f.getFriendId();
            if (friendId == null) {
                continue;
            }
            Map<String, Object> offer = loadOffer(friendId);
            if (offer != null) {
                out.add(offer);
            }
        }
        return out;
    }

    public Map<String, Object> borrow(long borrowerId, long ownerId) {
        return borrow(borrowerId, ownerId, -1);
    }

    public Map<String, Object> borrow(long borrowerId, long ownerId, int slot) {
        if (borrowerId <= 0 || ownerId <= 0 || borrowerId == ownerId) {
            return Map.of("ok", false, "error", "invalid_args");
        }
        if (!playerFriendRepository.existsByPlayerIdAndFriendId(borrowerId, ownerId)) {
            return Map.of("ok", false, "error", "not_friend");
        }
        Map<String, Object> offer;
        if (slot >= 0) {
            Map<String, Object> lineups = loadLineups(ownerId);
            Object raw = lineups.get(String.valueOf(slot));
            if (!(raw instanceof Map<?, ?>)) {
                return Map.of("ok", false, "error", "no_lineup_slot");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> snap = new LinkedHashMap<>((Map<String, Object>) raw);
            offer = snap;
        } else {
            offer = loadOffer(ownerId);
        }
        if (offer == null) {
            return Map.of("ok", false, "error", "no_offer");
        }
        String cdKey = KEY_COOLDOWN + borrowerId + ":" + ownerId;
        Boolean cd = stringRedisTemplate.hasKey(cdKey);
        if (Boolean.TRUE.equals(cd)) {
            Long ttl = stringRedisTemplate.getExpire(cdKey);
            return Map.of("ok", false, "error", "cooldown", "remainSec", ttl == null ? 0 : ttl);
        }
        String day = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString().replace("-", "");
        String dailyKey = KEY_DAILY + borrowerId + ":" + day;
        Long used = stringRedisTemplate.opsForValue().increment(dailyKey);
        if (used != null && used == 1L) {
            stringRedisTemplate.expire(dailyKey, Duration.ofDays(2));
        }
        if (used != null && used > DAILY_LIMIT) {
            stringRedisTemplate.opsForValue().decrement(dailyKey);
            return Map.of("ok", false, "error", "daily_limit", "limit", DAILY_LIMIT);
        }
        Map<String, Object> borrowed = new LinkedHashMap<>(offer);
        borrowed.put("borrowerId", borrowerId);
        borrowed.put("borrowedAtMs", System.currentTimeMillis());
        borrowed.put("thanked", false);
        try {
            stringRedisTemplate.opsForValue().set(KEY_BORROW + borrowerId,
                    objectMapper.writeValueAsString(borrowed), BORROW_TTL);
        } catch (Exception e) {
            return Map.of("ok", false, "error", "serialize_failed");
        }
        stringRedisTemplate.opsForValue().set(cdKey, "1", COOLDOWN);
        stringRedisTemplate.opsForHash().increment(KEY_STATS + ownerId, "borrowedCount", 1);
        stringRedisTemplate.opsForHash().increment(KEY_STATS + borrowerId, "borrowCount", 1);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("ok", true);
        resp.put("borrowed", borrowed);
        resp.put("dailyUsed", used);
        resp.put("dailyLimit", DAILY_LIMIT);
        resp.put("cooldownSec", COOLDOWN.toSeconds());
        return resp;
    }

    public Map<String, Object> settle(long borrowerId, boolean victory) {
        Map<String, Object> borrowed = loadJson(KEY_BORROW + borrowerId);
        if (borrowed == null) {
            return Map.of("ok", false, "error", "no_borrow");
        }
        Object ownerObj = borrowed.get("ownerId");
        long ownerId = ownerObj instanceof Number n ? n.longValue() : 0L;
        if (ownerId <= 0) {
            return Map.of("ok", false, "error", "invalid_owner");
        }
        int reward = victory ? REWARD_COIN : REWARD_COIN / 2;
        stringRedisTemplate.opsForHash().increment(KEY_REWARD + ownerId, "pendingCoin", reward);
        stringRedisTemplate.opsForHash().increment(KEY_STATS + ownerId, "settleCount", 1);
        if (victory) {
            stringRedisTemplate.opsForHash().increment(KEY_STATS + ownerId, "victoryAssist", 1);
        }
        // 保留结算快照供感谢，短 TTL
        borrowed.put("settled", true);
        borrowed.put("victory", victory);
        borrowed.put("settledAtMs", System.currentTimeMillis());
        try {
            stringRedisTemplate.opsForValue().set(KEY_BORROW + borrowerId + ":last",
                    objectMapper.writeValueAsString(borrowed), Duration.ofHours(24));
        } catch (Exception ignored) {
            // 感谢链路降级不影响结算
        }
        stringRedisTemplate.delete(KEY_BORROW + borrowerId);
        socialEventPublisher.publishAssistSettled(borrowerId, ownerId, victory);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("ownerId", ownerId);
        out.put("rewardCoin", reward);
        out.put("victory", victory);
        return out;
    }

    /**
     * 助战感谢/评价：1~5 星 + 留言，给借出方额外亲密度向反馈（统计 + 少量报酬）。
     */
    public Map<String, Object> thank(long borrowerId, long ownerId, int rating, String message) {
        if (borrowerId <= 0 || ownerId <= 0 || borrowerId == ownerId) {
            return Map.of("ok", false, "error", "invalid_args");
        }
        if (rating < 1 || rating > 5) {
            return Map.of("ok", false, "error", "rating_out_of_range");
        }
        Map<String, Object> last = loadJson(KEY_BORROW + borrowerId + ":last");
        if (last == null) {
            return Map.of("ok", false, "error", "no_recent_settle");
        }
        long settledOwner = last.get("ownerId") instanceof Number n ? n.longValue() : 0L;
        if (settledOwner != ownerId) {
            return Map.of("ok", false, "error", "owner_mismatch");
        }
        if (Boolean.TRUE.equals(last.get("thanked"))) {
            return Map.of("ok", false, "error", "already_thanked");
        }
        last.put("thanked", true);
        last.put("rating", rating);
        last.put("thanksMessage", message == null ? "" : message.trim());
        try {
            stringRedisTemplate.opsForValue().set(KEY_BORROW + borrowerId + ":last",
                    objectMapper.writeValueAsString(last), Duration.ofHours(24));
        } catch (Exception e) {
            return Map.of("ok", false, "error", "serialize_failed");
        }
        int bonus = rating >= 4 ? 20 : 10;
        stringRedisTemplate.opsForHash().increment(KEY_REWARD + ownerId, "pendingCoin", bonus);
        stringRedisTemplate.opsForHash().increment(KEY_STATS + ownerId, "thanksCount", 1);
        stringRedisTemplate.opsForHash().increment(KEY_STATS + ownerId, "ratingSum", rating);
        stringRedisTemplate.opsForList().leftPush(KEY_THANKS + ownerId,
                borrowerId + "," + rating + "," + System.currentTimeMillis());
        stringRedisTemplate.opsForList().trim(KEY_THANKS + ownerId, 0, 49);
        socialEventPublisher.publish("ASSIST_THANKS", borrowerId, ownerId, "rating=" + rating);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("ownerId", ownerId);
        out.put("rating", rating);
        out.put("bonusCoin", bonus);
        return out;
    }

    public Map<String, Object> claimReward(long ownerId) {
        Object raw = stringRedisTemplate.opsForHash().get(KEY_REWARD + ownerId, "pendingCoin");
        long pending = 0L;
        if (raw instanceof String s) {
            try {
                pending = Long.parseLong(s);
            } catch (NumberFormatException ignored) {
                pending = 0L;
            }
        } else if (raw instanceof Number n) {
            pending = n.longValue();
        }
        if (pending <= 0) {
            return Map.of("ok", false, "error", "no_reward");
        }
        stringRedisTemplate.opsForHash().put(KEY_REWARD + ownerId, "pendingCoin", "0");
        stringRedisTemplate.opsForHash().increment(KEY_STATS + ownerId, "claimedCoin", pending);
        return Map.of("ok", true, "claimedCoin", pending);
    }

    public Map<String, Object> stats(long playerId) {
        Map<Object, Object> raw = stringRedisTemplate.opsForHash().entries(KEY_STATS + playerId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("playerId", playerId);
        out.put("borrowCount", parseLong(raw.get("borrowCount")));
        out.put("borrowedCount", parseLong(raw.get("borrowedCount")));
        out.put("settleCount", parseLong(raw.get("settleCount")));
        out.put("victoryAssist", parseLong(raw.get("victoryAssist")));
        out.put("claimedCoin", parseLong(raw.get("claimedCoin")));
        out.put("thanksCount", parseLong(raw.get("thanksCount")));
        long ratingSum = parseLong(raw.get("ratingSum"));
        long thanks = parseLong(raw.get("thanksCount"));
        out.put("avgRating", thanks == 0 ? 0.0 : Math.round((ratingSum * 100.0 / thanks)) / 100.0);
        Object pending = stringRedisTemplate.opsForHash().get(KEY_REWARD + playerId, "pendingCoin");
        out.put("pendingCoin", parseLong(pending));
        return out;
    }

    private static long parseLong(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException e) {
                return 0L;
            }
        }
        return 0L;
    }

    public Map<String, Object> myBorrowed(long borrowerId) {
        Map<String, Object> raw = loadJson(KEY_BORROW + borrowerId);
        if (raw == null) {
            return Map.of("ok", true, "borrowed", null);
        }
        return Map.of("ok", true, "borrowed", raw);
    }

    public Map<String, Object> revoke(long ownerId) {
        Boolean deleted = stringRedisTemplate.delete(KEY_OFFER + ownerId);
        stringRedisTemplate.delete(KEY_LINEUPS + ownerId);
        return Map.of("ok", true, "revoked", Boolean.TRUE.equals(deleted));
    }

    private Map<String, Object> loadOffer(long ownerId) {
        return loadJson(KEY_OFFER + ownerId);
    }

    private Map<String, Object> loadLineups(long ownerId) {
        Map<String, Object> raw = loadJson(KEY_LINEUPS + ownerId);
        return raw == null ? new LinkedHashMap<>() : raw;
    }

    private Map<String, Object> loadJson(String key) {
        String raw = stringRedisTemplate.opsForValue().get(key);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<HashMap<String, Object>>() {
            });
        } catch (Exception e) {
            return null;
        }
    }
}
