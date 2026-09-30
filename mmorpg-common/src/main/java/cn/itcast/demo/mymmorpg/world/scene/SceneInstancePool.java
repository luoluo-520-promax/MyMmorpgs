package cn.itcast.demo.mymmorpg.world.scene;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 跨服场景实例池（K8s Job 风格）：按模板分配、租约心跳、空闲/过期自动回收。
 */
@Component
public class SceneInstancePool {

    public enum Status {
        IDLE, ALLOCATED, ACTIVE, DRAINING, RECLAIMED
    }

    public record SceneInstance(
            String instanceId,
            int sceneTemplateId,
            String nodeId,
            int capacity,
            int playerCount,
            Status status,
            long createdAtMs,
            long expireAtMs,
            String leaseId) {
    }

    private final ConcurrentHashMap<String, SceneInstance> instances = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong(1);
    private final AtomicInteger allocatedTotal = new AtomicInteger();
    private final AtomicInteger reclaimedTotal = new AtomicInteger();
    private final String defaultNodeId;
    private volatile long defaultHeartbeatExtendMs = 60_000L;

    public SceneInstancePool() {
        this("scene-pool-local");
    }

    public SceneInstancePool(String defaultNodeId) {
        this.defaultNodeId = defaultNodeId == null || defaultNodeId.isBlank() ? "scene-pool-local" : defaultNodeId;
    }

    public void configure(long heartbeatExtendMs) {
        this.defaultHeartbeatExtendMs = Math.max(1_000L, heartbeatExtendMs);
    }

    /**
     * 分配场景实例并签发租约；初始状态 ALLOCATED，expireAtMs = now + ttlMs。
     */
    public SceneInstance allocate(int sceneTemplateId, int capacity, long ttlMs) {
        long now = System.currentTimeMillis();
        return allocate(sceneTemplateId, capacity, ttlMs, now, defaultNodeId);
    }

    public SceneInstance allocate(int sceneTemplateId, int capacity, long ttlMs, long nowMs, String nodeId) {
        String instanceId = "si-" + seq.getAndIncrement() + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String leaseId = "lease-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        long ttl = Math.max(1_000L, ttlMs);
        SceneInstance inst = new SceneInstance(
                instanceId,
                sceneTemplateId,
                nodeId == null || nodeId.isBlank() ? defaultNodeId : nodeId,
                Math.max(1, capacity),
                0,
                Status.ALLOCATED,
                nowMs,
                nowMs + ttl,
                leaseId);
        instances.put(instanceId, inst);
        allocatedTotal.incrementAndGet();
        return inst;
    }

    /**
     * 心跳续租：ALLOCATED → ACTIVE，并延长 expireAtMs。
     */
    public SceneInstance heartbeat(String instanceId) {
        return heartbeat(instanceId, System.currentTimeMillis(), defaultHeartbeatExtendMs);
    }

    public SceneInstance heartbeat(String instanceId, long nowMs, long extendMs) {
        SceneInstance cur = instances.get(instanceId);
        if (cur == null || cur.status() == Status.RECLAIMED || cur.status() == Status.DRAINING) {
            return null;
        }
        Status next = cur.status() == Status.ALLOCATED || cur.status() == Status.IDLE
                ? Status.ACTIVE
                : cur.status();
        long extend = Math.max(1_000L, extendMs);
        SceneInstance updated = new SceneInstance(
                cur.instanceId(), cur.sceneTemplateId(), cur.nodeId(), cur.capacity(),
                cur.playerCount(), next, cur.createdAtMs(), nowMs + extend, cur.leaseId());
        instances.put(instanceId, updated);
        return updated;
    }

    /**
     * 主动释放：进入 DRAINING，等待 reclaimExpired 回收。
     */
    public boolean release(String instanceId) {
        SceneInstance cur = instances.get(instanceId);
        if (cur == null || cur.status() == Status.RECLAIMED) {
            return false;
        }
        SceneInstance draining = new SceneInstance(
                cur.instanceId(), cur.sceneTemplateId(), cur.nodeId(), cur.capacity(),
                cur.playerCount(), Status.DRAINING, cur.createdAtMs(), cur.expireAtMs(), cur.leaseId());
        instances.put(instanceId, draining);
        return true;
    }

    /**
     * 回收已过期或 DRAINING/空闲实例，标记为 RECLAIMED 并从活跃目录移除。
     *
     * @return 本次回收数量
     */
    public int reclaimExpired(long nowMs) {
        int n = 0;
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, SceneInstance> e : instances.entrySet()) {
            SceneInstance cur = e.getValue();
            if (cur.status() == Status.RECLAIMED) {
                toRemove.add(e.getKey());
                continue;
            }
            boolean expired = cur.expireAtMs() <= nowMs;
            boolean idleEmpty = cur.status() == Status.IDLE && cur.playerCount() <= 0;
            boolean draining = cur.status() == Status.DRAINING;
            if (expired || idleEmpty || draining) {
                toRemove.add(e.getKey());
            }
        }
        for (String id : toRemove) {
            SceneInstance removed = instances.remove(id);
            if (removed != null && removed.status() != Status.RECLAIMED) {
                reclaimedTotal.incrementAndGet();
                n++;
            }
        }
        return n;
    }

    public List<SceneInstance> listActive() {
        List<SceneInstance> out = new ArrayList<>();
        for (SceneInstance inst : instances.values()) {
            if (inst.status() == Status.ALLOCATED || inst.status() == Status.ACTIVE) {
                out.add(inst);
            }
        }
        return out;
    }

    public SceneInstance get(String instanceId) {
        return instances.get(instanceId);
    }

    public Map<String, Object> stats() {
        int idle = 0;
        int allocated = 0;
        int active = 0;
        int draining = 0;
        for (SceneInstance inst : instances.values()) {
            switch (inst.status()) {
                case IDLE -> idle++;
                case ALLOCATED -> allocated++;
                case ACTIVE -> active++;
                case DRAINING -> draining++;
                default -> {
                }
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalTracked", instances.size());
        m.put("idle", idle);
        m.put("allocated", allocated);
        m.put("active", active);
        m.put("draining", draining);
        m.put("allocatedTotal", allocatedTotal.get());
        m.put("reclaimedTotal", reclaimedTotal.get());
        return m;
    }
}
