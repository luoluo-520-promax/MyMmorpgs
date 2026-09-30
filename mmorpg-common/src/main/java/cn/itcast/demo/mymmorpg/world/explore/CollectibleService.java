package cn.itcast.demo.mymmorpg.world.explore;

import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.support.SimpleBloomFilter;
import cn.itcast.demo.mymmorpg.world.ownership.AccessLevel;
import cn.itcast.demo.mymmorpg.world.ownership.WorldOwnershipContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 收集物分级体系：普通/精致/珍贵宝箱 + 神瞳类特殊收集物（提升探索技能等级）。
 * 升级自「惊喜彩蛋」线性链路，形成纵深探索循环。
 */
@Service
public class CollectibleService {

    public enum Tier {
        /** 普通宝箱：金币等 */
        COMMON_CHEST,
        /** 精致宝箱：抽卡资源 */
        FINE_CHEST,
        /** 珍贵宝箱：高价值抽卡/养成 */
        PRECIOUS_CHEST,
        /** 神瞳类：提升体力/探索技能等级 */
        OCULUS
    }

    /** 可见性：HIDDEN 需亲密度寻宝等协助揭示 */
    public enum Visibility {
        VISIBLE, HIDDEN
    }

    public record CollectibleDef(
            String collectibleId,
            String title,
            Tier tier,
            int regionId,
            float x, float y, float z,
            float radius,
            String rewardItemId,
            int rewardCount,
            /** OCULUS：探索技能等级增量 */
            int skillLevelBonus,
            /** OCULUS：体力上限增量 */
            int staminaBonus,
            /** 联机访问级别：神瞳/关键宝箱默认 HOST_ONLY */
            AccessLevel accessLevel) {

        public CollectibleDef(
                String collectibleId, String title, Tier tier, int regionId,
                float x, float y, float z, float radius,
                String rewardItemId, int rewardCount, int skillLevelBonus, int staminaBonus) {
            this(collectibleId, title, tier, regionId, x, y, z, radius,
                    rewardItemId, rewardCount, skillLevelBonus, staminaBonus,
                    tier == Tier.OCULUS || tier == Tier.PRECIOUS_CHEST
                            ? AccessLevel.HOST_ONLY : AccessLevel.ALL_SHARE);
        }

        public CollectibleDef {
            tier = tier == null ? Tier.COMMON_CHEST : tier;
            radius = radius <= 0f ? 4f : radius;
            rewardCount = Math.max(1, rewardCount);
            skillLevelBonus = Math.max(0, skillLevelBonus);
            staminaBonus = Math.max(0, staminaBonus);
            accessLevel = accessLevel == null
                    ? (tier == Tier.OCULUS || tier == Tier.PRECIOUS_CHEST
                    ? AccessLevel.HOST_ONLY : AccessLevel.ALL_SHARE)
                    : accessLevel;
        }
    }

    private final ConcurrentHashMap<String, CollectibleDef> defs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Set<String>> collected = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, AtomicInteger> exploreSkillLevel = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, AtomicInteger> staminaCapBonus = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Visibility> visibility = new ConcurrentHashMap<>();
    private final SimpleBloomFilter collectedBloom = new SimpleBloomFilter(4096, 0.01);
    private WorldOwnershipContext ownership;

    public static final long COLLECT_RESUME_MS = 5_000L;
    public static final int COLLECT_AUTO_COMPLETE_PERCENT = 80;

    public record CollectProgress(
            String progressId, long playerId, String collectibleId,
            int percent, long startedAtMs, long pausedAtMs, long totalTickMs) {
    }

    private final ConcurrentHashMap<String, CollectProgress> activeProgress = new ConcurrentHashMap<>();

    private static String progressKey(long playerId, String objId) {
        return playerId + ":" + (objId == null ? "" : objId.trim());
    }

    /**
     * 动态掉落品质：世界等级 + 区域探索度 + 热度密度系数抬升宝箱 GrantPlan 权重。
     */
    public record DynamicLootTier(int worldLevel, float regionExplorationRate, double heatDensityCoefficient) {
        public DynamicLootTier(int worldLevel, float regionExplorationRate) {
            this(worldLevel, regionExplorationRate, 1.0);
        }

        public DynamicLootTier {
            worldLevel = Math.max(0, Math.min(8, worldLevel));
            regionExplorationRate = Math.max(0f, Math.min(1f, regionExplorationRate));
            heatDensityCoefficient = heatDensityCoefficient <= 0 ? 1.0 : heatDensityCoefficient;
        }

        /** 0=低 / 1=中 / 2=高品质权重桶 */
        public int qualityBucket() {
            float score = worldLevel * 0.12f + regionExplorationRate * 0.6f;
            score *= (float) Math.min(2.0, heatDensityCoefficient);
            if (score >= 0.75f) {
                return 2;
            }
            if (score >= 0.4f) {
                return 1;
            }
            return 0;
        }

        public double countMultiplier() {
            return (1.0 + worldLevel * 0.08 + regionExplorationRate * 0.4) * heatDensityCoefficient;
        }
    }

    public void bindOwnership(WorldOwnershipContext ownership) {
        this.ownership = ownership;
    }

    public void register(CollectibleDef def) {
        defs.put(def.collectibleId(), def);
        visibility.putIfAbsent(def.collectibleId(), Visibility.VISIBLE);
    }

    public void setVisibility(String collectibleId, Visibility vis) {
        if (collectibleId != null && !collectibleId.isBlank() && vis != null) {
            visibility.put(collectibleId.trim(), vis);
        }
    }

    public Visibility visibilityOf(String collectibleId) {
        return visibility.getOrDefault(collectibleId == null ? "" : collectibleId.trim(), Visibility.VISIBLE);
    }

    public Map<String, Object> collect(long playerId, String collectibleId, float px, float py, float pz) {
        return collect(playerId, collectibleId, px, py, pz, null);
    }

    /** 开始分帧采集，返回 progressId 与初始进度。 */
    public Map<String, Object> startCollect(long playerId, String collectibleId, long nowMs) {
        CollectibleDef def = defs.get(collectibleId);
        if (def == null) {
            return Map.of("ok", false, "error", "collectible_not_found");
        }
        String key = progressKey(playerId, collectibleId);
        CollectProgress existing = activeProgress.get(key);
        int resumePercent = 0;
        if (existing != null && existing.pausedAtMs() > 0
                && nowMs - existing.pausedAtMs() <= COLLECT_RESUME_MS) {
            resumePercent = existing.percent();
        }
        String progressId = key + ":" + nowMs;
        CollectProgress prog = new CollectProgress(
                progressId, playerId, collectibleId, resumePercent, nowMs, 0, 0);
        activeProgress.put(key, prog);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("progressId", progressId);
        body.put("collectibleId", collectibleId);
        body.put("collectedPercent", resumePercent);
        body.put("resumed", resumePercent > 0);
        body.put("redisKey", "collect:progress:" + playerId + ":" + collectibleId);
        return body;
    }

    /** 分帧推进采集进度。 */
    public Map<String, Object> tickCollect(String progressId, long tickMs, long nowMs) {
        CollectProgress prog = findProgress(progressId);
        if (prog == null) {
            return Map.of("ok", false, "error", "progress_not_found");
        }
        int durationMs = Math.max(1000, 3000);
        long total = prog.totalTickMs() + Math.max(1, tickMs);
        int percent = (int) Math.min(100, prog.percent() + (tickMs * 100 / durationMs));
        CollectProgress next = new CollectProgress(
                prog.progressId(), prog.playerId(), prog.collectibleId(),
                percent, prog.startedAtMs(), 0, total);
        activeProgress.put(progressKey(prog.playerId(), prog.collectibleId()), next);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("progressId", progressId);
        body.put("collectedPercent", percent);
        if (percent >= 100 || (prog.percent() < COLLECT_AUTO_COMPLETE_PERCENT && percent >= COLLECT_AUTO_COMPLETE_PERCENT)) {
            body.put("readyToComplete", true);
            if (percent >= COLLECT_AUTO_COMPLETE_PERCENT && percent < 100) {
                body.put("autoCompleteNearFinish", true);
                body.put("collectedPercent", 100);
            }
        }
        return body;
    }

    /** 受击打断：暂停采集，5 秒内可续传。 */
    public Map<String, Object> pauseCollect(long playerId, String collectibleId, long nowMs) {
        String key = progressKey(playerId, collectibleId);
        CollectProgress prog = activeProgress.get(key);
        if (prog == null) {
            return Map.of("ok", false, "error", "no_active_progress");
        }
        CollectProgress paused = new CollectProgress(
                prog.progressId(), prog.playerId(), prog.collectibleId(),
                prog.percent(), prog.startedAtMs(), nowMs, prog.totalTickMs());
        activeProgress.put(key, paused);
        return Map.of("ok", true, "paused", true, "collectedPercent", prog.percent(),
                "resumeWithinMs", COLLECT_RESUME_MS);
    }

    private CollectProgress findProgress(String progressId) {
        if (progressId == null || progressId.isBlank()) {
            return null;
        }
        for (CollectProgress p : activeProgress.values()) {
            if (progressId.equals(p.progressId())) {
                return p;
            }
        }
        return null;
    }

    public Map<String, Object> collect(
            long playerId, String collectibleId, float px, float py, float pz, DynamicLootTier lootTier) {
        return collect(playerId, collectibleId, px, py, pz, lootTier, null);
    }

    public Map<String, Object> collect(
            long playerId, String collectibleId, float px, float py, float pz,
            DynamicLootTier lootTier, String coopRoomId) {
        CollectibleDef def = defs.get(collectibleId);
        if (def == null) {
            return Map.of("ok", false, "error", "collectible_not_found");
        }
        if (ownership != null) {
            Map<String, Object> auth = ownership.authorizeInteract(
                    playerId, coopRoomId, def.accessLevel(), "collect:" + collectibleId);
            if (!Boolean.TRUE.equals(auth.get("authorized"))) {
                Map<String, Object> denied = new LinkedHashMap<>(auth);
                denied.put("ok", false);
                denied.put("error", "host_only_denied");
                denied.put("retcode", RetCode.HOST_ONLY_DENIED);
                denied.put("collectibleId", collectibleId);
                denied.put("accessLevel", def.accessLevel().name());
                return denied;
            }
        }
        String bloomKey = playerId + ":" + collectibleId;
        if (!collectedBloom.mightContain(bloomKey)) {
            // 布隆过滤器否定 → 一定未收集，跳过 Redis 穿透
        }
        Set<String> done = collected.computeIfAbsent(playerId, id -> ConcurrentHashMap.newKeySet());
        if (done.contains(collectibleId)) {
            return Map.of("ok", false, "error", "already_collected", "collectibleId", collectibleId);
        }
        float dx = px - def.x();
        float dy = py - def.y();
        float dz = pz - def.z();
        if (Math.sqrt(dx * dx + dy * dy + dz * dz) > def.radius()) {
            return Map.of("ok", false, "error", "out_of_range");
        }
        done.add(collectibleId);
        collectedBloom.add(bloomKey);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("collectibleId", collectibleId);
        body.put("tier", def.tier().name());
        body.put("title", def.title());
        body.put("collectedPercent", 100);
        List<Map<String, Object>> plans = buildGrantPlans(def, lootTier);
        body.put("grantPlans", plans);
        if (lootTier != null) {
            Map<String, Object> dyn = new LinkedHashMap<>();
            dyn.put("worldLevel", lootTier.worldLevel());
            dyn.put("regionExplorationRate", lootTier.regionExplorationRate());
            dyn.put("heatDensityCoefficient", lootTier.heatDensityCoefficient());
            dyn.put("qualityBucket", lootTier.qualityBucket());
            dyn.put("countMultiplier", lootTier.countMultiplier());
            if (lootTier.heatDensityCoefficient() > 1.0) {
                dyn.put("rarityBoostLabel", "稀有度提升");
                dyn.put("msgId", cn.itcast.demo.mymmorpg.protocol.MessageId.EXPLORE_LOOT_RARITY_BOOST_SC_NOTIFY);
            }
            body.put("dynamicLoot", dyn);
        }
        if (def.tier() == Tier.OCULUS) {
            int skillLv = exploreSkillLevel.computeIfAbsent(playerId, id -> new AtomicInteger(1))
                    .addAndGet(Math.max(1, def.skillLevelBonus()));
            int stamina = staminaCapBonus.computeIfAbsent(playerId, id -> new AtomicInteger(0))
                    .addAndGet(Math.max(1, def.staminaBonus()));
            body.put("exploreSkillLevel", skillLv);
            body.put("staminaCapBonus", stamina);
            body.put("attrBoost", Map.of("exploreSkillLevel", skillLv, "staminaCapBonus", stamina));
        }
        body.put("idempotencyKey", "collectible:" + playerId + ":" + collectibleId);
        activeProgress.remove(progressKey(playerId, collectibleId));
        return body;
    }

    private List<Map<String, Object>> buildGrantPlans(CollectibleDef def, DynamicLootTier lootTier) {
        String baseItem = def.rewardItemId() == null ? defaultItem(def.tier()) : def.rewardItemId();
        int baseCount = def.rewardCount();
        if (lootTier == null) {
            return List.of(Map.of("itemId", baseItem, "count", baseCount));
        }
        int count = Math.max(1, (int) Math.round(baseCount * lootTier.countMultiplier()));
        List<Map<String, Object>> plans = new ArrayList<>();
        plans.add(Map.of("itemId", upgradeItem(baseItem, lootTier.qualityBucket()), "count", count));
        // 高探索度额外掉落养成材料，避免后期低级材料无价值
        if (lootTier.qualityBucket() >= 1 && def.tier() != Tier.OCULUS) {
            plans.add(Map.of(
                    "itemId", lootTier.qualityBucket() >= 2 ? "asc_mat_high" : "asc_mat_mid",
                    "count", lootTier.qualityBucket()));
        }
        return plans;
    }

    private static String upgradeItem(String base, int bucket) {
        if (bucket <= 0) {
            return base;
        }
        if ("gold".equals(base)) {
            return bucket >= 2 ? "mora_sack_l" : "mora_sack_m";
        }
        if ("gacha_ticket".equals(base)) {
            return bucket >= 2 ? "intertwined_fate" : "acquaint_fate";
        }
        return base;
    }

    public Map<String, Object> progress(long playerId) {
        Set<String> done = collected.getOrDefault(playerId, Set.of());
        Map<String, Integer> byTier = new LinkedHashMap<>();
        for (Tier t : Tier.values()) {
            byTier.put(t.name(), 0);
        }
        int totalOculus = 0;
        int gotOculus = 0;
        for (CollectibleDef d : defs.values()) {
            byTier.merge(d.tier().name(), 1, Integer::sum);
            if (d.tier() == Tier.OCULUS) {
                totalOculus++;
                if (done.contains(d.collectibleId())) {
                    gotOculus++;
                }
            }
        }
        Map<String, Integer> gotByTier = new LinkedHashMap<>();
        for (Tier t : Tier.values()) {
            gotByTier.put(t.name(), 0);
        }
        for (String id : done) {
            CollectibleDef d = defs.get(id);
            if (d != null) {
                gotByTier.merge(d.tier().name(), 1, Integer::sum);
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("collectedCount", done.size());
        body.put("total", defs.size());
        body.put("byTierTotal", byTier);
        body.put("byTierCollected", gotByTier);
        body.put("oculus", Map.of("got", gotOculus, "total", totalOculus));
        body.put("exploreSkillLevel", exploreSkillLevel.getOrDefault(playerId, new AtomicInteger(1)).get());
        body.put("staminaCapBonus", staminaCapBonus.getOrDefault(playerId, new AtomicInteger(0)).get());
        body.put("collected", List.copyOf(done));
        return body;
    }

    public List<CollectibleDef> listByRegion(int regionId) {
        return defs.values().stream().filter(d -> d.regionId() == regionId).toList();
    }

    public List<CollectibleDef> listAll() {
        return List.copyOf(defs.values());
    }

    public Set<String> collectedIds(long playerId) {
        return collected.getOrDefault(playerId, Set.of());
    }

    public boolean isCollected(long playerId, String collectibleId) {
        return collectedIds(playerId).contains(collectibleId);
    }

    public int countOculiInRegion(int regionId) {
        int count = 0;
        for (CollectibleDef d : defs.values()) {
            if (d.regionId() == regionId && d.tier() == Tier.OCULUS) {
                count++;
            }
        }
        return count;
    }

    public int collectedOculiInRegion(long playerId, int regionId) {
        Set<String> done = collectedIds(playerId);
        int count = 0;
        for (CollectibleDef d : defs.values()) {
            if (d.regionId() == regionId && d.tier() == Tier.OCULUS && done.contains(d.collectibleId())) {
                count++;
            }
        }
        return count;
    }

    /** 未收集神瞳的模糊范围提示（供共鸣波 UI） */
    public List<Map<String, Object>> remainingOculiHints(long playerId, int regionId, float blurRadius) {
        Set<String> done = collectedIds(playerId);
        List<Map<String, Object>> hints = new ArrayList<>();
        for (CollectibleDef d : defs.values()) {
            if (d.regionId() == regionId && d.tier() == Tier.OCULUS && !done.contains(d.collectibleId())) {
                hints.add(Map.of(
                        "collectibleId", d.collectibleId(),
                        "hintX", d.x() + (blurRadius > 0 ? blurRadius * 0.3f : 0f),
                        "hintY", d.y(),
                        "hintZ", d.z() + (blurRadius > 0 ? blurRadius * 0.3f : 0f),
                        "blurRadiusM", blurRadius));
            }
        }
        return hints;
    }

    private static String defaultItem(Tier tier) {
        return switch (tier) {
            case COMMON_CHEST -> "gold";
            case FINE_CHEST -> "gacha_ticket";
            case PRECIOUS_CHEST -> "primogem";
            case OCULUS -> "oculus_fragment";
        };
    }
}
