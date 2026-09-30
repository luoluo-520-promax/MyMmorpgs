package cn.itcast.demo.mymmorpg.aoi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 大世界 3D AOI：八叉树空间索引，按 XYZ 包围盒裁剪，避免悬崖上下误同步。
 * 可与 {@link AoiGrid} 并存；查询可叠加 {@link OcclusionCuller} 做障碍物视距裁剪。
 */
public final class AoiOctree {

    public record Vec3(float x, float y, float z) {
    }

    private final float minX;
    private final float minY;
    private final float minZ;
    private final float size;
    private final int maxDepth;
    private final int maxLeafEntities;
    private final Node root;
    private final ConcurrentHashMap<Long, Vec3> positions = new ConcurrentHashMap<>();

    public AoiOctree(float minX, float minY, float minZ, float size, int maxDepth, int maxLeafEntities) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.size = Math.max(1f, size);
        this.maxDepth = Math.max(1, maxDepth);
        this.maxLeafEntities = Math.max(1, maxLeafEntities);
        this.root = new Node(minX, minY, minZ, this.size, 0);
    }

    /** 默认覆盖 0..4096 立方，深度 6。 */
    public AoiOctree() {
        this(0f, -64f, 0f, 4096f, 6, 12);
    }

    public void insert(long entityId, float x, float y, float z) {
        remove(entityId);
        Vec3 pos = new Vec3(x, y, z);
        positions.put(entityId, pos);
        root.insert(entityId, pos);
    }

    public void remove(long entityId) {
        Vec3 old = positions.remove(entityId);
        if (old != null) {
            root.remove(entityId, old);
        }
    }

    public void move(long entityId, float x, float y, float z) {
        insert(entityId, x, y, z);
    }

    public List<Long> queryIds(float cx, float cy, float cz, float radius) {
        float r2 = radius * radius;
        List<Long> out = new ArrayList<>();
        root.query(cx, cy, cz, radius, r2, out);
        return out;
    }

    /**
     * 三维距离精筛 + 可选遮挡裁剪。
     */
    public <T> List<T> queryNearby(
            float cx, float cy, float cz, float radius,
            Function<Long, T> resolver,
            Function<T, float[]> xyzExtractor,
            Predicate<float[]> occlusionPass) {
        float r2 = radius * radius;
        List<T> out = new ArrayList<>();
        for (Long id : queryIds(cx, cy, cz, radius)) {
            T entity = resolver.apply(id);
            if (entity == null) {
                continue;
            }
            float[] xyz = xyzExtractor.apply(entity);
            float dx = xyz[0] - cx;
            float dy = xyz[1] - cy;
            float dz = xyz[2] - cz;
            if (dx * dx + dy * dy + dz * dz > r2) {
                continue;
            }
            if (occlusionPass != null && !occlusionPass.test(new float[]{cx, cy, cz, xyz[0], xyz[1], xyz[2]})) {
                continue;
            }
            out.add(entity);
        }
        return out;
    }

    public Map<Long, Vec3> snapshotPositions() {
        return Map.copyOf(positions);
    }

    public void clear() {
        positions.clear();
        root.clear();
    }

    public int entityCount() {
        return positions.size();
    }

    public long benchmarkQueryMillis(int entityCount, int queryCount, float radius) {
        clear();
        int side = Math.max(1, (int) Math.ceil(Math.cbrt(entityCount)));
        for (int i = 0; i < entityCount; i++) {
            float x = (i % side) * 8f;
            float y = ((i / side) % 3) * 12f; // 高低差分层
            float z = ((i / side) / 3) * 8f;
            insert(i + 1L, x, y, z);
        }
        long start = System.nanoTime();
        for (int q = 0; q < queryCount; q++) {
            float cx = (q % side) * 8f;
            float cy = ((q / side) % 3) * 12f;
            float cz = ((q / side) / 3 % side) * 8f;
            queryIds(cx, cy, cz, radius);
        }
        return (System.nanoTime() - start) / 1_000_000L;
    }

    private final class Node {
        private final float x;
        private final float y;
        private final float z;
        private final float size;
        private final int depth;
        private final List<Long> entities = new ArrayList<>();
        private Node[] children;

        private Node(float x, float y, float z, float size, int depth) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.size = size;
            this.depth = depth;
        }

        private void insert(long id, Vec3 pos) {
            if (!contains(pos)) {
                return;
            }
            if (children == null) {
                entities.add(id);
                if (entities.size() > maxLeafEntities && depth < maxDepth) {
                    split();
                }
                return;
            }
            childFor(pos).insert(id, pos);
        }

        private void remove(long id, Vec3 pos) {
            if (children == null) {
                entities.remove(id);
                return;
            }
            if (contains(pos)) {
                childFor(pos).remove(id, pos);
            }
        }

        private void query(float cx, float cy, float cz, float radius, float r2, List<Long> out) {
            if (!intersectsSphere(cx, cy, cz, radius)) {
                return;
            }
            if (children == null) {
                for (Long id : entities) {
                    Vec3 p = positions.get(id);
                    if (p == null) {
                        continue;
                    }
                    float dx = p.x - cx;
                    float dy = p.y - cy;
                    float dz = p.z - cz;
                    if (dx * dx + dy * dy + dz * dz <= r2) {
                        out.add(id);
                    }
                }
                return;
            }
            for (Node child : children) {
                child.query(cx, cy, cz, radius, r2, out);
            }
        }

        private void split() {
            float half = size * 0.5f;
            children = new Node[8];
            int i = 0;
            for (int ox = 0; ox <= 1; ox++) {
                for (int oy = 0; oy <= 1; oy++) {
                    for (int oz = 0; oz <= 1; oz++) {
                        children[i++] = new Node(
                                x + ox * half, y + oy * half, z + oz * half, half, depth + 1);
                    }
                }
            }
            List<Long> old = new ArrayList<>(entities);
            entities.clear();
            for (Long id : old) {
                Vec3 p = positions.get(id);
                if (p != null) {
                    childFor(p).insert(id, p);
                }
            }
        }

        private Node childFor(Vec3 pos) {
            float half = size * 0.5f;
            int ox = pos.x >= x + half ? 1 : 0;
            int oy = pos.y >= y + half ? 1 : 0;
            int oz = pos.z >= z + half ? 1 : 0;
            return children[(ox << 2) | (oy << 1) | oz];
        }

        private boolean contains(Vec3 pos) {
            return pos.x >= x && pos.x < x + size
                    && pos.y >= y && pos.y < y + size
                    && pos.z >= z && pos.z < z + size;
        }

        private boolean intersectsSphere(float cx, float cy, float cz, float radius) {
            float closestX = clamp(cx, x, x + size);
            float closestY = clamp(cy, y, y + size);
            float closestZ = clamp(cz, z, z + size);
            float dx = closestX - cx;
            float dy = closestY - cy;
            float dz = closestZ - cz;
            return dx * dx + dy * dy + dz * dz <= radius * radius;
        }

        private void clear() {
            entities.clear();
            children = null;
        }
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
