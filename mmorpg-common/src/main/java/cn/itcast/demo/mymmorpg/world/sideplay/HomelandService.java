package cn.itcast.demo.mymmorpg.world.sideplay;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 家园种植 / 烹饪产出链（可拆独立微服务端口 8997）。惰性计算生长，不秒级轮询。
 */
@Service
public class HomelandService {

    public enum PlotState {
        EMPTY, PLANTED, READY
    }

    public record LandPlot(String plotId, long ownerId, PlotState state) {
    }

    public record CropInstance(
            String cropId,
            String plotId,
            String seedId,
            long plantTimeMs,
            long growMs,
            int waterCount,
            String quality) {
    }

    private final ConcurrentHashMap<String, LandPlot> plots = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CropInstance> crops = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Map<String, Integer>> harvestBag = new ConcurrentHashMap<>();

    public Map<String, Object> claimLand(long playerId, String plotId) {
        if (plotId == null || plotId.isBlank()) {
            return Map.of("ok", false, "error", "plot_id_required");
        }
        LandPlot existing = plots.get(plotId);
        if (existing != null && existing.ownerId() != playerId) {
            return Map.of("ok", false, "error", "plot_owned");
        }
        plots.put(plotId, new LandPlot(plotId, playerId, PlotState.EMPTY));
        return Map.of("ok", true, "plotId", plotId, "ownerId", playerId, "state", "EMPTY",
                "servicePort", 8997);
    }

    public Map<String, Object> plant(long playerId, String plotId, String seedId, long growMs, long nowMs) {
        LandPlot plot = plots.get(plotId);
        if (plot == null || plot.ownerId() != playerId) {
            return Map.of("ok", false, "error", "no_plot");
        }
        if (plot.state() != PlotState.EMPTY) {
            return Map.of("ok", false, "error", "plot_busy");
        }
        String cropId = "crop-" + UUID.randomUUID();
        long grow = growMs <= 0 ? 3_600_000L : growMs;
        CropInstance crop = new CropInstance(cropId, plotId, seedId, nowMs, grow, 0, "COMMON");
        crops.put(cropId, crop);
        plots.put(plotId, new LandPlot(plotId, playerId, PlotState.PLANTED));
        return Map.of("ok", true, "cropId", cropId, "plotId", plotId, "plantTimeMs", nowMs, "growMs", grow);
    }

    /** 好友浇水：提升收获品质权重。 */
    public Map<String, Object> water(String cropId, long friendId) {
        CropInstance c = crops.get(cropId);
        if (c == null) {
            return Map.of("ok", false, "error", "crop_not_found");
        }
        CropInstance next = new CropInstance(
                c.cropId(), c.plotId(), c.seedId(), c.plantTimeMs(), c.growMs(),
                c.waterCount() + 1, c.quality());
        crops.put(cropId, next);
        return Map.of("ok", true, "cropId", cropId, "waterCount", next.waterCount(), "byFriend", friendId);
    }

    /**
     * 惰性收获：now - plantTime 达 growMs；品质受浇水次数加成，产出 GrantPlan（可随机金色）。
     */
    public Map<String, Object> harvest(long playerId, String cropId, long nowMs) {
        CropInstance c = crops.get(cropId);
        if (c == null) {
            return Map.of("ok", false, "error", "crop_not_found");
        }
        LandPlot plot = plots.get(c.plotId());
        if (plot == null || plot.ownerId() != playerId) {
            return Map.of("ok", false, "error", "not_owner");
        }
        long elapsed = nowMs - c.plantTimeMs();
        if (elapsed < c.growMs()) {
            return Map.of("ok", false, "error", "not_ready",
                    "remainMs", c.growMs() - elapsed, "elapsedMs", elapsed);
        }
        String quality = rollQuality(c.waterCount());
        String itemId = "crop_" + c.seedId() + "_" + quality.toLowerCase();
        Map<String, Integer> bag = harvestBag.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>());
        bag.merge(itemId, 1, Integer::sum);
        crops.remove(cropId);
        plots.put(c.plotId(), new LandPlot(c.plotId(), playerId, PlotState.EMPTY));
        List<Map<String, Object>> grantPlans = new ArrayList<>();
        grantPlans.add(Map.of(
                "itemId", itemId,
                "count", 1,
                "quality", quality,
                "randomQuality", true,
                "source", "homeland_harvest"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("cropId", cropId);
        body.put("quality", quality);
        body.put("elapsedMs", elapsed);
        body.put("waterCount", c.waterCount());
        body.put("grantPlans", grantPlans);
        body.put("idempotencyKey", "homeland:" + playerId + ":" + cropId);
        return body;
    }

    /**
     * 烹饪必须消耗家园产出；支持随机金色料理。
     */
    public Map<String, Object> cookWithCrop(
            long playerId, String recipeId, String cropItemId, long nowMs) {
        Map<String, Integer> bag = harvestBag.get(playerId);
        if (bag == null || bag.getOrDefault(cropItemId, 0) < 1) {
            return Map.of("ok", false, "error", "need_homeland_crop", "need", cropItemId);
        }
        bag.merge(cropItemId, -1, Integer::sum);
        String quality = cropItemId.contains("_legendary") || ThreadLocalRandom.current().nextInt(100) < 8
                ? "GOLD" : (cropItemId.contains("_rare") ? "RARE" : "COMMON");
        String foodId = "food_" + recipeId + "_" + quality.toLowerCase();
        List<Map<String, Object>> grantPlans = List.of(Map.of(
                "itemId", foodId,
                "count", 1,
                "quality", quality,
                "buff", quality.equals("GOLD") ? "crit_up" : "atk_up",
                "source", "homeland_cook"));
        return Map.of(
                "ok", true,
                "recipeId", recipeId,
                "grantPlans", grantPlans,
                "idempotencyKey", "homeland-cook:" + playerId + ":" + recipeId + ":" + nowMs,
                "consumed", cropItemId);
    }

    public Map<String, Object> inventory(long playerId) {
        return Map.of("ok", true, "playerId", playerId,
                "crops", Map.copyOf(harvestBag.getOrDefault(playerId, Map.of())));
    }

    private static String rollQuality(int waterCount) {
        int roll = ThreadLocalRandom.current().nextInt(100) + Math.min(30, waterCount * 8);
        if (roll >= 95) {
            return "LEGENDARY";
        }
        if (roll >= 75) {
            return "RARE";
        }
        return "COMMON";
    }
}
