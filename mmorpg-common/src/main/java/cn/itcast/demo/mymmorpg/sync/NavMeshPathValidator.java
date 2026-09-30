package cn.itcast.demo.mymmorpg.sync;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 寻路网格可达性校验：拒绝穿墙/瞬移到不可达格子。
 * <p>
 * 网格以 cellSize 离散；blocked 位图可由策划 NavMesh 烘焙结果导入。
 * 未配置阻挡时默认全通（兼容现有单测），生产地图应调用 {@link #setBlocked}。
 */
@Component
public class NavMeshPathValidator {

    public record PathCheckResult(boolean reachable, String reason, int sampledSteps) {
        public static PathCheckResult ok(int steps) {
            return new PathCheckResult(true, "", steps);
        }

        public static PathCheckResult reject(String reason, int steps) {
            return new PathCheckResult(false, reason, steps);
        }
    }

    private final ConcurrentHashMap<Integer, SceneNavMesh> meshes = new ConcurrentHashMap<>();
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong accepted = new AtomicLong();
    private volatile float defaultCellSize = 1f;
    private volatile int maxSampleSteps = 256;

    public void configure(float cellSize, int maxSampleSteps) {
        this.defaultCellSize = Math.max(0.25f, cellSize);
        this.maxSampleSteps = Math.max(8, maxSampleSteps);
    }

    public void ensureMesh(int sceneId, int widthCells, int heightCells, float cellSize) {
        meshes.computeIfAbsent(sceneId, id -> new SceneNavMesh(
                Math.max(1, widthCells),
                Math.max(1, heightCells),
                cellSize <= 0 ? defaultCellSize : cellSize));
    }

    public void setBlocked(int sceneId, int cellX, int cellZ, boolean blocked) {
        SceneNavMesh mesh = meshes.get(sceneId);
        if (mesh == null) {
            return;
        }
        mesh.setBlocked(cellX, cellZ, blocked);
    }

    /**
     * 直线采样：路径上任一点落入 blocked 则拒绝。
     */
    public PathCheckResult validate(int sceneId, float fromX, float fromZ, float toX, float toZ) {
        SceneNavMesh mesh = meshes.get(sceneId);
        if (mesh == null) {
            accepted.incrementAndGet();
            return PathCheckResult.ok(0);
        }
        float dx = toX - fromX;
        float dz = toZ - fromZ;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        if (dist < 0.001f) {
            accepted.incrementAndGet();
            return PathCheckResult.ok(0);
        }
        int steps = Math.min(maxSampleSteps, Math.max(1, (int) Math.ceil(dist / mesh.cellSize)));
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            float x = fromX + dx * t;
            float z = fromZ + dz * t;
            int cx = mesh.cellX(x);
            int cz = mesh.cellZ(z);
            if (!mesh.inBounds(cx, cz)) {
                rejected.incrementAndGet();
                return PathCheckResult.reject("out_of_navmesh", i);
            }
            if (mesh.isBlocked(cx, cz)) {
                rejected.incrementAndGet();
                return PathCheckResult.reject("path_blocked", i);
            }
        }
        accepted.incrementAndGet();
        return PathCheckResult.ok(steps);
    }

    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("meshes", meshes.size());
        m.put("accepted", accepted.get());
        m.put("rejected", rejected.get());
        m.put("defaultCellSize", defaultCellSize);
        return m;
    }

    /** 测试辅助：列出某场景阻挡格 */
    public List<int[]> listBlocked(int sceneId) {
        SceneNavMesh mesh = meshes.get(sceneId);
        if (mesh == null) {
            return List.of();
        }
        return mesh.listBlocked();
    }

    private static final class SceneNavMesh {
        final int width;
        final int height;
        final float cellSize;
        final BitSet blocked;

        SceneNavMesh(int width, int height, float cellSize) {
            this.width = width;
            this.height = height;
            this.cellSize = cellSize;
            this.blocked = new BitSet(width * height);
        }

        int cellX(float x) {
            return (int) Math.floor(x / cellSize);
        }

        int cellZ(float z) {
            return (int) Math.floor(z / cellSize);
        }

        boolean inBounds(int cx, int cz) {
            return cx >= 0 && cz >= 0 && cx < width && cz < height;
        }

        void setBlocked(int cx, int cz, boolean value) {
            if (!inBounds(cx, cz)) {
                return;
            }
            blocked.set(cz * width + cx, value);
        }

        boolean isBlocked(int cx, int cz) {
            return inBounds(cx, cz) && blocked.get(cz * width + cx);
        }

        List<int[]> listBlocked() {
            List<int[]> out = new ArrayList<>();
            for (int i = blocked.nextSetBit(0); i >= 0; i = blocked.nextSetBit(i + 1)) {
                out.add(new int[]{i % width, i / width});
            }
            return out;
        }
    }
}
