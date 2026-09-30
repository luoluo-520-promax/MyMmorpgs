package cn.itcast.demo.mymmorpg.world.economy;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家交易所：Redis SortedSet 按价格排序；一口价 / 竞拍；5% 手续费。
 * 逻辑可拆为独立 trade-service（端口 8999）。
 */
@Service
public class AuctionHouseService {

    public static final int PORT = 8999;
    public static final double FEE_RATE = 0.05;
    public static final double CROSS_SHARD_FEE_RATE = 0.10;
    public static final long AUCTION_TTL_MS = 24 * 3600_000L;
    public static final long OUTLIER_COOLDOWN_MS = 24 * 3600_000L;
    public static final double ZSCORE_LIMIT = 3.0;

    public enum ListingType {
        BUYOUT, AUCTION
    }

    public record Listing(
            String listingId,
            long sellerId,
            String itemId,
            int count,
            long price,
            ListingType type,
            long expireAt,
            long highestBid,
            long highestBidder,
            String regionShardId,
            boolean crossShard,
            String priceReason) {
        public Listing(
                String listingId, long sellerId, String itemId, int count, long price,
                ListingType type, long expireAt, long highestBid, long highestBidder) {
            this(listingId, sellerId, itemId, count, price, type, expireAt,
                    highestBid, highestBidder, "local", false, "");
        }
    }

    private final ConcurrentHashMap<String, Listing> listings = new ConcurrentHashMap<>();
    /** itemId → listingIds 按 price 排序（ZSET 语义） */
    private final ConcurrentHashMap<String, List<String>> priceIndex = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Map<String, Integer>> frozenBag = new ConcurrentHashMap<>();
    private final List<Map<String, Object>> feeSink = new ArrayList<>();
    /** itemId → 近 7 天成交价 */
    private final ConcurrentHashMap<String, List<Long>> tradeHistory = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> outlierCooldownUntil = new ConcurrentHashMap<>();

    public Map<String, Object> listBuyout(
            long sellerId, String itemId, int count, long price, long nowMs) {
        return list(sellerId, itemId, count, price, ListingType.BUYOUT,
                nowMs + AUCTION_TTL_MS, nowMs, "local", false, null);
    }

    public Map<String, Object> listBuyout(
            long sellerId, String itemId, int count, long price, long nowMs,
            String regionShardId, boolean crossShard, String priceReason) {
        return list(sellerId, itemId, count, price, ListingType.BUYOUT,
                nowMs + AUCTION_TTL_MS, nowMs, regionShardId, crossShard, priceReason);
    }

    public Map<String, Object> listAuction(
            long sellerId, String itemId, int count, long startPrice, long nowMs) {
        return list(sellerId, itemId, count, startPrice, ListingType.AUCTION,
                nowMs + AUCTION_TTL_MS, nowMs, "local", false, null);
    }

    /** 记录成交价，供 PriceStabilityIndex 使用。 */
    public void recordTradePrice(String itemId, long price) {
        tradeHistory.computeIfAbsent(itemId == null ? "" : itemId.trim(), k -> new ArrayList<>())
                .add(price);
    }

    public Map<String, Object> priceStabilityIndex(String itemId, long askPrice) {
        List<Long> hist = tradeHistory.getOrDefault(itemId == null ? "" : itemId.trim(), List.of());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("itemId", itemId);
        body.put("askPrice", askPrice);
        if (hist.size() < 3) {
            body.put("zScore", 0.0);
            body.put("outlier", false);
            body.put("sampleSize", hist.size());
            return body;
        }
        double mean = hist.stream().mapToLong(Long::longValue).average().orElse(0);
        double variance = 0;
        for (long p : hist) {
            variance += (p - mean) * (p - mean);
        }
        double std = Math.sqrt(variance / hist.size());
        double z = std < 1e-6 ? 0 : (askPrice - mean) / std;
        body.put("mean", Math.round(mean * 100.0) / 100.0);
        body.put("std", Math.round(std * 100.0) / 100.0);
        body.put("zScore", Math.round(z * 1000.0) / 1000.0);
        body.put("outlier", Math.abs(z) > ZSCORE_LIMIT);
        body.put("sampleSize", hist.size());
        return body;
    }

    private Map<String, Object> list(
            long sellerId, String itemId, int count, long price, ListingType type, long expireAt,
            long nowMs, String regionShardId, boolean crossShard, String priceReason) {
        if (count <= 0 || price <= 0) {
            return Map.of("ok", false, "error", "invalid_params");
        }
        Long coolUntil = outlierCooldownUntil.get(sellerId);
        if (coolUntil != null && coolUntil > nowMs) {
            return Map.of("ok", false, "error", "outlier_cooldown",
                    "remainMs", coolUntil - nowMs);
        }
        Map<String, Object> psi = priceStabilityIndex(itemId, price);
        boolean outlier = Boolean.TRUE.equals(psi.get("outlier"));
        if (outlier && (priceReason == null || priceReason.isBlank())) {
            return Map.of("ok", false, "error", "price_reason_required",
                    "priceStabilityIndex", psi,
                    "hint", "挂单价超过均值 3σ，须填写定价理由");
        }
        if (outlier) {
            outlierCooldownUntil.put(sellerId, nowMs + OUTLIER_COOLDOWN_MS);
        }

        String shard = regionShardId == null || regionShardId.isBlank() ? "local" : regionShardId.trim();
        String id = "ah-" + UUID.randomUUID();
        Listing listing = new Listing(id, sellerId, itemId, count, price, type, expireAt, 0, 0,
                shard, crossShard, priceReason == null ? "" : priceReason.trim());
        listings.put(id, listing);
        priceIndex.computeIfAbsent(itemId, k -> new ArrayList<>()).add(id);
        frozenBag.computeIfAbsent(sellerId, k -> new ConcurrentHashMap<>())
                .merge(itemId, count, Integer::sum);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("listingId", id);
        body.put("type", type.name());
        body.put("frozen", true);
        body.put("via", "bag-service-freeze");
        body.put("expireAt", expireAt);
        body.put("tradeServicePort", PORT);
        body.put("atMs", nowMs);
        body.put("regionShardId", shard);
        body.put("crossShard", crossShard);
        body.put("feeRate", crossShard ? CROSS_SHARD_FEE_RATE : FEE_RATE);
        body.put("priceStabilityIndex", psi);
        if (outlier) {
            body.put("outlierCooldownMs", OUTLIER_COOLDOWN_MS);
            body.put("priceReason", priceReason);
        }
        if (crossShard) {
            body.put("mailVia", "hall-service-international-post");
            body.put("redisBinding", "region_shard_id");
        }
        return body;
    }

    public Map<String, Object> buyout(long buyerId, String listingId) {
        Listing L = listings.get(listingId);
        if (L == null) {
            return Map.of("ok", false, "error", "listing_not_found");
        }
        if (L.type() != ListingType.BUYOUT) {
            return Map.of("ok", false, "error", "not_buyout");
        }
        double feeRate = L.crossShard() ? CROSS_SHARD_FEE_RATE : FEE_RATE;
        long fee = Math.round(L.price() * feeRate);
        long sellerGain = L.price() - fee;
        synchronized (feeSink) {
            feeSink.add(Map.of("listingId", listingId, "fee", fee, "sink", "system"));
        }
        unfreeze(L.sellerId(), L.itemId(), L.count());
        listings.remove(listingId);
        removeFromIndex(L.itemId(), listingId);
        recordTradePrice(L.itemId(), L.price());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("buyerId", buyerId);
        body.put("sellerId", L.sellerId());
        body.put("itemId", L.itemId());
        body.put("count", L.count());
        body.put("price", L.price());
        body.put("fee", fee);
        body.put("sellerGain", sellerGain);
        body.put("feeRate", feeRate);
        body.put("regionShardId", L.regionShardId());
        body.put("crossShard", L.crossShard());
        return body;
    }

    public Map<String, Object> bid(long bidderId, String listingId, long bidAmount, long nowMs) {
        Listing L = listings.get(listingId);
        if (L == null) {
            return Map.of("ok", false, "error", "listing_not_found");
        }
        if (L.type() != ListingType.AUCTION) {
            return Map.of("ok", false, "error", "not_auction");
        }
        if (nowMs > L.expireAt()) {
            return Map.of("ok", false, "error", "auction_expired");
        }
        if (bidAmount <= Math.max(L.price(), L.highestBid())) {
            return Map.of("ok", false, "error", "bid_too_low");
        }
        Listing next = new Listing(L.listingId(), L.sellerId(), L.itemId(), L.count(), L.price(),
                L.type(), L.expireAt(), bidAmount, bidderId,
                L.regionShardId(), L.crossShard(), L.priceReason());
        listings.put(listingId, next);
        return Map.of("ok", true, "listingId", listingId, "highestBid", bidAmount,
                "highestBidder", bidderId, "delayQueue", "Redisson", "ttlMs", L.expireAt() - nowMs);
    }

    public Map<String, Object> settleAuction(String listingId, long nowMs) {
        Listing L = listings.get(listingId);
        if (L == null) {
            return Map.of("ok", false, "error", "listing_not_found");
        }
        if (L.type() != ListingType.AUCTION) {
            return Map.of("ok", false, "error", "not_auction");
        }
        if (nowMs < L.expireAt()) {
            return Map.of("ok", false, "error", "not_expired");
        }
        if (L.highestBidder() <= 0) {
            unfreeze(L.sellerId(), L.itemId(), L.count());
            listings.remove(listingId);
            removeFromIndex(L.itemId(), listingId);
            return Map.of("ok", true, "result", "expired_no_bid");
        }
        double feeRate = L.crossShard() ? CROSS_SHARD_FEE_RATE : FEE_RATE;
        long fee = Math.round(L.highestBid() * feeRate);
        synchronized (feeSink) {
            feeSink.add(Map.of("listingId", listingId, "fee", fee, "sink", "system"));
        }
        unfreeze(L.sellerId(), L.itemId(), L.count());
        listings.remove(listingId);
        removeFromIndex(L.itemId(), listingId);
        recordTradePrice(L.itemId(), L.highestBid());
        return Map.of("ok", true, "result", "sold", "buyerId", L.highestBidder(),
                "price", L.highestBid(), "fee", fee, "sellerGain", L.highestBid() - fee,
                "feeRate", feeRate);
    }

    public List<Map<String, Object>> listByPriceAsc(String itemId) {
        List<String> ids = priceIndex.getOrDefault(itemId, List.of());
        return ids.stream()
                .map(listings::get)
                .filter(l -> l != null)
                .sorted(Comparator.comparingLong(Listing::price))
                .map(l -> Map.<String, Object>of(
                        "listingId", l.listingId(),
                        "price", l.price(),
                        "count", l.count(),
                        "type", l.type().name()))
                .toList();
    }

    public int frozenCount(long playerId, String itemId) {
        return frozenBag.getOrDefault(playerId, Map.of()).getOrDefault(itemId, 0);
    }

    private void unfreeze(long sellerId, String itemId, int count) {
        Map<String, Integer> bag = frozenBag.get(sellerId);
        if (bag == null) {
            return;
        }
        bag.computeIfPresent(itemId, (k, v) -> {
            int n = v - count;
            return n <= 0 ? null : n;
        });
    }

    private void removeFromIndex(String itemId, String listingId) {
        List<String> ids = priceIndex.get(itemId);
        if (ids != null) {
            ids.remove(listingId);
        }
    }
}
