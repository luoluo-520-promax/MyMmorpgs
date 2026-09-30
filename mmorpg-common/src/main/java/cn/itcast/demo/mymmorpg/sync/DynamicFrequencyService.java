package cn.itcast.demo.mymmorpg.sync;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 动态同步频率（LOD）：距离越远、相对速度越低，SyncInterval 越大。
 * 公式：interval = base + distance * distanceFactor - relativeSpeed * speedFactor
 */
@Component
public class DynamicFrequencyService {

    public static final long INTERVAL_NEAR_MS = 33L;
    public static final long INTERVAL_MID_MS = 100L;
    public static final long INTERVAL_FAR_MS = 200L;
    public static final float NEAR_METERS = 10f;
    public static final float FAR_METERS = 100f;

    private volatile long baseIntervalMs = 33L;
    private volatile float distanceFactor = 0.1f;
    private volatile float speedFactor = 0.05f;
    private final ConcurrentHashMap<Long, Long> lastSyncMs = new ConcurrentHashMap<>();
    private final AtomicLong syncAllowed = new AtomicLong();
    private final AtomicLong syncSkipped = new AtomicLong();

    public void configure(long baseIntervalMs, float distanceFactor, float speedFactor) {
        this.baseIntervalMs = Math.max(16L, baseIntervalMs);
        this.distanceFactor = Math.max(0.01f, distanceFactor);
        this.speedFactor = Math.max(0.01f, speedFactor);
    }

    /**
     * 计算同步间隔（ms）。
     */
    public long computeSyncIntervalMs(float distanceMeters, float relativeSpeed) {
        long interval = (long) (baseIntervalMs + distanceMeters * distanceFactor
                - relativeSpeed * speedFactor * 1000f);
        return Math.max(INTERVAL_NEAR_MS, Math.min(INTERVAL_FAR_MS, interval));
    }

    /**
     * 是否到达本次同步时刻。
     */
    public boolean shouldSync(long entityId, float distanceMeters, float relativeSpeed, long nowMs) {
        long interval = computeSyncIntervalMs(distanceMeters, relativeSpeed);
        long last = lastSyncMs.getOrDefault(entityId, 0L);
        if (nowMs - last >= interval) {
            lastSyncMs.put(entityId, nowMs);
            syncAllowed.incrementAndGet();
            return true;
        }
        syncSkipped.incrementAndGet();
        return false;
    }

    public long tierIntervalMs(float distanceMeters) {
        if (distanceMeters <= NEAR_METERS) {
            return INTERVAL_NEAR_MS;
        }
        if (distanceMeters <= FAR_METERS) {
            return INTERVAL_MID_MS;
        }
        return INTERVAL_FAR_MS;
    }

    public void evict(long entityId) {
        lastSyncMs.remove(entityId);
    }

    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tracked", lastSyncMs.size());
        m.put("syncAllowed", syncAllowed.get());
        m.put("syncSkipped", syncSkipped.get());
        m.put("baseIntervalMs", baseIntervalMs);
        m.put("distanceFactor", distanceFactor);
        return m;
    }
}
