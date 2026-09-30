package cn.itcast.demo.mymmorpg.world.content;

import cn.itcast.demo.mymmorpg.world.ecosystem.EcoCarryingCapacity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 程序化洒点：按地形坡度、道路距离自动生成低价值采集物，人工只放高价值谜题/宝箱。
 * 接入 {@link EcoCarryingCapacity}：过杀某类生物会压低刷新目标数。
 */
@Service
public class ProceduralPlacementService {

    public record TerrainSample(float x, float z, float slope, float roadDistance) {
    }

    public record Placement(
            String pointId,
            String kind,
            float x, float y, float z,
            String itemId,
            boolean procedural) {
    }

    private final ConcurrentHashMap<String, List<Placement>> byRegion = new ConcurrentHashMap<>();
    private final EcoCarryingCapacity carrying = new EcoCarryingCapacity();

    public EcoCarryingCapacity carrying() {
        return carrying;
    }

    public Map<String, Object> fillGatherables(
            String regionId,
            List<TerrainSample> samples,
            int targetCount,
            double minDensityPerSqKm,
            double areaSqKm) {
        return fillGatherables(regionId, samples, targetCount, minDensityPerSqKm, areaSqKm, "wildlife");
    }

    public Map<String, Object> fillGatherables(
            String regionId,
            List<TerrainSample> samples,
            int targetCount,
            double minDensityPerSqKm,
            double areaSqKm,
            String speciesForCapacity) {
        Map<String, Object> cap = carrying.applyToPlacementTarget(regionId, speciesForCapacity, targetCount);
        int need = Math.max(1, ((Number) cap.get("adjustedTarget")).intValue());
        List<Placement> placed = new ArrayList<>();
        List<TerrainSample> pool = samples == null ? List.of() : samples;
        int i = 0;
        while (placed.size() < need) {
            TerrainSample s;
            if (!pool.isEmpty()) {
                s = pool.get(i % pool.size());
                i++;
            } else {
                s = new TerrainSample(
                        ThreadLocalRandom.current().nextFloat(0f, 1000f),
                        ThreadLocalRandom.current().nextFloat(0f, 1000f),
                        ThreadLocalRandom.current().nextFloat(0f, 30f),
                        ThreadLocalRandom.current().nextFloat(5f, 80f));
            }
            if (s.slope() > 35f || s.roadDistance() > 120f) {
                if (!pool.isEmpty() && i > pool.size() * 3) {
                    break;
                }
                if (!pool.isEmpty()) {
                    continue;
                }
            }
            String item = s.slope() < 10f ? "mint" : "sweet_flower";
            String id = regionId + "-gather-" + placed.size();
            placed.add(new Placement(id, "GATHER", s.x(), 0f, s.z(), item, true));
        }
        byRegion.put(regionId, placed);
        double density = areaSqKm <= 0 ? placed.size() : placed.size() / areaSqKm;
        boolean densityOk = density >= minDensityPerSqKm;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("regionId", regionId);
        body.put("placedCount", placed.size());
        body.put("densityPerSqKm", Math.round(density * 100.0) / 100.0);
        body.put("minDensityPerSqKm", minDensityPerSqKm);
        body.put("densityOk", densityOk);
        body.put("ecoCapacity", cap);
        body.put("placements", placed.stream().map(this::toView).toList());
        if (!densityOk) {
            body.put("warning", "density_below_threshold");
        }
        return body;
    }

    public Map<String, Object> validateDensity(
            String regionId, int interactivePoints, double areaSqKm, double minPerSqKm) {
        double density = areaSqKm <= 0 ? interactivePoints : interactivePoints / areaSqKm;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", density >= minPerSqKm);
        body.put("regionId", regionId);
        body.put("interactivePoints", interactivePoints);
        body.put("areaSqKm", areaSqKm);
        body.put("density", Math.round(density * 100.0) / 100.0);
        body.put("minPerSqKm", minPerSqKm);
        if (density < minPerSqKm) {
            body.put("error", "density_too_low");
            body.put("needAtLeast", (int) Math.ceil(minPerSqKm * areaSqKm));
        }
        return body;
    }

    public List<Map<String, Object>> list(String regionId) {
        return byRegion.getOrDefault(regionId, List.of()).stream().map(this::toView).toList();
    }

    private Map<String, Object> toView(Placement p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pointId", p.pointId());
        m.put("kind", p.kind());
        m.put("x", p.x());
        m.put("y", p.y());
        m.put("z", p.z());
        m.put("itemId", p.itemId());
        m.put("procedural", p.procedural());
        return m;
    }
}
