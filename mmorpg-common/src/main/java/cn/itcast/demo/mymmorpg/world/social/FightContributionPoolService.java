package cn.itcast.demo.mymmorpg.world.social;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 过客共鸣：世界 Boss/稀有精英协战贡献池，路人 &gt;5% 伤害自动进入协战者名单。
 */
@Service
public class FightContributionPoolService {

    public static final float PASSERBY_THRESHOLD = 0.05f;

    public record Contribution(long playerId, long damage, float ratio) {
    }

    private final AtomicLong battleSeq = new AtomicLong(1);
    private final ConcurrentHashMap<Long, Long> battleMaxHp = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ConcurrentHashMap<Long, Long>> damageByBattle =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, List<Long>> passerbyGrants = new ConcurrentHashMap<>();
    /** 实例掉落按玩家 UID 分桶：房主池与访客池互不稀释 */
    private final ConcurrentHashMap<Long, ConcurrentHashMap<Long, List<Map<String, Object>>>> instanceLootByBattle =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> battleHostPlayer = new ConcurrentHashMap<>();

    public long openBattle(String bossName, long maxHp) {
        return openBattle(bossName, maxHp, 0L);
    }

    public long openBattle(String bossName, long maxHp, long hostPlayerId) {
        long battleId = battleSeq.getAndIncrement();
        battleMaxHp.put(battleId, Math.max(1, maxHp));
        damageByBattle.put(battleId, new ConcurrentHashMap<>());
        instanceLootByBattle.put(battleId, new ConcurrentHashMap<>());
        if (hostPlayerId > 0) {
            battleHostPlayer.put(battleId, hostPlayerId);
        }
        return battleId;
    }

    public Map<String, Object> recordDamage(long battleId, long playerId, long damage) {
        if (battleId <= 0 || playerId <= 0 || damage <= 0) {
            return Map.of("ok", false, "error", "invalid_args");
        }
        ConcurrentHashMap<Long, Long> pool = damageByBattle.computeIfAbsent(
                battleId, id -> new ConcurrentHashMap<>());
        long total = pool.merge(playerId, damage, Long::sum);
        long maxHp = battleMaxHp.getOrDefault(battleId, 1L);
        long sumDamage = pool.values().stream().mapToLong(Long::longValue).sum();
        float ratio = (float) total / maxHp;
        boolean passerby = ratio >= PASSERBY_THRESHOLD;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("battleId", battleId);
        body.put("playerId", playerId);
        body.put("playerDamage", total);
        body.put("damageRatio", Math.round(ratio * 1000f) / 1000f);
        body.put("passerbyEligible", passerby);
        body.put("totalDamageDealt", sumDamage);
        return body;
    }

    public Map<String, Object> settleBattle(long battleId, long killerPlayerId) {
        ConcurrentHashMap<Long, Long> pool = damageByBattle.get(battleId);
        if (pool == null) {
            return Map.of("ok", false, "error", "battle_not_found");
        }
        long maxHp = battleMaxHp.getOrDefault(battleId, 1L);
        List<Contribution> contributors = new ArrayList<>();
        for (Map.Entry<Long, Long> e : pool.entrySet()) {
            float ratio = (float) e.getValue() / maxHp;
            if (ratio >= PASSERBY_THRESHOLD) {
                contributors.add(new Contribution(e.getKey(), e.getValue(), ratio));
            }
        }
        List<Map<String, Object>> grants = new ArrayList<>();
        for (Contribution c : contributors) {
            if (c.playerId() == killerPlayerId) {
                continue;
            }
            Map<String, Object> grant = new LinkedHashMap<>();
            grant.put("playerId", c.playerId());
            grant.put("event", "PASSERBY_GRANT_BOX");
            grant.put("damageRatio", Math.round(c.ratio() * 1000f) / 1000f);
            grant.put("rewardMode", "INDEPENDENT_LOW_TIER");
            grants.add(grant);
        }
        passerbyGrants.put(battleId, contributors.stream().map(Contribution::playerId).toList());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("battleId", battleId);
        body.put("killerPlayerId", killerPlayerId);
        body.put("coopCount", contributors.size());
        body.put("passerbyGrants", grants);
        body.put("broadcastLoot", false);
        body.put("hint", "协战者获得独立低保箱，非全服掉落广播");
        return body;
    }

    public List<Long> passerbyList(long battleId) {
        return passerbyGrants.getOrDefault(battleId, List.of());
    }

    /**
     * 访客镜像掉落：按玩家 UID 分桶写入 Instance Loot；房主池不受访客数量稀释。
     */
    public Map<String, Object> grantInstanceLoot(
            long battleId, long playerId, String itemId, int count) {
        if (battleId <= 0 || playerId <= 0 || itemId == null || itemId.isBlank() || count <= 0) {
            return Map.of("ok", false, "error", "invalid_args");
        }
        ConcurrentHashMap<Long, List<Map<String, Object>>> buckets =
                instanceLootByBattle.computeIfAbsent(battleId, id -> new ConcurrentHashMap<>());
        List<Map<String, Object>> bucket = buckets.computeIfAbsent(playerId, id -> new ArrayList<>());
        Map<String, Object> drop = new LinkedHashMap<>();
        drop.put("itemId", itemId.trim());
        drop.put("count", count);
        drop.put("bucketPlayerId", playerId);
        Long host = battleHostPlayer.get(battleId);
        drop.put("isHostBucket", host != null && host == playerId);
        bucket.add(drop);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("battleId", battleId);
        body.put("playerId", playerId);
        body.put("instanceLoot", true);
        body.put("bucketSize", bucket.size());
        body.put("drop", drop);
        body.put("hostPoolIsolated", true);
        body.put("hint", "掉落按 UID 分桶，房主/访客池互不稀释");
        return body;
    }

    public List<Map<String, Object>> instanceLootOf(long battleId, long playerId) {
        ConcurrentHashMap<Long, List<Map<String, Object>>> buckets = instanceLootByBattle.get(battleId);
        if (buckets == null) {
            return List.of();
        }
        return List.copyOf(buckets.getOrDefault(playerId, List.of()));
    }

    public Map<String, Object> settleInstanceLoot(long battleId) {
        ConcurrentHashMap<Long, List<Map<String, Object>>> buckets = instanceLootByBattle.get(battleId);
        if (buckets == null) {
            return Map.of("ok", false, "error", "battle_not_found");
        }
        Long host = battleHostPlayer.get(battleId);
        List<Map<String, Object>> hostLoot = host == null
                ? List.of()
                : List.copyOf(buckets.getOrDefault(host, List.of()));
        List<Map<String, Object>> guestBuckets = new ArrayList<>();
        for (Map.Entry<Long, List<Map<String, Object>>> e : buckets.entrySet()) {
            if (host != null && e.getKey().equals(host)) {
                continue;
            }
            guestBuckets.add(Map.of("playerId", e.getKey(), "drops", List.copyOf(e.getValue())));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("battleId", battleId);
        body.put("hostPlayerId", host == null ? 0L : host);
        body.put("hostLoot", hostLoot);
        body.put("guestBuckets", guestBuckets);
        body.put("hostDiluted", false);
        body.put("guestDiluted", false);
        return body;
    }
}
