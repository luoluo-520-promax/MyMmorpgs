package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.client.GuildBagClient;
import cn.itcast.demo.mymmorpg.model.GuildRecord;
import cn.itcast.demo.mymmorpg.model.GuildRole;
import cn.itcast.demo.mymmorpg.protocol.BagRetCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 公会远征：会长开启；伤害写入 Redis ZSET {@code guild:boss:damage:{guildId}}；
 * 结算按伤害百分位发放公会货币 itemId=90001（200/100/50）。
 */
@Service
public class GuildExpeditionService {

    private static final Logger log = LoggerFactory.getLogger(GuildExpeditionService.class);

    public static final String REDIS_DAMAGE = "guild:boss:damage:";
    public static final int GUILD_COIN_ITEM = 90_001;
    public static final long BASE_BOSS_HP = 1_000_000L;

    public enum ExpeditionState {
        IDLE, ACTIVE, SETTLED
    }

    public record Expedition(
            long guildId,
            String guildBossId,
            ExpeditionState state,
            long bossHp,
            long bossHpMax,
            long openedAtMs) {
    }

    private final ConcurrentHashMap<Long, Expedition> expeditions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ConcurrentHashMap<Long, AtomicLong>> localDamage = new ConcurrentHashMap<>();

    private final GuildService guildService;
    private final ObjectProvider<StringRedisTemplate> redisTemplate;
    private final ObjectProvider<GuildBagClient> bagClient;
    private final ObjectProvider<LocalDamageCounter> localDamageCounter;

    public GuildExpeditionService(GuildService guildService,
                                  ObjectProvider<StringRedisTemplate> redisTemplate,
                                  ObjectProvider<GuildBagClient> bagClient,
                                  ObjectProvider<LocalDamageCounter> localDamageCounter) {
        this.guildService = guildService;
        this.redisTemplate = redisTemplate;
        this.bagClient = bagClient;
        this.localDamageCounter = localDamageCounter;
    }

    public Map<String, Object> open(long playerId, long guildId) {
        GuildRecord guild = guildService.requireGuild(guildId);
        var member = guild.getMembers().get(playerId);
        if (member == null || member.getRole() != GuildRole.LEADER) {
            return Map.of("ok", false, "error", "NOT_LEADER");
        }
        Expedition cur = expeditions.get(guildId);
        if (cur != null && cur.state() == ExpeditionState.ACTIVE) {
            return Map.of("ok", false, "error", "ALREADY_ACTIVE");
        }
        long hpMax = Math.round(BASE_BOSS_HP * (1.0 + 0.2 * guild.getLevel()));
        String bossId = "guild_boss_" + guildId + "_" + System.currentTimeMillis();
        Expedition exp = new Expedition(guildId, bossId, ExpeditionState.ACTIVE, hpMax, hpMax, System.currentTimeMillis());
        expeditions.put(guildId, exp);
        localDamage.put(guildId, new ConcurrentHashMap<>());
        clearDamageBoard(guildId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("guildBossId", bossId);
        out.put("bossHpMax", hpMax);
        out.put("guildLevel", guild.getLevel());
        return out;
    }

    public Map<String, Object> reportDamage(long guildId, long playerId, long damage) {
        Expedition exp = expeditions.get(guildId);
        if (exp == null || exp.state() != ExpeditionState.ACTIVE) {
            return Map.of("ok", false, "error", "NOT_ACTIVE");
        }
        if (playerId <= 0 || damage <= 0) {
            return Map.of("ok", false, "error", "INVALID_DAMAGE");
        }
        if (guildService.guildIdOf(playerId) == null || guildService.guildIdOf(playerId) != guildId) {
            return Map.of("ok", false, "error", "NOT_MEMBER");
        }
        long total = localDamage.computeIfAbsent(guildId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(playerId, id -> new AtomicLong())
                .addAndGet(damage);
        accumulateDamage(guildId, playerId, damage, total);
        guildService.addContribution(guildId, playerId, damage);
        long newHp = Math.max(0L, exp.bossHp() - damage);
        Expedition updated = new Expedition(exp.guildId(), exp.guildBossId(), ExpeditionState.ACTIVE, newHp,
                exp.bossHpMax(), exp.openedAtMs());
        expeditions.put(guildId, updated);
        return Map.of("ok", true, "playerDamage", total, "bossHp", newHp);
    }

    /**
     * 结算档位：前 10% 金 / 前 30% 银 / 前 50% 铜；其余不发公会币。
     */
    public Map<String, Object> settle(long guildId) {
        Expedition exp = expeditions.get(guildId);
        if (exp == null) {
            return Map.of("ok", false, "error", "NOT_FOUND");
        }
        flushDamage(guildId);
        List<Map.Entry<Long, Long>> board = damageBoard(guildId);
        int eligible = board.size();
        List<Map<String, Object>> grants = new ArrayList<>();
        for (int i = 0; i < board.size(); i++) {
            Map.Entry<Long, Long> e = board.get(i);
            String tier = tierOf(i, eligible);
            int amount = rewardAmount(tier);
            if (amount <= 0) {
                continue;
            }
            String idem = "guild-expedition:" + exp.guildBossId() + ":" + e.getKey();
            grantCoin(e.getKey(), amount, idem);
            guildService.addContribution(guildId, e.getKey(), amount);
            Map<String, Object> plan = new LinkedHashMap<>();
            plan.put("playerId", e.getKey());
            plan.put("rank", i + 1);
            plan.put("damage", e.getValue());
            plan.put("tier", tier);
            plan.put("itemId", GUILD_COIN_ITEM);
            plan.put("count", amount);
            plan.put("idempotencyKey", idem);
            grants.add(plan);
        }
        expeditions.put(guildId, new Expedition(exp.guildId(), exp.guildBossId(), ExpeditionState.SETTLED,
                exp.bossHp(), exp.bossHpMax(), exp.openedAtMs()));
        clearDamageBoard(guildId);
        localDamage.remove(guildId);
        return Map.of("ok", true, "guildBossId", exp.guildBossId(), "grantPlans", grants);
    }

    /** 每周一 5:00：强制结算未结远征并重置。 */
    public int weeklyResetAll() {
        int n = 0;
        for (Long guildId : new ArrayList<>(expeditions.keySet())) {
            Expedition exp = expeditions.get(guildId);
            if (exp != null && exp.state() == ExpeditionState.ACTIVE) {
                settle(guildId);
            }
            expeditions.remove(guildId);
            clearDamageBoard(guildId);
            localDamage.remove(guildId);
            n++;
        }
        return n;
    }

    public Expedition get(long guildId) {
        return expeditions.get(guildId);
    }

    static String tierOf(int rankIndex0, int eligible) {
        if (eligible <= 0) {
            return "NONE";
        }
        double pct = (rankIndex0 + 1.0) / eligible;
        if (pct <= 0.10) {
            return "GOLD";
        }
        if (pct <= 0.30) {
            return "SILVER";
        }
        if (pct <= 0.50) {
            return "BRONZE";
        }
        return "NONE";
    }

    static int rewardAmount(String tier) {
        return switch (tier) {
            case "GOLD" -> 200;
            case "SILVER" -> 100;
            case "BRONZE" -> 50;
            default -> 0;
        };
    }

    private List<Map.Entry<Long, Long>> damageBoard(long guildId) {
        StringRedisTemplate redis = redisTemplate.getIfAvailable();
        if (redis != null) {
            Set<ZSetOperations.TypedTuple<String>> tuples =
                    redis.opsForZSet().reverseRangeWithScores(REDIS_DAMAGE + guildId, 0, -1);
            if (tuples != null && !tuples.isEmpty()) {
                List<Map.Entry<Long, Long>> out = new ArrayList<>();
                for (ZSetOperations.TypedTuple<String> t : tuples) {
                    if (t.getValue() == null || t.getScore() == null) {
                        continue;
                    }
                    out.add(Map.entry(Long.parseLong(t.getValue()), t.getScore().longValue()));
                }
                return out;
            }
        }
        ConcurrentHashMap<Long, AtomicLong> local = localDamage.getOrDefault(guildId, new ConcurrentHashMap<>());
        return local.entrySet().stream()
                .map(e -> Map.entry(e.getKey(), e.getValue().get()))
                .sorted(Comparator.<Map.Entry<Long, Long>>comparingLong(Map.Entry::getValue).reversed())
                .toList();
    }

    private void accumulateDamage(long guildId, long playerId, long delta, long localTotal) {
        LocalDamageCounter counter = localDamageCounter.getIfAvailable();
        if (counter != null) {
            counter.accumulate(REDIS_DAMAGE + guildId, playerId, delta);
            return;
        }
        writeZset(guildId, playerId, localTotal);
    }

    private void flushDamage(long guildId) {
        LocalDamageCounter counter = localDamageCounter.getIfAvailable();
        if (counter != null) {
            counter.flushAll();
            counter.clear(REDIS_DAMAGE + guildId);
        }
    }

    private void writeZset(long guildId, long playerId, long total) {
        StringRedisTemplate redis = redisTemplate.getIfAvailable();
        if (redis == null) {
            return;
        }
        redis.opsForZSet().add(REDIS_DAMAGE + guildId, String.valueOf(playerId), total);
    }

    private void clearDamageBoard(long guildId) {
        StringRedisTemplate redis = redisTemplate.getIfAvailable();
        if (redis != null) {
            redis.delete(REDIS_DAMAGE + guildId);
        }
    }

    private void grantCoin(long playerId, int amount, String idem) {
        GuildBagClient bag = bagClient.getIfAvailable();
        if (bag == null) {
            return;
        }
        try {
            Map<String, Object> body = Map.of(
                    "idempotencyKey", idem,
                    "rewards", List.of(Map.of("itemId", GUILD_COIN_ITEM, "count", amount)));
            Integer rc = bag.grant(playerId, body);
            if (rc != null && rc != BagRetCode.OK) {
                log.warn("guild coin grant rc={} playerId={}", rc, playerId);
            }
        } catch (Exception e) {
            log.warn("guild coin grant failed playerId={}", playerId, e);
        }
    }
}
