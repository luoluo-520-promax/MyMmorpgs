package cn.itcast.demo.mymmorpg.service;

import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 圣遗物/词条塑形：定向锁定 + 强化时跳过锁定位成长区间，缓解「强化歪了」挫败感。
 */
@Service
public class RelicScoringService {

    private final RelicRandomizer randomizer;
    private final ConcurrentHashMap<Long, RelicRandomizer.RelicRoll> relics = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Integer> lockConsumables = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Integer> enhanceMats = new ConcurrentHashMap<>();

    public RelicScoringService(RelicRandomizer randomizer) {
        this.randomizer = randomizer;
    }

    public void putRelic(long itemUid, RelicRandomizer.RelicRoll roll) {
        relics.put(itemUid, roll);
    }

    public void creditLockConsumable(long playerId, int amount) {
        lockConsumables.merge(playerId, Math.max(0, amount), Integer::sum);
    }

    public void creditEnhanceMaterial(long playerId, int amount) {
        enhanceMats.merge(playerId, Math.max(0, amount), Integer::sum);
    }

    public RelicRandomizer.RelicRoll get(long itemUid) {
        return relics.get(itemUid);
    }

    /**
     * POST /internal/bag/relic/lock：消耗锁定道具，固定某副词条索引不参与随机强化。
     */
    public Map<String, Object> lockSubStat(long playerId, long itemUid, int subIndex) {
        RelicRandomizer.RelicRoll roll = relics.get(itemUid);
        if (roll == null) {
            return Map.of("ok", false, "error", "relic_not_found");
        }
        if (subIndex < 0 || subIndex >= roll.subStats().size()) {
            return Map.of("ok", false, "error", "invalid_sub_index",
                    "size", roll.subStats().size());
        }
        int have = lockConsumables.getOrDefault(playerId, 0);
        if (have < 1) {
            return Map.of("ok", false, "error", "need_lock_consumable");
        }
        Set<Integer> locked = new HashSet<>(roll.lockedSubIndexes());
        if (locked.contains(subIndex)) {
            return Map.of("ok", true, "alreadyLocked", true, "itemUid", itemUid, "subIndex", subIndex);
        }
        if (locked.size() >= 2) {
            return Map.of("ok", false, "error", "lock_cap", "max", 2);
        }
        lockConsumables.put(playerId, have - 1);
        locked.add(subIndex);
        RelicRandomizer.RelicRoll next = new RelicRandomizer.RelicRoll(
                roll.suitId(), roll.mainStat(), roll.mainValue(), roll.subStats(), locked);
        relics.put(itemUid, next);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("itemUid", itemUid);
        body.put("lockedIndexes", List.copyOf(locked));
        body.put("lockedStat", roll.subStats().get(subIndex).get("stat"));
        body.put("consumableLeft", lockConsumables.get(playerId));
        return body;
    }

    /**
     * 强化：种子跳过被锁词条成长区间。
     */
    public Map<String, Object> enhance(long playerId, long itemUid, long seed) {
        RelicRandomizer.RelicRoll roll = relics.get(itemUid);
        if (roll == null) {
            return Map.of("ok", false, "error", "relic_not_found");
        }
        int mats = enhanceMats.getOrDefault(playerId, 0);
        if (mats < 1) {
            return Map.of("ok", false, "error", "insufficient_enhance_mat");
        }
        enhanceMats.put(playerId, mats - 1);
        long effective = seed != 0 ? seed : (playerId * 31L + itemUid * 17L);
        Map<String, Object> detail = randomizer.growUnlockedSub(roll, effective);
        if (detail.get("subStats") instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> subs = (List<Map<String, Object>>) list;
            RelicRandomizer.RelicRoll next = new RelicRandomizer.RelicRoll(
                    roll.suitId(), roll.mainStat(), roll.mainValue(), subs, roll.lockedSubIndexes());
            relics.put(itemUid, next);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("itemUid", itemUid);
        body.put("seed", effective);
        body.put("rollDetail", detail);
        body.put("affix", toView(relics.get(itemUid)));
        return body;
    }

    private static Map<String, Object> toView(RelicRandomizer.RelicRoll roll) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("suitId", roll.suitId());
        m.put("mainStat", roll.mainStat());
        m.put("mainValue", roll.mainValue());
        m.put("subStats", roll.subStats());
        m.put("lockedIndexes", List.copyOf(roll.lockedSubIndexes()));
        return m;
    }
}
