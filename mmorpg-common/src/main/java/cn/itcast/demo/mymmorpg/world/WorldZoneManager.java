package cn.itcast.demo.mymmorpg.world;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 动态世界域网格：按玩家密度拆合 Zone 负责的 cell 范围，供无缝大世界路由使用。
 * 底座实现：单进程内存目录；多节点时可由 Center 同步 ownedCells。
 */
@Component
public class WorldZoneManager {

    public record ZoneShard(
            int worldId,
            int zoneId,
            String ownerNodeId,
            int cellMinX,
            int cellMaxX,
            int cellMinZ,
            int cellMaxZ,
            int playerCount) {
    }

    public record BorderHandoff(
            boolean required,
            int fromZoneId,
            int toZoneId,
            String targetNodeId,
            int cellX,
            int cellZ) {
        public static BorderHandoff none() {
            return new BorderHandoff(false, 0, 0, "", 0, 0);
        }
    }

    private final ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, ZoneShard>> worlds = new ConcurrentHashMap<>();
    private final AtomicInteger zoneSeq = new AtomicInteger(1);

    private volatile int splitThreshold = 80;
    private volatile int mergeThreshold = 20;
    private volatile int defaultCellSpan = 8;

    public void configure(int splitThreshold, int mergeThreshold, int defaultCellSpan) {
        this.splitThreshold = Math.max(2, splitThreshold);
        this.mergeThreshold = Math.max(1, Math.min(mergeThreshold, this.splitThreshold - 1));
        this.defaultCellSpan = Math.max(2, defaultCellSpan);
    }

    public ZoneShard ensureWorld(int worldId, String ownerNodeId) {
        ConcurrentHashMap<Integer, ZoneShard> zones = worlds.computeIfAbsent(worldId, w -> new ConcurrentHashMap<>());
        if (!zones.isEmpty()) {
            return zones.values().iterator().next();
        }
        int zoneId = zoneSeq.getAndIncrement();
        int span = defaultCellSpan;
        ZoneShard root = new ZoneShard(worldId, zoneId, ownerNodeId == null ? "local" : ownerNodeId,
                0, span - 1, 0, span - 1, 0);
        zones.put(zoneId, root);
        return root;
    }

    public ZoneShard locate(int worldId, float x, float z, int gridSize) {
        ensureWorld(worldId, "local");
        int cellX = (int) Math.floor(x / Math.max(1, gridSize));
        int cellZ = (int) Math.floor(z / Math.max(1, gridSize));
        ConcurrentHashMap<Integer, ZoneShard> zones = worlds.get(worldId);
        for (ZoneShard shard : zones.values()) {
            if (cellX >= shard.cellMinX() && cellX <= shard.cellMaxX()
                    && cellZ >= shard.cellMinZ() && cellZ <= shard.cellMaxZ()) {
                return shard;
            }
        }
        return zones.values().stream()
                .min(Comparator.comparingInt(ZoneShard::zoneId))
                .orElseThrow();
    }

    public synchronized void reportPlayerCount(int worldId, int zoneId, int playerCount) {
        ConcurrentHashMap<Integer, ZoneShard> zones = worlds.get(worldId);
        if (zones == null) {
            return;
        }
        ZoneShard cur = zones.get(zoneId);
        if (cur == null) {
            return;
        }
        ZoneShard updated = new ZoneShard(cur.worldId(), cur.zoneId(), cur.ownerNodeId(),
                cur.cellMinX(), cur.cellMaxX(), cur.cellMinZ(), cur.cellMaxZ(), Math.max(0, playerCount));
        zones.put(zoneId, updated);
        if (updated.playerCount() >= splitThreshold) {
            trySplit(worldId, updated);
        } else if (updated.playerCount() <= mergeThreshold) {
            tryMerge(worldId, updated);
        }
    }

    private void trySplit(int worldId, ZoneShard zone) {
        ConcurrentHashMap<Integer, ZoneShard> zones = worlds.get(worldId);
        if (zones == null || zones.get(zone.zoneId()) == null) {
            return;
        }
        int width = zone.cellMaxX() - zone.cellMinX() + 1;
        int height = zone.cellMaxZ() - zone.cellMinZ() + 1;
        if (width < 2 && height < 2) {
            return;
        }
        boolean splitX = width >= height;
        int mid;
        ZoneShard left;
        ZoneShard right;
        int newId = zoneSeq.getAndIncrement();
        if (splitX) {
            mid = zone.cellMinX() + width / 2 - 1;
            left = new ZoneShard(worldId, zone.zoneId(), zone.ownerNodeId(),
                    zone.cellMinX(), mid, zone.cellMinZ(), zone.cellMaxZ(), zone.playerCount() / 2);
            right = new ZoneShard(worldId, newId, zone.ownerNodeId() + "-b",
                    mid + 1, zone.cellMaxX(), zone.cellMinZ(), zone.cellMaxZ(),
                    zone.playerCount() - zone.playerCount() / 2);
        } else {
            mid = zone.cellMinZ() + height / 2 - 1;
            left = new ZoneShard(worldId, zone.zoneId(), zone.ownerNodeId(),
                    zone.cellMinX(), zone.cellMaxX(), zone.cellMinZ(), mid, zone.playerCount() / 2);
            right = new ZoneShard(worldId, newId, zone.ownerNodeId() + "-b",
                    zone.cellMinX(), zone.cellMaxX(), mid + 1, zone.cellMaxZ(),
                    zone.playerCount() - zone.playerCount() / 2);
        }
        zones.put(left.zoneId(), left);
        zones.put(right.zoneId(), right);
    }

    private void tryMerge(int worldId, ZoneShard zone) {
        ConcurrentHashMap<Integer, ZoneShard> zones = worlds.get(worldId);
        if (zones == null || zones.size() <= 1) {
            return;
        }
        ZoneShard neighbor = null;
        for (ZoneShard other : zones.values()) {
            if (other.zoneId() == zone.zoneId()) {
                continue;
            }
            if (adjacent(zone, other) && other.playerCount() <= mergeThreshold) {
                neighbor = other;
                break;
            }
        }
        if (neighbor == null) {
            return;
        }
        ZoneShard merged = new ZoneShard(worldId, Math.min(zone.zoneId(), neighbor.zoneId()), zone.ownerNodeId(),
                Math.min(zone.cellMinX(), neighbor.cellMinX()),
                Math.max(zone.cellMaxX(), neighbor.cellMaxX()),
                Math.min(zone.cellMinZ(), neighbor.cellMinZ()),
                Math.max(zone.cellMaxZ(), neighbor.cellMaxZ()),
                zone.playerCount() + neighbor.playerCount());
        zones.remove(zone.zoneId());
        zones.remove(neighbor.zoneId());
        zones.put(merged.zoneId(), merged);
    }

    private static boolean adjacent(ZoneShard a, ZoneShard b) {
        boolean xTouch = a.cellMaxX() + 1 == b.cellMinX() || b.cellMaxX() + 1 == a.cellMinX();
        boolean zOverlap = a.cellMinZ() <= b.cellMaxZ() && b.cellMinZ() <= a.cellMaxZ();
        boolean zTouch = a.cellMaxZ() + 1 == b.cellMinZ() || b.cellMaxZ() + 1 == a.cellMinZ();
        boolean xOverlap = a.cellMinX() <= b.cellMaxX() && b.cellMinX() <= a.cellMaxX();
        return (xTouch && zOverlap) || (zTouch && xOverlap);
    }

    /**
     * 检测玩家从 from 坐标移动到 to 是否跨越 Zone 边界，需要无缝交接。
     */
    public BorderHandoff detectHandoff(int worldId, float fromX, float fromZ, float toX, float toZ, int gridSize) {
        ZoneShard from = locate(worldId, fromX, fromZ, gridSize);
        ZoneShard to = locate(worldId, toX, toZ, gridSize);
        if (from.zoneId() == to.zoneId()) {
            return BorderHandoff.none();
        }
        int cellX = (int) Math.floor(toX / Math.max(1, gridSize));
        int cellZ = (int) Math.floor(toZ / Math.max(1, gridSize));
        return new BorderHandoff(true, from.zoneId(), to.zoneId(), to.ownerNodeId(), cellX, cellZ);
    }

    public List<ZoneShard> listZones(int worldId) {
        ConcurrentHashMap<Integer, ZoneShard> zones = worlds.get(worldId);
        if (zones == null) {
            return List.of();
        }
        return new ArrayList<>(zones.values());
    }

    public Map<String, Object> stats(int worldId) {
        List<ZoneShard> list = listZones(worldId);
        int players = list.stream().mapToInt(ZoneShard::playerCount).sum();
        return Map.of(
                "worldId", worldId,
                "zoneCount", list.size(),
                "playerCount", players,
                "splitThreshold", splitThreshold,
                "mergeThreshold", mergeThreshold);
    }
}
