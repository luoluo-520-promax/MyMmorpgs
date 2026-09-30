package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.PlayerFriend;
import cn.itcast.demo.mymmorpg.repository.PlayerFriendRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 异步社交：家园发布 / 拜访 / 访问权限 / 装饰同步；
 * 扩展：互动家具小游戏、评分排行、家园商店（Redis 快照）。
 */
@Service
public class HomeVisitService {

    public enum AccessPolicy {
        FRIENDS_ONLY, PUBLIC, PRIVATE
    }

    private static final String KEY_HOME = "home:layout:";
    private static final String KEY_VISIT_LOG = "home:visit:";
    private static final String KEY_DECO = "home:deco:";
    private static final String KEY_COIN = "home:coin:";
    private static final String KEY_OWNED = "home:shop:owned:";
    private static final String KEY_RANK = "home:rank";
    private static final String KEY_RATED = "home:rated:";
    private static final Duration HOME_TTL = Duration.ofDays(365);

    /** 互动家具类型 → 小游戏 */
    private static final Map<String, String> FURNITURE_GAMES = Map.of(
            "fish_pond", "FISHING",
            "chess_table", "CHESS",
            "card_table", "CARDS",
            "tea_set", "TEA_CHAT"
    );

    private static final List<Map<String, Object>> SHOP_CATALOG = List.of(
            Map.of("itemId", "fish_pond", "name", "钓鱼池", "price", 200, "game", "FISHING"),
            Map.of("itemId", "chess_table", "name", "棋牌桌", "price", 150, "game", "CHESS"),
            Map.of("itemId", "card_table", "name", "卡牌桌", "price", 150, "game", "CARDS"),
            Map.of("itemId", "tea_set", "name", "茶席", "price", 80, "game", "TEA_CHAT"),
            Map.of("itemId", "flower_shelf", "name", "花架", "price", 50, "game", "")
    );

    private final PlayerFriendRepository playerFriendRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final SocialEventPublisher socialEventPublisher;

    public HomeVisitService(PlayerFriendRepository playerFriendRepository,
                            StringRedisTemplate stringRedisTemplate,
                            ObjectMapper objectMapper,
                            SocialEventPublisher socialEventPublisher) {
        this.playerFriendRepository = playerFriendRepository;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.socialEventPublisher = socialEventPublisher;
    }

    public Map<String, Object> publishHome(long ownerId, String name, String layoutJson) {
        return publishHome(ownerId, name, layoutJson, AccessPolicy.FRIENDS_ONLY.name());
    }

    public Map<String, Object> publishHome(long ownerId, String name, String layoutJson, String accessPolicy) {
        if (ownerId <= 0) {
            return Map.of("ok", false, "error", "invalid_owner");
        }
        AccessPolicy policy = parsePolicy(accessPolicy);
        Map<String, Object> home = new LinkedHashMap<>();
        home.put("ownerId", ownerId);
        home.put("name", name == null || name.isBlank() ? ("家园-" + ownerId) : name.trim());
        home.put("layoutJson", layoutJson == null ? "{}" : layoutJson);
        home.put("accessPolicy", policy.name());
        home.put("scoreSum", 0);
        home.put("scoreCount", 0);
        home.put("avgScore", 0.0);
        home.put("updatedAtMs", System.currentTimeMillis());
        home.put("persistent", true);
        try {
            stringRedisTemplate.opsForValue().set(KEY_HOME + ownerId, objectMapper.writeValueAsString(home), HOME_TTL);
        } catch (Exception e) {
            return Map.of("ok", false, "error", "serialize_failed");
        }
        return Map.of("ok", true, "home", home);
    }

    public Map<String, Object> setAccess(long ownerId, String accessPolicy) {
        Map<String, Object> home = loadHome(ownerId);
        if (home == null) {
            return Map.of("ok", false, "error", "home_not_found");
        }
        home.put("accessPolicy", parsePolicy(accessPolicy).name());
        home.put("updatedAtMs", System.currentTimeMillis());
        try {
            stringRedisTemplate.opsForValue().set(KEY_HOME + ownerId, objectMapper.writeValueAsString(home), HOME_TTL);
        } catch (Exception e) {
            return Map.of("ok", false, "error", "serialize_failed");
        }
        return Map.of("ok", true, "home", home);
    }

    public Map<String, Object> syncDecorations(long ownerId, List<Map<String, Object>> decorations) {
        Map<String, Object> home = loadHome(ownerId);
        if (home == null) {
            return Map.of("ok", false, "error", "home_not_found");
        }
        List<Map<String, Object>> deco = decorations == null ? List.of() : decorations;
        try {
            stringRedisTemplate.opsForValue().set(KEY_DECO + ownerId, objectMapper.writeValueAsString(deco), HOME_TTL);
        } catch (Exception e) {
            return Map.of("ok", false, "error", "serialize_failed");
        }
        long ver = System.currentTimeMillis();
        home.put("decoVersion", ver);
        home.put("decoCount", deco.size());
        home.put("updatedAtMs", ver);
        refreshRankScore(ownerId, home);
        try {
            stringRedisTemplate.opsForValue().set(KEY_HOME + ownerId, objectMapper.writeValueAsString(home), HOME_TTL);
        } catch (Exception e) {
            return Map.of("ok", false, "error", "serialize_failed");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("home", home);
        out.put("decorations", deco);
        return out;
    }

    public Map<String, Object> getDecorations(long ownerId) {
        String raw = stringRedisTemplate.opsForValue().get(KEY_DECO + ownerId);
        if (raw == null || raw.isBlank()) {
            return Map.of("ok", true, "decorations", List.of());
        }
        try {
            List<Map<String, Object>> deco = objectMapper.readValue(raw, new TypeReference<List<Map<String, Object>>>() {
            });
            return Map.of("ok", true, "decorations", deco);
        } catch (Exception e) {
            return Map.of("ok", false, "error", "deserialize_failed");
        }
    }

    private static AccessPolicy parsePolicy(String raw) {
        if (raw == null || raw.isBlank()) {
            return AccessPolicy.FRIENDS_ONLY;
        }
        try {
            return AccessPolicy.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return AccessPolicy.FRIENDS_ONLY;
        }
    }

    public Map<String, Object> getHome(long ownerId) {
        Map<String, Object> home = loadHome(ownerId);
        if (home == null) {
            return Map.of("ok", false, "error", "home_not_found");
        }
        return Map.of("ok", true, "home", home);
    }

    public Map<String, Object> visit(long visitorId, long ownerId) {
        if (visitorId <= 0 || ownerId <= 0) {
            return Map.of("ok", false, "error", "invalid_args");
        }
        Map<String, Object> home = loadHome(ownerId);
        if (home == null) {
            return Map.of("ok", false, "error", "home_not_found");
        }
        AccessPolicy policy = parsePolicy(String.valueOf(home.getOrDefault("accessPolicy", "FRIENDS_ONLY")));
        if (visitorId != ownerId) {
            if (policy == AccessPolicy.PRIVATE) {
                return Map.of("ok", false, "error", "home_private");
            }
            if (policy == AccessPolicy.FRIENDS_ONLY
                    && !playerFriendRepository.existsByPlayerIdAndFriendId(visitorId, ownerId)) {
                return Map.of("ok", false, "error", "not_friend");
            }
        }
        stringRedisTemplate.opsForList().leftPush(KEY_VISIT_LOG + ownerId,
                visitorId + "," + System.currentTimeMillis());
        stringRedisTemplate.opsForList().trim(KEY_VISIT_LOG + ownerId, 0, 49);
        if (visitorId != ownerId) {
            stringRedisTemplate.opsForValue().increment(KEY_COIN + ownerId);
            socialEventPublisher.publishHomeVisited(visitorId, ownerId);
        }
        Map<String, Object> deco = getDecorations(ownerId);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("ok", true);
        resp.put("home", home);
        resp.put("decorations", deco.get("decorations"));
        resp.put("interactiveFurniture", listInteractive(ownerId));
        resp.put("visitorId", visitorId);
        resp.put("message", "欢迎拜访「" + home.get("name") + "」");
        return resp;
    }

    /**
     * 互动家具小游戏：访客与主人在钓鱼池/棋牌桌等家具上互动。
     */
    public Map<String, Object> playMiniGame(long playerId, long ownerId, String furnitureId, int score) {
        if (playerId <= 0 || ownerId <= 0 || furnitureId == null || furnitureId.isBlank()) {
            return Map.of("ok", false, "error", "invalid_args");
        }
        Map<String, Object> access = visit(playerId, ownerId);
        if (!Boolean.TRUE.equals(access.get("ok"))) {
            return access;
        }
        String game = FURNITURE_GAMES.get(furnitureId.trim());
        if (game == null) {
            Set<String> owned = stringRedisTemplate.opsForSet().members(KEY_OWNED + ownerId);
            if (owned == null || !owned.contains(furnitureId.trim())) {
                return Map.of("ok", false, "error", "furniture_not_interactive");
            }
            game = "CUSTOM";
        }
        int points = Math.max(0, Math.min(1000, score));
        long coinGain = 5L + points / 50L;
        stringRedisTemplate.opsForValue().increment(KEY_COIN + ownerId, coinGain);
        if (playerId != ownerId) {
            stringRedisTemplate.opsForValue().increment(KEY_COIN + playerId, Math.max(1L, coinGain / 2));
        }
        socialEventPublisher.publish("HOME_MINIGAME", playerId, ownerId,
                "furniture=" + furnitureId + "|game=" + game + "|score=" + points);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("game", game);
        out.put("furnitureId", furnitureId.trim());
        out.put("score", points);
        out.put("ownerCoinGain", coinGain);
        out.put("message", "在「" + game + "」中玩得开心！");
        return out;
    }

    /**
     * 家园评分 1~5，刷新排行榜；布置越精致（装饰数）加权越高。
     */
    public Map<String, Object> rateHome(long raterId, long ownerId, int score) {
        if (raterId <= 0 || ownerId <= 0 || raterId == ownerId) {
            return Map.of("ok", false, "error", "invalid_args");
        }
        if (score < 1 || score > 5) {
            return Map.of("ok", false, "error", "score_out_of_range");
        }
        Map<String, Object> visit = visit(raterId, ownerId);
        if (!Boolean.TRUE.equals(visit.get("ok"))) {
            return visit;
        }
        String ratedKey = KEY_RATED + ownerId + ":" + raterId;
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(ratedKey))) {
            return Map.of("ok", false, "error", "already_rated");
        }
        Map<String, Object> home = loadHome(ownerId);
        if (home == null) {
            return Map.of("ok", false, "error", "home_not_found");
        }
        long sum = asLong(home.get("scoreSum")) + score;
        long count = asLong(home.get("scoreCount")) + 1;
        double avg = count == 0 ? 0.0 : (double) sum / count;
        home.put("scoreSum", sum);
        home.put("scoreCount", count);
        home.put("avgScore", Math.round(avg * 100.0) / 100.0);
        home.put("updatedAtMs", System.currentTimeMillis());
        refreshRankScore(ownerId, home);
        try {
            stringRedisTemplate.opsForValue().set(KEY_HOME + ownerId, objectMapper.writeValueAsString(home), HOME_TTL);
        } catch (Exception e) {
            return Map.of("ok", false, "error", "serialize_failed");
        }
        stringRedisTemplate.opsForValue().set(ratedKey, String.valueOf(score), Duration.ofDays(30));
        stringRedisTemplate.opsForValue().increment(KEY_COIN + ownerId, score * 2L);
        socialEventPublisher.publish("HOME_RATED", raterId, ownerId, "score=" + score);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("home", home);
        out.put("yourScore", score);
        return out;
    }

    public Map<String, Object> ranking(int limit) {
        int lim = Math.max(1, Math.min(50, limit));
        Set<ZSetOperations.TypedTuple<String>> tuples =
                stringRedisTemplate.opsForZSet().reverseRangeWithScores(KEY_RANK, 0, lim - 1L);
        List<Map<String, Object>> rows = new ArrayList<>();
        if (tuples != null) {
            int rank = 1;
            for (ZSetOperations.TypedTuple<String> t : tuples) {
                if (t.getValue() == null) {
                    continue;
                }
                long ownerId;
                try {
                    ownerId = Long.parseLong(t.getValue());
                } catch (NumberFormatException e) {
                    continue;
                }
                Map<String, Object> home = loadHome(ownerId);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("rank", rank++);
                row.put("ownerId", ownerId);
                row.put("score", t.getScore() == null ? 0.0 : t.getScore());
                row.put("name", home == null ? ("家园-" + ownerId) : home.get("name"));
                row.put("avgScore", home == null ? 0.0 : home.getOrDefault("avgScore", 0.0));
                row.put("decoCount", home == null ? 0 : home.getOrDefault("decoCount", 0));
                rows.add(row);
            }
        }
        return Map.of("ok", true, "ranking", rows);
    }

    public Map<String, Object> shopCatalog() {
        return Map.of("ok", true, "catalog", SHOP_CATALOG);
    }

    public Map<String, Object> buyShopItem(long ownerId, String itemId) {
        if (ownerId <= 0 || itemId == null || itemId.isBlank()) {
            return Map.of("ok", false, "error", "invalid_args");
        }
        Map<String, Object> home = loadHome(ownerId);
        if (home == null) {
            return Map.of("ok", false, "error", "home_not_found");
        }
        Map<String, Object> product = null;
        for (Map<String, Object> p : SHOP_CATALOG) {
            if (itemId.trim().equals(String.valueOf(p.get("itemId")))) {
                product = p;
                break;
            }
        }
        if (product == null) {
            return Map.of("ok", false, "error", "item_not_found");
        }
        Boolean already = stringRedisTemplate.opsForSet().isMember(KEY_OWNED + ownerId, itemId.trim());
        if (Boolean.TRUE.equals(already)) {
            return Map.of("ok", false, "error", "already_owned");
        }
        int price = ((Number) product.get("price")).intValue();
        long coin = asLong(stringRedisTemplate.opsForValue().get(KEY_COIN + ownerId));
        if (coin < price) {
            return Map.of("ok", false, "error", "insufficient_coin", "coin", coin, "price", price);
        }
        stringRedisTemplate.opsForValue().increment(KEY_COIN + ownerId, -price);
        stringRedisTemplate.opsForSet().add(KEY_OWNED + ownerId, itemId.trim());
        socialEventPublisher.publish("HOME_SHOP_BUY", ownerId, 0L, "itemId=" + itemId.trim());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("item", product);
        out.put("remainCoin", coin - price);
        return out;
    }

    public Map<String, Object> wallet(long ownerId) {
        long coin = asLong(stringRedisTemplate.opsForValue().get(KEY_COIN + ownerId));
        Set<String> owned = stringRedisTemplate.opsForSet().members(KEY_OWNED + ownerId);
        return Map.of("ok", true, "coin", coin, "owned", owned == null ? List.of() : List.copyOf(owned));
    }

    public List<Map<String, Object>> listFriendHomes(long playerId) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (playerId <= 0) {
            return out;
        }
        for (PlayerFriend f : playerFriendRepository.findByPlayerId(playerId)) {
            Long friendId = f.getFriendId();
            if (friendId == null) {
                continue;
            }
            Map<String, Object> home = loadHome(friendId);
            if (home != null) {
                out.add(home);
            }
        }
        return out;
    }

    private List<Map<String, Object>> listInteractive(long ownerId) {
        List<Map<String, Object>> out = new ArrayList<>();
        Set<String> owned = stringRedisTemplate.opsForSet().members(KEY_OWNED + ownerId);
        if (owned == null) {
            return out;
        }
        for (String id : owned) {
            String game = FURNITURE_GAMES.get(id);
            if (game != null) {
                out.add(Map.of("furnitureId", id, "game", game));
            }
        }
        return out;
    }

    private void refreshRankScore(long ownerId, Map<String, Object> home) {
        double avg = asDouble(home.get("avgScore"));
        long deco = asLong(home.get("decoCount"));
        double rankScore = avg * 100.0 + deco * 2.0;
        stringRedisTemplate.opsForZSet().add(KEY_RANK, String.valueOf(ownerId), rankScore);
        home.put("rankScore", rankScore);
    }

    private Map<String, Object> loadHome(long ownerId) {
        String raw = stringRedisTemplate.opsForValue().get(KEY_HOME + ownerId);
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

    private static long asLong(Object v) {
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

    private static double asDouble(Object v) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }
}
