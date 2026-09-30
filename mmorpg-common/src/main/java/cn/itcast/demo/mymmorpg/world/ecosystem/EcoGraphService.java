package cn.itcast.demo.mymmorpg.world.ecosystem;

import cn.itcast.demo.mymmorpg.world.explore.RegionImpactService;
import cn.itcast.demo.mymmorpg.world.narrative.RegionTugOfWarService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 生态图数据库：捕食矩阵 + 存量比驱动跨物种因果链（乱杀野猪 → 狼群袭城 → 拉锯贡献偏移）。
 */
@Service
public class EcoGraphService {

    public static final float PREY_RATIO_THRESHOLD = 0.35f;
    public static final int DEFAULT_STOCK = 20;

    /** predator → prey */
    public record PredationEdge(String predator, String prey, float dependence) {
        public PredationEdge {
            predator = predator == null ? "" : predator.trim();
            prey = prey == null ? "" : prey.trim();
            dependence = Math.max(0.1f, Math.min(1f, dependence));
        }
    }

    private final ConcurrentHashMap<String, PredationEdge> matrix = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> stock = new ConcurrentHashMap<>();
    private RegionImpactService regions;
    private RegionTugOfWarService tugOfWar;

    public EcoGraphService() {
        seedDefaultMatrix();
    }

    public EcoGraphService(RegionImpactService regions, RegionTugOfWarService tugOfWar) {
        this();
        this.regions = regions;
        this.tugOfWar = tugOfWar;
    }

    public void bind(RegionImpactService regions, RegionTugOfWarService tugOfWar) {
        this.regions = regions;
        this.tugOfWar = tugOfWar;
    }

    private void seedDefaultMatrix() {
        putEdge(new PredationEdge("wolf", "boar", 0.8f));
        putEdge(new PredationEdge("wolf", "deer", 0.5f));
        putEdge(new PredationEdge("eagle", "rabbit", 0.7f));
        putEdge(new PredationEdge("bear", "salmon", 0.6f));
    }

    public void putEdge(PredationEdge edge) {
        if (edge != null && !edge.predator().isBlank() && !edge.prey().isBlank()) {
            matrix.put(edge.predator() + "->" + edge.prey(), edge);
            stock.putIfAbsent(stockKey("", edge.predator()), new AtomicInteger(DEFAULT_STOCK));
            stock.putIfAbsent(stockKey("", edge.prey()), new AtomicInteger(DEFAULT_STOCK));
        }
    }

    private static String stockKey(String regionId, String species) {
        return (regionId == null ? "" : regionId.trim()) + ":" + (species == null ? "" : species.trim());
    }

    public void setStock(String regionId, String species, int count) {
        stock.put(stockKey(regionId, species), new AtomicInteger(Math.max(0, count)));
    }

    public int stockOf(String regionId, String species) {
        return stock.computeIfAbsent(stockKey(regionId, species), k -> new AtomicInteger(DEFAULT_STOCK)).get();
    }

    public Map<String, Object> recordKill(String regionId, String species, int count) {
        String rid = regionId == null ? "" : regionId.trim();
        AtomicInteger s = stock.computeIfAbsent(stockKey(rid, species), k -> new AtomicInteger(DEFAULT_STOCK));
        int next = Math.max(0, s.addAndGet(-Math.max(1, count)));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("regionId", rid);
        body.put("species", species);
        body.put("stock", next);
        body.put("preyRatioAlert", next < DEFAULT_STOCK * PREY_RATIO_THRESHOLD);
        return body;
    }

    /**
     * 刷新决策：不仅看杀怪数，还看上下级生物存量比。
     */
    public Map<String, Object> spawnWeightHint(String regionId, String species) {
        int self = stockOf(regionId, species);
        float preyRatio = 1f;
        float predatorPressure = 0f;
        for (PredationEdge e : matrix.values()) {
            if (e.prey().equals(species)) {
                int pred = stockOf(regionId, e.predator());
                predatorPressure += pred / (float) DEFAULT_STOCK * e.dependence();
            }
            if (e.predator().equals(species)) {
                int prey = stockOf(regionId, e.prey());
                preyRatio = Math.min(preyRatio, prey / (float) DEFAULT_STOCK);
            }
        }
        double weight = 1.0;
        if (preyRatio < PREY_RATIO_THRESHOLD) {
            weight *= 0.4; // 猎物稀缺 → 捕食者少刷
        }
        if (self < DEFAULT_STOCK * PREY_RATIO_THRESHOLD) {
            weight *= 1.6; // 本物种过稀 → 补刷
        }
        if (predatorPressure > 1.2f) {
            weight *= 0.7;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("species", species);
        body.put("stock", self);
        body.put("preyRatio", Math.round(preyRatio * 1000.0) / 1000.0);
        body.put("predatorPressure", Math.round(predatorPressure * 1000.0) / 1000.0);
        body.put("spawnWeight", Math.round(weight * 1000.0) / 1000.0);
        return body;
    }

    /**
     * POST /ecosystem/imbalance/ripple：物种降至阈值 → EventChain + 拉锯初始贡献偏移。
     */
    public Map<String, Object> rippleImbalance(String regionId, String depletedSpecies, long nowMs) {
        String rid = regionId == null ? "" : regionId.trim();
        int s = stockOf(rid, depletedSpecies);
        if (s >= DEFAULT_STOCK * PREY_RATIO_THRESHOLD) {
            return Map.of("ok", false, "error", "not_below_threshold", "stock", s,
                    "threshold", (int) (DEFAULT_STOCK * PREY_RATIO_THRESHOLD));
        }
        List<String> predators = new ArrayList<>();
        for (PredationEdge e : matrix.values()) {
            if (e.prey().equals(depletedSpecies)) {
                predators.add(e.predator());
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("regionId", rid);
        body.put("depletedSpecies", depletedSpecies);
        body.put("stock", s);
        body.put("hungryPredators", predators);
        body.put("event", "ECO_IMBALANCE_RIPPLE");

        if (regions != null) {
            regions.registerEventChain(rid, List.of(
                    new RegionImpactService.EventChainStep(
                            "eco-raid-1",
                            "掠食者因缺食袭击城镇 NPC",
                            "eco-raid-2",
                            Map.of("kind", "PREDATOR_RAID_TOWN", "species", depletedSpecies)),
                    new RegionImpactService.EventChainStep(
                            "eco-raid-2",
                            "日常任务变更为驱狼/护商",
                            "",
                            Map.of("kind", "DAILY_QUEST_SHIFT"))));
            Map<String, Object> chain = regions.beginEventChain(rid, nowMs);
            body.put("eventChain", chain);
            regions.forceSafety(rid, RegionImpactService.RegionSafety.HOSTILE);
            body.put("regionSafety", "HOSTILE");
        }
        if (tugOfWar != null) {
            // 乱杀导致深渊侧初始贡献上升（狼群袭城削弱王国控制）
            Map<String, Object> donate = tugOfWar.donate(0L, rid, RegionTugOfWarService.FACTION_ABYSS, 50);
            body.put("tugOfWarSeed", donate);
            body.put("note", "乱杀影响势力拉锯初始贡献");
        }
        return body;
    }

    public Map<String, Object> matrixSnapshot() {
        List<Map<String, Object>> edges = new ArrayList<>();
        for (PredationEdge e : matrix.values()) {
            edges.add(Map.of("predator", e.predator(), "prey", e.prey(), "dependence", e.dependence()));
        }
        return Map.of("ok", true, "edges", edges, "count", edges.size());
    }
}
