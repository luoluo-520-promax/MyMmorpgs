package cn.itcast.demo.mymmorpg.world;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于密度快照驱动 {@link WorldZoneManager} 拆合域，并给出 zone→node 分配建议。
 */
@Component
public class ZoneLoadBalancer {

    private final WorldZoneManager zoneManager;
    private final AtomicLong splits = new AtomicLong();
    private final AtomicLong merges = new AtomicLong();
    private final AtomicLong rebalanceRuns = new AtomicLong();

    public ZoneLoadBalancer() {
        this(new WorldZoneManager());
    }

    public ZoneLoadBalancer(WorldZoneManager zoneManager) {
        this.zoneManager = zoneManager == null ? new WorldZoneManager() : zoneManager;
    }

    public WorldZoneManager zoneManager() {
        return zoneManager;
    }

    /**
     * 将密度快照写入各 Zone，触发拆合；并累计 splits/merges/rebalanceRuns 指标。
     */
    public Map<String, Object> rebalance(int worldId, Map<Integer, Integer> densitySnapshots) {
        zoneManager.ensureWorld(worldId, "local");
        int before = zoneManager.listZones(worldId).size();
        if (densitySnapshots != null) {
            for (Map.Entry<Integer, Integer> e : densitySnapshots.entrySet()) {
                zoneManager.reportPlayerCount(worldId, e.getKey(), e.getValue() == null ? 0 : e.getValue());
            }
        }
        int after = zoneManager.listZones(worldId).size();
        if (after > before) {
            splits.addAndGet(after - before);
        } else if (after < before) {
            merges.addAndGet(before - after);
        }
        rebalanceRuns.incrementAndGet();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("worldId", worldId);
        m.put("zoneCountBefore", before);
        m.put("zoneCountAfter", after);
        m.put("splits", splits.get());
        m.put("merges", merges.get());
        m.put("rebalanceRuns", rebalanceRuns.get());
        return m;
    }

    /**
     * 按玩家密度对现有节点做最少负载分配，返回 zoneId → nodeId。
     */
    public Map<Integer, String> suggestNodeAssignment(int worldId) {
        List<WorldZoneManager.ZoneShard> zones = zoneManager.listZones(worldId);
        Map<Integer, String> assignment = new LinkedHashMap<>();
        if (zones.isEmpty()) {
            return assignment;
        }
        List<String> nodes = new ArrayList<>();
        for (WorldZoneManager.ZoneShard z : zones) {
            String n = z.ownerNodeId();
            if (n != null && !n.isBlank() && !nodes.contains(n)) {
                nodes.add(n);
            }
        }
        if (nodes.isEmpty()) {
            nodes.add("local");
        }
        // 剥离 -b 后缀衍生节点，保留基线节点池做重分配
        List<String> baseNodes = new ArrayList<>();
        for (String n : nodes) {
            String base = n.contains("-b") ? n.substring(0, n.indexOf("-b")) : n;
            if (!baseNodes.contains(base)) {
                baseNodes.add(base);
            }
        }
        if (baseNodes.isEmpty()) {
            baseNodes.add("local");
        }

        Map<String, Integer> load = new LinkedHashMap<>();
        for (String n : baseNodes) {
            load.put(n, 0);
        }
        List<WorldZoneManager.ZoneShard> ordered = new ArrayList<>(zones);
        ordered.sort(Comparator.comparingInt(WorldZoneManager.ZoneShard::playerCount).reversed());
        for (WorldZoneManager.ZoneShard z : ordered) {
            String best = baseNodes.get(0);
            int bestLoad = load.getOrDefault(best, 0);
            for (String n : baseNodes) {
                int cur = load.getOrDefault(n, 0);
                if (cur < bestLoad) {
                    best = n;
                    bestLoad = cur;
                }
            }
            assignment.put(z.zoneId(), best);
            load.put(best, bestLoad + Math.max(0, z.playerCount()));
        }
        return assignment;
    }

    public Map<String, Object> metrics() {
        return Map.of(
                "splits", splits.get(),
                "merges", merges.get(),
                "rebalanceRuns", rebalanceRuns.get());
    }
}
