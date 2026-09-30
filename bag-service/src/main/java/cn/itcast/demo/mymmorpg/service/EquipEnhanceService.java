package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.PlayerBagItem;
import cn.itcast.demo.mymmorpg.repository.PlayerBagItemRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 装备强化/精炼：每升 4 级随机提升或新增一条副词条；服务端种子 + 分布式锁防「垫子」并发刷词条。
 */
@Service
public class EquipEnhanceService {

    private static final Logger log = LoggerFactory.getLogger(EquipEnhanceService.class);
    private static final String LOCK_KEY = "bag:equip:enhance:";
    private static final String PITY_KEY = "bag:equip:pity:";
    private static final int PITY_THRESHOLD = 3;
    private static final Duration LOCK_TTL = Duration.ofSeconds(8);

    private final EquipRandomizer randomizer;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<PlayerBagItemRepository> bagRepo;
    private final ObjectProvider<StringRedisTemplate> redisProvider;
    /** 无 DB 时的内存强化态：itemUid → affix doc */
    private final ConcurrentHashMap<Long, Map<String, Object>> memoryAffix = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> localLocks = new ConcurrentHashMap<>();
    /** 玩家虚拟金币 / 强化材料（演示与单测） */
    private final ConcurrentHashMap<Long, Integer> goldWallet = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Integer> enhanceMats = new ConcurrentHashMap<>();
    /** itemUid → 锁定的副词条下标（强化时跳过） */
    private final ConcurrentHashMap<Long, java.util.Set<Integer>> lockedSubs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Integer> lockConsumables = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Integer> localPityCounter = new ConcurrentHashMap<>();

    public EquipEnhanceService(EquipRandomizer randomizer, ObjectMapper objectMapper) {
        this(randomizer, objectMapper, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public EquipEnhanceService(
            EquipRandomizer randomizer,
            ObjectMapper objectMapper,
            ObjectProvider<PlayerBagItemRepository> bagRepo,
            ObjectProvider<StringRedisTemplate> redisProvider) {
        this.randomizer = randomizer;
        this.objectMapper = objectMapper;
        this.bagRepo = bagRepo;
        this.redisProvider = redisProvider;
    }

    public void creditGold(long playerId, int amount) {
        goldWallet.merge(playerId, Math.max(0, amount), Integer::sum);
    }

    public void creditEnhanceMaterial(long playerId, int amount) {
        enhanceMats.merge(playerId, Math.max(0, amount), Integer::sum);
    }

    public void creditLockConsumable(long playerId, int amount) {
        lockConsumables.merge(playerId, Math.max(0, amount), Integer::sum);
    }

    /**
     * 定向锁定副词条：消耗锁定道具，强化时伪随机种子跳过该成长区间。
     */
    public Map<String, Object> lockSubStat(long playerId, long itemUid, int subIndex) {
        Map<String, Object> doc = loadAffixDoc(playerId, itemUid);
        if (doc == null) {
            return Map.of("ok", false, "error", "item_not_found");
        }
        List<?> subs = doc.get("subStats") instanceof List<?> list ? list : List.of();
        if (subIndex < 0 || subIndex >= subs.size()) {
            return Map.of("ok", false, "error", "invalid_sub_index");
        }
        int have = lockConsumables.getOrDefault(playerId, 0);
        if (have < 1) {
            return Map.of("ok", false, "error", "need_lock_consumable");
        }
        java.util.Set<Integer> locked = lockedSubs.computeIfAbsent(itemUid, id -> ConcurrentHashMap.newKeySet());
        if (locked.size() >= 2 && !locked.contains(subIndex)) {
            return Map.of("ok", false, "error", "lock_cap", "max", 2);
        }
        if (locked.add(subIndex)) {
            lockConsumables.put(playerId, have - 1);
        }
        return Map.of("ok", true, "itemUid", itemUid, "lockedIndexes", List.copyOf(locked),
                "consumableLeft", lockConsumables.getOrDefault(playerId, 0));
    }

    /**
     * 强化一级：消耗材料与金币；每 4 级触发副词条成长。
     */
    public Map<String, Object> enhance(long playerId, long itemUid, long seed) {
        if (playerId <= 0 || itemUid <= 0) {
            return Map.of("ok", false, "error", "invalid_args");
        }
        String lockToken = tryLock(playerId, itemUid);
        if (lockToken == null) {
            return Map.of("ok", false, "error", "enhance_locked");
        }
        try {
            Map<String, Object> doc = loadAffixDoc(playerId, itemUid);
            if (doc == null) {
                return Map.of("ok", false, "error", "item_not_found");
            }
            int level = asInt(doc.get("enhanceLevel"), 0);
            int matCost = 2 + level / 4;
            int goldCost = 500 + level * 120;
            int mats = enhanceMats.getOrDefault(playerId, 0);
            int gold = goldWallet.getOrDefault(playerId, 0);
            if (mats < matCost || gold < goldCost) {
                return Map.of("ok", false, "error", "insufficient_cost",
                        "needMaterial", matCost, "needGold", goldCost,
                        "haveMaterial", mats, "haveGold", gold);
            }
            enhanceMats.put(playerId, mats - matCost);
            goldWallet.put(playerId, gold - goldCost);

            int newLevel = level + 1;
            doc.put("enhanceLevel", newLevel);
            boolean rolledSub = false;
            Map<String, Object> rollDetail = Map.of();
            if (newLevel % 4 == 0) {
                long effectiveSeed = seed != 0 ? seed : (playerId * 31L + itemUid * 17L + newLevel);
                java.util.Set<Integer> locked = lockedSubs.getOrDefault(itemUid, java.util.Set.of());
                int pity = getPityCounter(itemUid);
                boolean pityTriggered = pity >= PITY_THRESHOLD && !locked.isEmpty();
                rollDetail = growSubStat(doc, effectiveSeed, locked, pityTriggered);
                rolledSub = true;
                if (pityTriggered) {
                    resetPityCounter(itemUid);
                    rollDetail.put("ENHANCE_PITY_TRIGGERED", true);
                } else if (!locked.isEmpty() && !hitLockedSub(rollDetail, locked)) {
                    incrementPityCounter(itemUid);
                } else if (!locked.isEmpty() && hitLockedSub(rollDetail, locked)) {
                    resetPityCounter(itemUid);
                }
                rollDetail.put("pityCounter", getPityCounter(itemUid));
            }
            persistAffix(playerId, itemUid, doc);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ok", true);
            body.put("playerId", playerId);
            body.put("itemUid", itemUid);
            body.put("enhanceLevel", newLevel);
            body.put("materialCost", matCost);
            body.put("goldCost", goldCost);
            body.put("subStatRolled", rolledSub);
            body.put("rollDetail", rollDetail);
            if (Boolean.TRUE.equals(rollDetail.get("ENHANCE_PITY_TRIGGERED"))) {
                body.put("ENHANCE_PITY_TRIGGERED", true);
                body.put("clientVfx", "GOLDEN_PITY_FLASH");
            }
            body.put("affix", Map.copyOf(doc));
            body.put("seed", seed != 0 ? seed : (playerId * 31L + itemUid * 17L + newLevel));
            return body;
        } finally {
            unlock(playerId, itemUid, lockToken);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> growSubStat(Map<String, Object> doc, long seed, java.util.Set<Integer> locked) {
        return growSubStat(doc, seed, locked, false);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> growSubStat(
            Map<String, Object> doc, long seed, java.util.Set<Integer> locked, boolean pityForceLocked) {
        Random rng = new Random(seed);
        List<Map<String, Object>> subs = doc.get("subStats") instanceof List<?> list
                ? new ArrayList<>((List<Map<String, Object>>) list) : new ArrayList<>();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("lockedIndexes", List.copyOf(locked == null ? java.util.Set.of() : locked));
        java.util.Set<Integer> lockSet = locked == null ? java.util.Set.of() : locked;
        if (subs.size() < 4) {
            String stat = EquipRandomizer.SUB_STATS[rng.nextInt(EquipRandomizer.SUB_STATS.length)];
            double val = Math.round((3.0 + rng.nextDouble() * 4.0) * 100.0) / 100.0;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("stat", stat);
            row.put("value", val);
            subs.add(row);
            detail.put("action", "ADD");
            detail.put("stat", stat);
            detail.put("delta", val);
        } else {
            List<Integer> candidates = new ArrayList<>();
            for (int i = 0; i < subs.size(); i++) {
                if (!lockSet.contains(i)) {
                    candidates.add(i);
                }
            }
            if (candidates.isEmpty()) {
                detail.put("action", "SKIPPED_ALL_LOCKED");
                detail.put("skipped", true);
                doc.put("subStats", subs);
                return detail;
            }
            int idx;
            if (pityForceLocked && !lockSet.isEmpty()) {
                idx = lockSet.iterator().next();
                detail.put("pityForced", true);
            } else {
                idx = candidates.get(rng.nextInt(candidates.size()));
            }
            Map<String, Object> row = new LinkedHashMap<>(subs.get(idx));
            double before = row.get("value") instanceof Number n ? n.doubleValue() : 0;
            double delta = Math.round((1.5 + rng.nextDouble() * 2.5) * 100.0) / 100.0;
            row.put("value", Math.round((before + delta) * 100.0) / 100.0);
            subs.set(idx, row);
            detail.put("action", "UPGRADE");
            detail.put("index", idx);
            detail.put("stat", row.get("stat"));
            detail.put("delta", delta);
            detail.put("before", before);
            detail.put("after", row.get("value"));
            detail.put("skippedLocked", !lockSet.isEmpty() && !pityForceLocked);
            detail.put("hitLockedSub", lockSet.contains(idx));
        }
        doc.put("subStats", subs);
        return detail;
    }

    private boolean hitLockedSub(Map<String, Object> rollDetail, java.util.Set<Integer> locked) {
        if (rollDetail == null || locked == null || locked.isEmpty()) {
            return false;
        }
        Object idx = rollDetail.get("index");
        return idx instanceof Number n && locked.contains(n.intValue());
    }

    private int getPityCounter(long itemUid) {
        StringRedisTemplate redis = redisProvider == null ? null : redisProvider.getIfAvailable();
        if (redis != null) {
            try {
                String v = redis.opsForValue().get(PITY_KEY + itemUid);
                return v == null ? 0 : Integer.parseInt(v);
            } catch (Exception e) {
                log.warn("pity redis read failed: {}", e.getMessage());
            }
        }
        return localPityCounter.getOrDefault(itemUid, 0);
    }

    private void incrementPityCounter(long itemUid) {
        int next = getPityCounter(itemUid) + 1;
        StringRedisTemplate redis = redisProvider == null ? null : redisProvider.getIfAvailable();
        if (redis != null) {
            try {
                redis.opsForValue().set(PITY_KEY + itemUid, String.valueOf(next));
                return;
            } catch (Exception e) {
                log.warn("pity redis write failed: {}", e.getMessage());
            }
        }
        localPityCounter.put(itemUid, next);
    }

    private void resetPityCounter(long itemUid) {
        StringRedisTemplate redis = redisProvider == null ? null : redisProvider.getIfAvailable();
        if (redis != null) {
            try {
                redis.delete(PITY_KEY + itemUid);
            } catch (Exception e) {
                log.warn("pity redis reset failed: {}", e.getMessage());
            }
        }
        localPityCounter.remove(itemUid);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadAffixDoc(long playerId, long itemUid) {
        if (memoryAffix.containsKey(itemUid)) {
            return new LinkedHashMap<>(memoryAffix.get(itemUid));
        }
        PlayerBagItemRepository repo = bagRepo == null ? null : bagRepo.getIfAvailable();
        if (repo != null) {
            Optional<PlayerBagItem> opt = repo.findByIdAndPlayerId(itemUid, playerId);
            if (opt.isEmpty()) {
                return null;
            }
            EquipRandomizer.AffixRoll roll = randomizer.fromBlob(opt.get().getAffixBlob());
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("mainStat", roll.mainStat());
            doc.put("mainValue", roll.mainValue());
            doc.put("subStats", new ArrayList<>(roll.subStats()));
            doc.put("enhanceLevel", 0);
            // 尝试从 blob 读 enhanceLevel
            try {
                if (opt.get().getAffixBlob() != null) {
                    Map<String, Object> raw = objectMapper.readValue(opt.get().getAffixBlob(), Map.class);
                    if (raw.get("enhanceLevel") instanceof Number n) {
                        doc.put("enhanceLevel", n.intValue());
                    }
                    if (raw.get("subStats") instanceof List<?>) {
                        doc.put("subStats", raw.get("subStats"));
                    }
                }
            } catch (Exception ignored) {
                // keep roll defaults
            }
            return doc;
        }
        return null;
    }

    /** 单测/无 DB：预置一件可强化装备。 */
    public void putMemoryItem(long itemUid, EquipRandomizer.AffixRoll roll) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("mainStat", roll.mainStat());
        doc.put("mainValue", roll.mainValue());
        doc.put("subStats", new ArrayList<>(roll.subStats()));
        doc.put("enhanceLevel", 0);
        memoryAffix.put(itemUid, doc);
    }

    private void persistAffix(long playerId, long itemUid, Map<String, Object> doc) {
        memoryAffix.put(itemUid, new LinkedHashMap<>(doc));
        PlayerBagItemRepository repo = bagRepo == null ? null : bagRepo.getIfAvailable();
        if (repo == null) {
            return;
        }
        Optional<PlayerBagItem> opt = repo.findByIdAndPlayerId(itemUid, playerId);
        if (opt.isEmpty()) {
            return;
        }
        try {
            byte[] blob = objectMapper.writeValueAsBytes(doc);
            PlayerBagItem row = opt.get();
            row.setAffixBlob(blob);
            repo.save(row);
        } catch (Exception e) {
            log.warn("persist affix failed: {}", e.getMessage());
            // fallback minimal
            EquipRandomizer.AffixRoll roll = new EquipRandomizer.AffixRoll(
                    String.valueOf(doc.getOrDefault("mainStat", "ATK_PCT")),
                    doc.get("mainValue") instanceof Number n ? n.doubleValue() : 0,
                    doc.get("subStats") instanceof List<?> list
                            ? (List<Map<String, Object>>) list : List.of());
            opt.get().setAffixBlob(randomizer.toBlob(roll));
            repo.save(opt.get());
        }
    }

    private String tryLock(long playerId, long itemUid) {
        String lockKey = playerId + ":" + itemUid;
        String token = UUID.randomUUID().toString();
        StringRedisTemplate redis = redisProvider == null ? null : redisProvider.getIfAvailable();
        if (redis != null) {
            try {
                Boolean ok = redis.opsForValue().setIfAbsent(LOCK_KEY + lockKey, token, LOCK_TTL);
                if (Boolean.TRUE.equals(ok)) {
                    return token;
                }
                return null;
            } catch (Exception e) {
                log.warn("enhance lock redis failed, fallback local: {}", e.getMessage());
            }
        }
        return localLocks.putIfAbsent(lockKey, token) == null ? token : null;
    }

    private void unlock(long playerId, long itemUid, String token) {
        String lockKey = playerId + ":" + itemUid;
        StringRedisTemplate redis = redisProvider == null ? null : redisProvider.getIfAvailable();
        if (redis != null) {
            try {
                String cur = redis.opsForValue().get(LOCK_KEY + lockKey);
                if (token != null && token.equals(cur)) {
                    redis.delete(LOCK_KEY + lockKey);
                }
                return;
            } catch (Exception e) {
                log.warn("enhance unlock redis failed: {}", e.getMessage());
            }
        }
        localLocks.remove(lockKey, token);
    }

    private static int asInt(Object v, int dft) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return dft;
        }
    }
}
