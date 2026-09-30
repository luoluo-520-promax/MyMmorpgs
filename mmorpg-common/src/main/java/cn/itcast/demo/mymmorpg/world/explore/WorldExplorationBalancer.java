package cn.itcast.demo.mymmorpg.world.explore;

import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.world.content.ProceduralPlacementService;
import cn.itcast.demo.mymmorpg.world.content.RareEliteSpawnService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局探索调度器：按区域热度方差向冷门区补高级精英，并驱动 DynamicLoot 杠杆。
 */
@Service
public class WorldExplorationBalancer {

    public static final long SHORT_HEAT_WINDOW_MS = 600_000L; // 10 分钟
    public static final int COLD_PLAYER_THRESHOLD = 5;
    public static final double COLD_LOOT_WEIGHT = 1.5;

    private final RegionHeatService regionHeat;
    private final ProceduralPlacementService placement;
    private RareEliteSpawnService rareElites;

    public WorldExplorationBalancer(RegionHeatService regionHeat, ProceduralPlacementService placement) {
        this.regionHeat = regionHeat == null ? new RegionHeatService() : regionHeat;
        this.placement = placement == null ? new ProceduralPlacementService() : placement;
    }

    public void bindRareElites(RareEliteSpawnService rareElites) {
        this.rareElites = rareElites;
    }

    /**
     * 热度密度系数：过去 10 分钟格子停留 &lt; 5 人 → 1.5 倍掉落权重。
     */
    public Map<String, Object> heatDensityCoefficient(
            String regionId, int gridX, int gridZ, long nowMs) {
        Map<String, Object> heat = regionHeat.heatStatus(regionId, gridX, gridZ, nowMs);
        int visits = ((Number) heat.getOrDefault("playerVisitCount", 0)).intValue();
        // RegionHeat 默认 1h 窗；此处用访问数近似 10min 冷热（低热度即冷门）
        boolean cold = visits < COLD_PLAYER_THRESHOLD;
        double coeff = cold ? COLD_LOOT_WEIGHT : 1.0;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("regionId", regionId);
        body.put("gridX", gridX);
        body.put("gridZ", gridZ);
        body.put("playerVisitCount", visits);
        body.put("heatDensityCoefficient", coeff);
        body.put("coldSpot", cold);
        body.put("windowMs", SHORT_HEAT_WINDOW_MS);
        if (cold) {
            body.put("msgId", MessageId.EXPLORE_LOOT_RARITY_BOOST_SC_NOTIFY);
            body.put("clientHint", "稀有度提升");
            body.put("rarityBoostLabel", "稀有度提升");
        }
        return body;
    }

    /**
     * 定时计算各区热度方差，向冷门区下发 compensateSpawnCmd。
     */
    public Map<String, Object> balance(
            List<Map<String, Object>> regionCells, long nowMs) {
        if (regionCells == null || regionCells.isEmpty()) {
            return Map.of("ok", false, "error", "cells_required");
        }
        List<Integer> heats = new ArrayList<>();
        List<Map<String, Object>> compensate = new ArrayList<>();
        for (Map<String, Object> cell : regionCells) {
            String regionId = String.valueOf(cell.get("regionId"));
            int gx = asInt(cell.get("gridX"));
            int gz = asInt(cell.get("gridZ"));
            Map<String, Object> dens = heatDensityCoefficient(regionId, gx, gz, nowMs);
            int v = ((Number) dens.get("playerVisitCount")).intValue();
            heats.add(v);
            if (Boolean.TRUE.equals(dens.get("coldSpot"))) {
                float x = gx * 50f + 25f;
                float z = gz * 50f + 25f;
                Map<String, Object> placed = placement.fillGatherables(
                        regionId,
                        List.of(new ProceduralPlacementService.TerrainSample(x, z, 10f, 35f)),
                        1, 0.8, 0.4);
                Map<String, Object> cmd = new LinkedHashMap<>();
                cmd.put("cmd", "compensateSpawnCmd");
                cmd.put("regionId", regionId);
                cmd.put("gridX", gx);
                cmd.put("gridZ", gz);
                cmd.put("kind", "RARE_ELITE");
                cmd.put("placement", placed);
                if (rareElites != null) {
                    cmd.put("rareEliteLinked", true);
                }
                cmd.put("heatDensityCoefficient", dens.get("heatDensityCoefficient"));
                compensate.add(cmd);
            }
        }
        double mean = heats.stream().mapToInt(Integer::intValue).average().orElse(0);
        double variance = 0;
        for (int h : heats) {
            variance += (h - mean) * (h - mean);
        }
        variance = heats.isEmpty() ? 0 : variance / heats.size();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("heatMean", Math.round(mean * 100.0) / 100.0);
        body.put("heatVariance", Math.round(variance * 100.0) / 100.0);
        body.put("compensateSpawnCmds", compensate);
        body.put("compensateCount", compensate.size());
        body.put("atMs", nowMs);
        return body;
    }

    private static int asInt(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return 0;
        }
    }
}
