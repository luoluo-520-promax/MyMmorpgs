package cn.itcast.demo.mymmorpg.gateway;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 网关侧场景路由表（轻量，不依赖 mmorpg-common）：按 world cell 映射权威 scene 节点。
 */
@Component
public class GatewayZoneRouteTable {

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

    public GatewayZoneRouteTable() {
        long now = System.currentTimeMillis();
        upsert(new SceneNodeRoute("scene-local", "127.0.0.1", 8082, 1, 1, 0, 7, 0, 7, now));
        upsert(new SceneNodeRoute("scene-liyue", "127.0.0.1", 8083, 1, 2, 8, 15, 0, 7, now));
    }

    public void upsert(SceneNodeRoute route) {
        nodes.put(route.nodeId(), route);
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
        return out;
    }
}
