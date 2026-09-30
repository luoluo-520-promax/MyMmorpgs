package cn.itcast.demo.mymmorpg.world.scene;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 游戏服编排适配层（Agones / K8s HPA 风格）。
 * <p>
 * 将 {@link SceneInstancePool} 的调度权从「本进程内存」提升为「节点目录 + 容量/密度选点」。
 * 生产可替换为真实 Agones Allocator / K8s API；本地与单测走内存节点注册表。
 */
@Component
public class K8sSceneAllocator {

    public record SceneNode(
            String nodeId,
            String host,
            int port,
            int cpuMillis,
            int memoryMb,
            int playerCount,
            int maxPlayers,
            boolean ready,
            long lastHeartbeatMs) {
    }

    private final SceneInstancePool pool;
    private final ConcurrentHashMap<String, SceneNode> nodes = new ConcurrentHashMap<>();
    private final AtomicInteger allocateHits = new AtomicInteger();
    private final AtomicInteger scaleOutHints = new AtomicInteger();
    private volatile double scaleOutCpuRatio = 0.75;
    private volatile double scaleOutPlayerRatio = 0.80;

    public K8sSceneAllocator() {
        this(new SceneInstancePool());
    }

    public K8sSceneAllocator(SceneInstancePool pool) {
        this.pool = pool == null ? new SceneInstancePool() : pool;
    }

    public void configureScaleThresholds(double cpuRatio, double playerRatio) {
        this.scaleOutCpuRatio = Math.max(0.1, Math.min(0.99, cpuRatio));
        this.scaleOutPlayerRatio = Math.max(0.1, Math.min(0.99, playerRatio));
    }

    public void registerOrHeartbeat(SceneNode node, long nowMs) {
        if (node == null || node.nodeId() == null || node.nodeId().isBlank()) {
            return;
        }
        nodes.put(node.nodeId(), new SceneNode(
                node.nodeId(),
                node.host() == null ? "127.0.0.1" : node.host(),
                node.port() <= 0 ? 8082 : node.port(),
                Math.max(0, node.cpuMillis()),
                Math.max(0, node.memoryMb()),
                Math.max(0, node.playerCount()),
                Math.max(1, node.maxPlayers()),
                node.ready(),
                nowMs));
    }

    public void deregister(String nodeId) {
        if (nodeId != null) {
            nodes.remove(nodeId);
        }
    }

    /**
     * 按 CPU/玩家密度选最闲就绪节点，再走实例池分配。
     */
    public Map<String, Object> allocateOnBestNode(int sceneTemplateId, int capacity, long ttlMs, long nowMs) {
        SceneNode best = pickBestNode(nowMs);
        if (best == null) {
            scaleOutHints.incrementAndGet();
            return Map.of("ok", false, "error", "no_ready_node", "hint", "scale_out",
                    "scaleOutHints", scaleOutHints.get());
        }
        if (shouldHintScaleOut(best)) {
            scaleOutHints.incrementAndGet();
        }
        SceneInstancePool.SceneInstance inst = pool.allocate(
                sceneTemplateId, capacity, ttlMs, nowMs, best.nodeId());
        allocateHits.incrementAndGet();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("instanceId", inst.instanceId());
        m.put("leaseId", inst.leaseId());
        m.put("nodeId", best.nodeId());
        m.put("host", best.host());
        m.put("port", best.port());
        m.put("sceneTemplateId", sceneTemplateId);
        m.put("capacity", inst.capacity());
        m.put("expireAtMs", inst.expireAtMs());
        m.put("scaleOutHints", scaleOutHints.get());
        return m;
    }

    public SceneNode pickBestNode(long nowMs) {
        List<SceneNode> ready = new ArrayList<>();
        for (SceneNode n : nodes.values()) {
            if (n.ready() && (nowMs - n.lastHeartbeatMs()) < 60_000L) {
                ready.add(n);
            }
        }
        if (ready.isEmpty()) {
            return null;
        }
        ready.sort(Comparator
                .comparingDouble((SceneNode n) -> loadScore(n))
                .thenComparing(SceneNode::nodeId));
        return ready.get(0);
    }

    public boolean shouldHintScaleOut(SceneNode node) {
        if (node == null) {
            return true;
        }
        double playerRatio = node.playerCount() / (double) Math.max(1, node.maxPlayers());
        // cpuMillis 约定 1000=满核；无指标时仅看玩家密度
        double cpuRatio = node.cpuMillis() <= 0 ? 0.0 : node.cpuMillis() / 1000.0;
        return playerRatio >= scaleOutPlayerRatio || cpuRatio >= scaleOutCpuRatio;
    }

    private static double loadScore(SceneNode n) {
        double playerRatio = n.playerCount() / (double) Math.max(1, n.maxPlayers());
        double cpuRatio = n.cpuMillis() <= 0 ? 0.0 : n.cpuMillis() / 1000.0;
        return playerRatio * 0.7 + cpuRatio * 0.3;
    }

    public SceneInstancePool pool() {
        return pool;
    }

    public List<SceneNode> listNodes() {
        return new ArrayList<>(nodes.values());
    }

    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nodeCount", nodes.size());
        m.put("allocateHits", allocateHits.get());
        m.put("scaleOutHints", scaleOutHints.get());
        m.put("pool", pool.stats());
        m.put("scaleOutCpuRatio", scaleOutCpuRatio);
        m.put("scaleOutPlayerRatio", scaleOutPlayerRatio);
        return m;
    }
}
