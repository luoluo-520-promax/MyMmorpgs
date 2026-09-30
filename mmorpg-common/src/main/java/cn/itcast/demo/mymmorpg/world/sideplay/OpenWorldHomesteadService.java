package cn.itcast.demo.mymmorpg.world.sideplay;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 大世界家园地块：选点建造、采集制作、布置装饰（与 hall 家园拜访互补）。
 */
@Service
public class OpenWorldHomesteadService {

    public record Plot(
            String plotId,
            int worldId,
            float x, float y, float z,
            float radius) {
    }

    private final ConcurrentHashMap<String, Plot> plots = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> ownerPlot = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, List<Map<String, Object>>> decorations =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Map<String, Integer>> materials =
            new ConcurrentHashMap<>();

    public void registerPlot(Plot plot) {
        plots.put(plot.plotId(), plot);
    }

    public Map<String, Object> claimPlot(long playerId, String plotId, float px, float pz) {
        Plot plot = plots.get(plotId);
        if (plot == null) {
            return Map.of("ok", false, "error", "plot_not_found");
        }
        if (ownerPlot.containsKey(playerId)) {
            return Map.of("ok", false, "error", "already_owns_plot", "plotId", ownerPlot.get(playerId));
        }
        float dx = px - plot.x();
        float dz = pz - plot.z();
        if (Math.sqrt(dx * dx + dz * dz) > plot.radius()) {
            return Map.of("ok", false, "error", "out_of_plot_range");
        }
        ownerPlot.put(playerId, plotId);
        materials.putIfAbsent(playerId, new ConcurrentHashMap<>());
        decorations.putIfAbsent(playerId, new ArrayList<>());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("plotId", plotId);
        body.put("x", plot.x());
        body.put("z", plot.z());
        return body;
    }

    public Map<String, Object> gatherMaterial(long playerId, String materialId, int amount) {
        if (!ownerPlot.containsKey(playerId)) {
            return Map.of("ok", false, "error", "no_homestead");
        }
        Map<String, Integer> bag = materials.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>());
        bag.merge(materialId, Math.max(1, amount), Integer::sum);
        return Map.of("ok", true, "materials", Map.copyOf(bag));
    }

    public Map<String, Object> craft(long playerId, String recipeId, Map<String, Integer> cost, String outputItem) {
        Map<String, Integer> bag = materials.get(playerId);
        if (bag == null) {
            return Map.of("ok", false, "error", "no_homestead");
        }
        if (cost != null) {
            for (var e : cost.entrySet()) {
                if (bag.getOrDefault(e.getKey(), 0) < e.getValue()) {
                    return Map.of("ok", false, "error", "insufficient_material", "need", e.getKey());
                }
            }
            cost.forEach((k, v) -> bag.merge(k, -v, Integer::sum));
        }
        bag.merge(outputItem == null ? recipeId : outputItem, 1, Integer::sum);
        return Map.of("ok", true, "recipeId", recipeId, "materials", Map.copyOf(bag));
    }

    public Map<String, Object> placeDecoration(long playerId, String itemId, float lx, float ly, float lz) {
        if (!ownerPlot.containsKey(playerId)) {
            return Map.of("ok", false, "error", "no_homestead");
        }
        List<Map<String, Object>> deco =
                decorations.computeIfAbsent(playerId, id -> new ArrayList<>());
        Map<String, Object> piece = Map.of("itemId", itemId, "x", lx, "y", ly, "z", lz);
        deco.add(piece);
        return Map.of("ok", true, "decorations", List.copyOf(deco), "plotId", ownerPlot.get(playerId));
    }

    public Map<String, Object> snapshot(long playerId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("plotId", ownerPlot.getOrDefault(playerId, ""));
        body.put("materials", Map.copyOf(materials.getOrDefault(playerId, Map.of())));
        body.put("decorations", List.copyOf(decorations.getOrDefault(playerId, List.of())));
        return body;
    }
}
