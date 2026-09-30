package cn.itcast.demo.mymmorpg.aoi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 空间哈希 AOI 网格：按 gridSize 分 cell，查询时只扫描半径覆盖的格子。
 * 可被 scene 分线复用，也可用于千人同屏压测基准。
 */
public final class AoiGrid {

    private final int gridSize;
    private final ConcurrentHashMap<Long, Set<Long>> cellIndex = new ConcurrentHashMap<>();

    public AoiGrid(int gridSize) {
        this.gridSize = Math.max(1, gridSize);
    }

    public int gridSize() {
        return gridSize;
    }

    public int cellCount() {
        return cellIndex.size();
    }

    public static long cellKey(float x, float z, int gridSize) {
        int g = Math.max(1, gridSize);
        int gx = (int) Math.floor(x / g);
        int gz = (int) Math.floor(z / g);
        return (((long) gx) << 32) ^ (gz & 0xffffffffL);
    }

    public long cellKey(float x, float z) {
        return cellKey(x, z, gridSize);
    }

    public void insert(long entityId, float x, float z) {
        long cell = cellKey(x, z);
        cellIndex.computeIfAbsent(cell, k -> ConcurrentHashMap.newKeySet()).add(entityId);
    }

    public void remove(long entityId, long cellKey) {
        Set<Long> set = cellIndex.get(cellKey);
        if (set == null) {
            return;
        }
        set.remove(entityId);
        if (set.isEmpty()) {
            cellIndex.remove(cellKey, set);
        }
    }

    public long move(long entityId, long oldCell, float nx, float nz) {
        long newCell = cellKey(nx, nz);
        if (oldCell == newCell) {
            return oldCell;
        }
        remove(entityId, oldCell);
        insert(entityId, nx, nz);
        return newCell;
    }

    /**
     * 返回半径覆盖格子内的 entityId（未做距离精筛）。
     */
    public List<Long> queryIds(float cx, float cz, float radius) {
        int cellRadius = Math.max(1, (int) Math.ceil(radius / gridSize));
        int cgx = (int) Math.floor(cx / gridSize);
        int cgz = (int) Math.floor(cz / gridSize);
        List<Long> out = new ArrayList<>();
        for (int dx = -cellRadius; dx <= cellRadius; dx++) {
            for (int dz = -cellRadius; dz <= cellRadius; dz++) {
                long key = (((long) (cgx + dx)) << 32) ^ ((cgz + dz) & 0xffffffffL);
                Set<Long> ids = cellIndex.get(key);
                if (ids == null) {
                    continue;
                }
                out.addAll(ids);
            }
        }
        return out;
    }

    /**
     * 查询并映射为实体，同时按三维距离平方精筛。
     */
    public <T> List<T> queryNearby(
            float cx, float cy, float cz, float radius,
            Function<Long, T> resolver,
            Function<T, float[]> xyzExtractor) {
        float r2 = radius * radius;
        List<T> out = new ArrayList<>();
        for (Long id : queryIds(cx, cz, radius)) {
            T entity = resolver.apply(id);
            if (entity == null) {
                continue;
            }
            float[] xyz = xyzExtractor.apply(entity);
            float dx = xyz[0] - cx;
            float dy = xyz[1] - cy;
            float dz = xyz[2] - cz;
            if (dx * dx + dy * dy + dz * dz <= r2) {
                out.add(entity);
            }
        }
        return out;
    }

    public Map<Long, Set<Long>> snapshotCells() {
        return Map.copyOf(cellIndex);
    }

    public void clear() {
        cellIndex.clear();
    }

    /** 压测：批量插入后做 N 次查询，返回耗时毫秒。 */
    public long benchmarkQueryMillis(int entityCount, int queryCount, float radius) {
        clear();
        int side = Math.max(1, (int) Math.ceil(Math.sqrt(entityCount)));
        for (int i = 0; i < entityCount; i++) {
            float x = (i % side) * (gridSize * 0.5f);
            float z = (i / side) * (gridSize * 0.5f);
            insert(i + 1L, x, z);
        }
        long start = System.nanoTime();
        for (int q = 0; q < queryCount; q++) {
            float cx = (q % side) * (gridSize * 0.5f);
            float cz = ((q / side) % side) * (gridSize * 0.5f);
            queryIds(cx, cz, radius);
        }
        return (System.nanoTime() - start) / 1_000_000L;
    }

    public Collection<Set<Long>> cells() {
        return cellIndex.values();
    }
}
