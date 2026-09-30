package cn.itcast.demo.mymmorpg.aoi;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AOI 更新合并：将窗口内（默认 100~200ms）的移动/动作收成 Tick Snapshot 再批量下发，降低包头风暴。
 */
@Component
public class AoiUpdateBatcher {

    public enum SyncTier {
        /** 近距全量，约 20Hz */
        NEAR_FULL,
        /** 远距降频，约 5Hz */
        FAR_REDUCED,
        /** 视野外仅状态/血量 */
        OUT_OF_VIEW_STATE
    }

    public record PendingUpdate(
            long entityId,
            float x, float y, float z,
            int hp,
            int syncType,
            long queuedAtMs) {
    }

    public record TickSnapshot(
            long tickMs,
            List<PendingUpdate> nearFull,
            List<PendingUpdate> farReduced,
            List<PendingUpdate> outOfViewState) {
    }

    private final ConcurrentHashMap<Long, PendingUpdate> pending = new ConcurrentHashMap<>();
    private final AtomicLong flushedTicks = new AtomicLong();
    private final AtomicLong coalescedUpdates = new AtomicLong();
    private volatile long batchWindowMs = 150L;
    private volatile float nearDistance = 25f;
    private volatile float farDistance = 80f;
    private volatile long lastFlushMs = 0L;

    public void configure(long batchWindowMs, float nearDistance, float farDistance) {
        this.batchWindowMs = Math.max(50L, Math.min(500L, batchWindowMs));
        this.nearDistance = Math.max(1f, nearDistance);
        this.farDistance = Math.max(this.nearDistance + 1f, farDistance);
    }

    /**
     * 入队：同实体窗口内后写覆盖前写（合并）。
     */
    public void enqueue(long entityId, float x, float y, float z, int hp, int syncType, long nowMs) {
        PendingUpdate prev = pending.put(entityId, new PendingUpdate(entityId, x, y, z, hp, syncType, nowMs));
        if (prev != null) {
            coalescedUpdates.incrementAndGet();
        }
    }

    public boolean shouldFlush(long nowMs) {
        return !pending.isEmpty() && (nowMs - lastFlushMs) >= batchWindowMs;
    }

    /**
     * 按观察者位置分级打包 Tick Snapshot；清空 pending。
     */
    public TickSnapshot flush(float observerX, float observerZ, long nowMs) {
        List<PendingUpdate> near = new ArrayList<>();
        List<PendingUpdate> far = new ArrayList<>();
        List<PendingUpdate> out = new ArrayList<>();
        for (PendingUpdate u : pending.values()) {
            float dx = u.x() - observerX;
            float dz = u.z() - observerZ;
            float dist = (float) Math.sqrt(dx * dx + dz * dz);
            SyncTier tier = tierOf(dist);
            switch (tier) {
                case NEAR_FULL -> near.add(u);
                case FAR_REDUCED -> far.add(u);
                case OUT_OF_VIEW_STATE -> out.add(new PendingUpdate(
                        u.entityId(), u.x(), u.y(), u.z(), u.hp(), 4 /* SYNC_ATTR */, u.queuedAtMs()));
            }
        }
        pending.clear();
        lastFlushMs = nowMs;
        flushedTicks.incrementAndGet();
        return new TickSnapshot(nowMs, near, far, out);
    }

    /**
     * 无观察者上下文时：全部按 nearFull 打包（广播侧再按距离裁剪）。
     */
    public TickSnapshot flushAll(long nowMs) {
        List<PendingUpdate> all = new ArrayList<>(pending.values());
        pending.clear();
        lastFlushMs = nowMs;
        flushedTicks.incrementAndGet();
        return new TickSnapshot(nowMs, all, List.of(), List.of());
    }

    public SyncTier tierOf(float distance) {
        if (distance <= nearDistance) {
            return SyncTier.NEAR_FULL;
        }
        if (distance <= farDistance) {
            return SyncTier.FAR_REDUCED;
        }
        return SyncTier.OUT_OF_VIEW_STATE;
    }

    /**
     * 远距实体是否应在本 tick 下发（约每 4 个 near tick 一次 ≈ 5Hz @ 20Hz base）。
     */
    public boolean allowFarAtTick(long tickSeq) {
        return tickSeq % 4 == 0;
    }

    public int pendingSize() {
        return pending.size();
    }

    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pending", pending.size());
        m.put("flushedTicks", flushedTicks.get());
        m.put("coalescedUpdates", coalescedUpdates.get());
        m.put("batchWindowMs", batchWindowMs);
        m.put("nearDistance", nearDistance);
        m.put("farDistance", farDistance);
        return m;
    }
}
