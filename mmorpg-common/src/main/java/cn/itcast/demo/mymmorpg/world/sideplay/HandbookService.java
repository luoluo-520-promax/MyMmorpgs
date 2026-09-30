package cn.itcast.demo.mymmorpg.world.sideplay;

import cn.itcast.demo.mymmorpg.support.SimpleBloomFilter;
import cn.itcast.demo.mymmorpg.world.explore.RegionImpactService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 生活图鉴：生物/鱼类/料理首次发现与完美烹饪计数。
 * Redis 语义 {@code handbook:{playerId}} → field=creature_id|fish_id|food_id，value=discovered_time|perfect_cook_count。
 */
@Service
public class HandbookService {

    public enum EntryKind {
        CREATURE, FISH, FOOD
    }

    public record EntryValue(long discoveredTimeMs, int perfectCookCount) {
    }

    public static final double COLLECTOR_THRESHOLD = 0.80;
    public static final String NAMECARD_ITEM = "namecard_handbook_collector";
    public static final int DAILY_GATHER_LIMIT = 40;
    public static final long BARREN_DURATION_MS = 86_400_000L;

    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, EntryValue>> handbooks =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<EntryKind, ConcurrentHashMap<String, Boolean>> catalog =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> mailGranted = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> dailyGather = new ConcurrentHashMap<>();
    private final AtomicLong mailIdSeq = new AtomicLong(9_600_000L);
    private final SimpleBloomFilter bloom = new SimpleBloomFilter(8192, 0.01);
    private RegionImpactService regions;

    public HandbookService() {
        for (EntryKind k : EntryKind.values()) {
            catalog.put(k, new ConcurrentHashMap<>());
        }
    }

    public HandbookService(RegionImpactService regions) {
        this();
        this.regions = regions;
    }

    public void bindRegions(RegionImpactService regions) {
        this.regions = regions;
    }

    public void registerCatalog(EntryKind kind, String entryId) {
        if (kind != null && entryId != null && !entryId.isBlank()) {
            catalog.computeIfAbsent(kind, k -> new ConcurrentHashMap<>()).put(entryId.trim(), true);
        }
    }

    private static String field(EntryKind kind, String id) {
        return kind.name().toLowerCase() + "_" + id;
    }

    private static String redisKey(long playerId) {
        return "handbook:" + playerId;
    }

    /**
     * 布隆过滤器预判：false 则一定未解锁，可跳过 Redis 查询。
     */
    public boolean mightHaveDiscovered(long playerId, EntryKind kind, String entryId) {
        return bloom.mightContain(bloomKey(playerId, kind, entryId));
    }

    private static String bloomKey(long playerId, EntryKind kind, String entryId) {
        return playerId + ":" + kind.name() + ":" + entryId;
    }

    /**
     * 首次发现：发放发现奖励标记；重复发现仅更新 perfect 计数（料理）。
     */
    public Map<String, Object> discover(
            long playerId, EntryKind kind, String entryId, long nowMs, boolean perfectCook) {
        if (kind == null || entryId == null || entryId.isBlank()) {
            return Map.of("ok", false, "error", "entry_required");
        }
        registerCatalog(kind, entryId);
        String f = field(kind, entryId.trim());
        ConcurrentHashMap<String, EntryValue> book =
                handbooks.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>());
        EntryValue prev = book.get(f);
        boolean first = prev == null;
        int perfect = prev == null ? 0 : prev.perfectCookCount();
        if (perfectCook) {
            perfect += 1;
        }
        long discovered = first ? nowMs : prev.discoveredTimeMs();
        book.put(f, new EntryValue(discovered, perfect));
        bloom.add(bloomKey(playerId, kind, entryId.trim()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("redisKey", redisKey(playerId));
        body.put("field", f);
        body.put("firstDiscover", first);
        body.put("discoveredTimeMs", discovered);
        body.put("perfectCookCount", perfect);
        if (first) {
            body.put("grantPlans", List.of(Map.of(
                    "itemId", "handbook_first_" + kind.name().toLowerCase(),
                    "count", 1)));
            body.put("idempotencyKey", "handbook:first:" + playerId + ":" + f);
        }
        Map<String, Object> progress = progress(playerId, kind);
        body.put("progress", progress);
        if (Boolean.TRUE.equals(progress.get("collectorReady"))) {
            body.put("mailGrant", tryGrantCollectorNamecard(playerId, nowMs));
        }
        return body;
    }

    public Map<String, Object> progress(long playerId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("redisKey", redisKey(playerId));
        for (EntryKind k : EntryKind.values()) {
            body.put(k.name().toLowerCase(), progress(playerId, k));
        }
        return body;
    }

    public Map<String, Object> progress(long playerId, EntryKind kind) {
        int total = catalog.getOrDefault(kind, new ConcurrentHashMap<>()).size();
        int owned = 0;
        ConcurrentHashMap<String, EntryValue> book = handbooks.getOrDefault(playerId, new ConcurrentHashMap<>());
        for (String id : catalog.getOrDefault(kind, new ConcurrentHashMap<>()).keySet()) {
            if (book.containsKey(field(kind, id))) {
                owned++;
            }
        }
        double rate = total <= 0 ? 0.0 : (double) owned / total;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("kind", kind.name());
        body.put("owned", owned);
        body.put("total", total);
        body.put("rate", Math.round(rate * 1000.0) / 1000.0);
        body.put("collectorReady", rate >= COLLECTOR_THRESHOLD);
        return body;
    }

    /**
     * 达 80% 全区生物收集 → 系统邮件发放限定名片（复用 MailItemGrantPort 语义：grantPlans + mailId）。
     */
    public Map<String, Object> tryGrantCollectorNamecard(long playerId, long nowMs) {
        Map<String, Object> creature = progress(playerId, EntryKind.CREATURE);
        if (!Boolean.TRUE.equals(creature.get("collectorReady"))) {
            return Map.of("ok", false, "granted", false, "reason", "threshold_not_met");
        }
        String grantKey = playerId + ":collector";
        if (Boolean.TRUE.equals(mailGranted.putIfAbsent(grantKey, true))) {
            return Map.of("ok", true, "granted", false, "alreadyGranted", true);
        }
        long mailId = mailIdSeq.incrementAndGet();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("granted", true);
        body.put("mailId", mailId);
        body.put("title", "图鉴收集者");
        body.put("grantPlans", List.of(Map.of("itemId", NAMECARD_ITEM, "count", 1)));
        body.put("idempotencyKey", "mail:" + mailId);
        body.put("mailItemGrantPort", true);
        body.put("atMs", nowMs);
        return body;
    }

    /**
     * 过度采集（摘花/采矿）：超每日阈值 → 区域资源贫瘠 Debuff（24h 产出降低，非伤害）。
     */
    public Map<String, Object> recordGather(
            long playerId, String regionId, String resourceType, long nowMs) {
        String dayKey = playerId + ":" + regionId + ":" + (nowMs / 86_400_000L);
        int count = dailyGather.computeIfAbsent(dayKey, k -> new AtomicInteger(0)).incrementAndGet();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("regionId", regionId);
        body.put("resourceType", resourceType);
        body.put("dailyGatherCount", count);
        body.put("dailyLimit", DAILY_GATHER_LIMIT);
        if (count > DAILY_GATHER_LIMIT && regions != null) {
            Map<String, Object> barren = regions.applyResourceBarren(
                    regionId, 0.5, nowMs + BARREN_DURATION_MS);
            body.put("overGather", true);
            body.put("resourceBarren", barren);
            body.put("debuff", "RESOURCE_BARREN");
            body.put("hint", "该区域未来24小时资源产出降低");
        } else {
            body.put("overGather", false);
        }
        return body;
    }

    public EntryValue get(long playerId, EntryKind kind, String entryId) {
        return handbooks.getOrDefault(playerId, new ConcurrentHashMap<>())
                .get(field(kind, entryId));
    }

    public List<String> catalogIds(EntryKind kind) {
        return new ArrayList<>(catalog.getOrDefault(kind, new ConcurrentHashMap<>()).keySet());
    }
}
