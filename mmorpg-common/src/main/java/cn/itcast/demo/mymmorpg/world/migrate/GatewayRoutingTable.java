package cn.itcast.demo.mymmorpg.world.migrate;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 网关路由表：按 worldId + cell 映射权威 scene 节点，支撑无缝跨区。
 */
@Component
public class GatewayRoutingTable {

    public record SceneNodeRoute(
            String nodeId,
            String host,
            int port,
            int worldId,
            int zoneId,
            int cellMinX,
            int cellMaxX,
            int cellMinZ,
            int cellMaxZ,
            long heartbeatAtMs) {
    }

    public record RouteLookup(
            boolean found,
            String nodeId,
            String host,
            int port,
            int zoneId,
            boolean sameNode) {
        public static RouteLookup miss() {
            return new RouteLookup(false, "", "", 0, 0, false);
        }
    }

    private final ConcurrentHashMap<String, SceneNodeRoute> nodes = new ConcurrentHashMap<>();

    public void upsert(SceneNodeRoute route) {
        nodes.put(route.nodeId(), route);
    }

    public void remove(String nodeId) {
        nodes.remove(nodeId);
    }

    public void heartbeat(String nodeId, long nowMs) {
        SceneNodeRoute cur = nodes.get(nodeId);
        if (cur == null) {
            return;
        }
        nodes.put(nodeId, new SceneNodeRoute(
                cur.nodeId(), cur.host(), cur.port(), cur.worldId(), cur.zoneId(),
                cur.cellMinX(), cur.cellMaxX(), cur.cellMinZ(), cur.cellMaxZ(), nowMs));
    }

    public RouteLookup lookup(int worldId, float x, float z, int gridSize, String currentNodeId) {
        int cellX = (int) Math.floor(x / Math.max(1, gridSize));
        int cellZ = (int) Math.floor(z / Math.max(1, gridSize));
        Optional<SceneNodeRoute> hit = nodes.values().stream()
                .filter(r -> r.worldId() == worldId)
                .filter(r -> cellX >= r.cellMinX() && cellX <= r.cellMaxX()
                        && cellZ >= r.cellMinZ() && cellZ <= r.cellMaxZ())
                .min(Comparator.comparingInt(SceneNodeRoute::zoneId));
        if (hit.isEmpty()) {
            return RouteLookup.miss();
        }
        SceneNodeRoute r = hit.get();
        boolean same = currentNodeId != null && currentNodeId.equals(r.nodeId());
        return new RouteLookup(true, r.nodeId(), r.host(), r.port(), r.zoneId(), same);
    }

    public List<SceneNodeRoute> list(int worldId) {
        List<SceneNodeRoute> out = new ArrayList<>();
        for (SceneNodeRoute r : nodes.values()) {
            if (r.worldId() == worldId) {
                out.add(r);
            }
        }
        out.sort(Comparator.comparingInt(SceneNodeRoute::zoneId));
        return out;
    }

    public Map<String, SceneNodeRoute> snapshot() {
        return Map.copyOf(nodes);
    }

    public void evictStale(long olderThanMs) {
        long cutoff = System.currentTimeMillis() - olderThanMs;
        nodes.entrySet().removeIf(e -> e.getValue().heartbeatAtMs() < cutoff);
    }
}
