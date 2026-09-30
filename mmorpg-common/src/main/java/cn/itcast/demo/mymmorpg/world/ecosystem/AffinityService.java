package cn.itcast.demo.mymmorpg.world.ecosystem;

import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.world.explore.CollectibleService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 野生生物亲密度：Redis 语义 key {@code creature:affinity:{playerId}:{creatureUid}}（0~1000）。
 * 喂食解锁寻宝 / 预警协助。
 */
@Service
public class AffinityService {

    public static final int MAX_AFFINITY = 1000;
    public static final int THRESHOLD_TREASURE = 300;
    public static final int THRESHOLD_ALERT = 600;
    public static final String FOOD_ITEM = "FOOD_ITEM";

    /** 内存模拟 Redis Hash */
    private final ConcurrentHashMap<String, Integer> affinity = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> lastTreasureHintMs = new ConcurrentHashMap<>();
    private CollectibleService collectibles;

    public AffinityService() {
    }

    public AffinityService(CollectibleService collectibles) {
        this.collectibles = collectibles;
    }

    public void bindCollectibles(CollectibleService collectibles) {
        this.collectibles = collectibles;
    }

    private static String key(long playerId, String creatureUid) {
        return "creature:affinity:" + playerId + ":" + (creatureUid == null ? "" : creatureUid.trim());
    }

    public int get(long playerId, String creatureUid) {
        return affinity.getOrDefault(key(playerId, creatureUid), 0);
    }

    public Map<String, Object> feed(long playerId, String creatureUid, String itemId, int amount) {
        if (!FOOD_ITEM.equals(itemId == null ? "" : itemId.trim())) {
            return Map.of("ok", false, "error", "requires_FOOD_ITEM");
        }
        int add = Math.max(1, amount) * 25;
        String k = key(playerId, creatureUid);
        int next = Math.min(MAX_AFFINITY, affinity.getOrDefault(k, 0) + add);
        affinity.put(k, next);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("redisKey", k);
        body.put("affinity", next);
        body.put("unlockTreasure", next >= THRESHOLD_TREASURE);
        body.put("unlockAlert", next >= THRESHOLD_ALERT);
        return body;
    }

    /**
     * 寻宝：亲密度达标后周期性提示附近 HIDDEN 宝箱。
     */
    public Map<String, Object> tryTreasureHint(
            long playerId, String creatureUid, float px, float pz, long nowMs, long intervalMs) {
        int a = get(playerId, creatureUid);
        if (a < THRESHOLD_TREASURE) {
            return Map.of("ok", false, "error", "affinity_too_low", "affinity", a,
                    "need", THRESHOLD_TREASURE);
        }
        long last = lastTreasureHintMs.getOrDefault(playerId, 0L);
        long gap = intervalMs <= 0 ? 30_000L : intervalMs;
        if (last > 0 && nowMs - last < gap) {
            return Map.of("ok", true, "hinted", false, "reason", "cooldown");
        }
        lastTreasureHintMs.put(playerId, nowMs);
        List<Map<String, Object>> nearby = new ArrayList<>();
        if (collectibles != null) {
            for (CollectibleService.CollectibleDef def : collectibles.listAll()) {
                if (collectibles.visibilityOf(def.collectibleId()) != CollectibleService.Visibility.HIDDEN) {
                    continue;
                }
                float dx = def.x() - px;
                float dz = def.z() - pz;
                if (dx * dx + dz * dz <= 80f * 80f) {
                    nearby.add(Map.of(
                            "collectibleId", def.collectibleId(),
                            "x", def.x(), "y", def.y(), "z", def.z(),
                            "tier", def.tier().name()));
                }
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("hinted", true);
        body.put("assist", "TREASURE_SENSE");
        body.put("hiddenNearby", nearby);
        return body;
    }

    /**
     * 预警：稀有精英刷出时比 AOI 更早推送 CREATURE_ALERT。
     */
    public Map<String, Object> pushCreatureAlert(
            long playerId, String creatureUid, String eliteId, String gridCell) {
        int a = get(playerId, creatureUid);
        if (a < THRESHOLD_ALERT) {
            return Map.of("ok", false, "error", "affinity_too_low", "affinity", a,
                    "need", THRESHOLD_ALERT);
        }
        Map<String, Object> notify = new LinkedHashMap<>();
        notify.put("event", "CREATURE_ALERT");
        notify.put("msgId", MessageId.CREATURE_ALERT_SC_NOTIFY);
        notify.put("playerId", playerId);
        notify.put("creatureUid", creatureUid);
        notify.put("eliteId", eliteId);
        notify.put("gridCell", gridCell);
        notify.put("earlierThanAoi", true);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("assist", "ALERT");
        body.put("notify", notify);
        return body;
    }
}
