package cn.itcast.demo.mymmorpg.sync;

import cn.itcast.demo.mymmorpg.aoi.AoiBroadcastStrategy;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 广播风暴「重要性熔断」：单 Scene 节点出带宽超阈值时，非战斗玩家同步降至 2Hz（仅 Position），
 * 战斗中的玩家与 Boss 保持高频同步。
 */
@Component
public class BroadcastImportanceFuseService {

    public static final long DEFAULT_BANDWIDTH_THRESHOLD_BYTES = 512_000L;
    public static final long FUSED_INTERVAL_MS = 500L;
    public static final long NORMAL_INTERVAL_MS = 100L;

    private volatile long bandwidthThresholdBytes = DEFAULT_BANDWIDTH_THRESHOLD_BYTES;
    private final AtomicLong bytesSentThisWindow = new AtomicLong();
    private final AtomicLong fusedBroadcasts = new AtomicLong();
    private final AtomicLong highPriorityKept = new AtomicLong();
    private final AtomicLong fuseActivations = new AtomicLong();
    private volatile long windowStartMs;
    private volatile boolean fused;

    /** entityId → 是否处于战斗态（由 Scene 层标记） */
    private final ConcurrentHashMap<Long, Boolean> combatEntities = new ConcurrentHashMap<>();
    /** entityId → 是否 Boss 高优先级 */
    private final ConcurrentHashMap<Long, Boolean> bossEntities = new ConcurrentHashMap<>();

    public void configure(long bandwidthThresholdBytes) {
        this.bandwidthThresholdBytes = Math.max(64_000L, bandwidthThresholdBytes);
    }

    public void markCombat(long entityId, boolean inCombat) {
        if (entityId > 0) {
            if (inCombat) {
                combatEntities.put(entityId, true);
            } else {
                combatEntities.remove(entityId);
            }
        }
    }

    public void markBoss(long entityId, boolean isBoss) {
        if (entityId > 0) {
            if (isBoss) {
                bossEntities.put(entityId, true);
            } else {
                bossEntities.remove(entityId);
            }
        }
    }

    public void recordOutboundBytes(int bytes, long nowMs) {
        if (windowStartMs == 0 || nowMs - windowStartMs > 1000L) {
            windowStartMs = nowMs;
            bytesSentThisWindow.set(0);
            fused = false;
        }
        long total = bytesSentThisWindow.addAndGet(Math.max(0, bytes));
        if (total > bandwidthThresholdBytes && !fused) {
            fused = true;
            fuseActivations.incrementAndGet();
        }
    }

    /**
     * @param syncRotation 是否同步朝向（熔断时非战斗实体仅发 Position）
     */
    public record SyncDecision(
            boolean allow,
            boolean positionOnly,
            long minIntervalMs,
            AoiBroadcastStrategy.FrequencyTier effectiveTier) {
    }

    public SyncDecision decide(
            long entityId,
            float distanceMeters,
            AoiBroadcastStrategy.FrequencyTier tier,
            long nowMs) {
        boolean highPriority = isHighPriority(entityId);
        if (fused && !highPriority) {
            fusedBroadcasts.incrementAndGet();
            return new SyncDecision(
                    shouldAllowFused(entityId, nowMs),
                    true,
                    FUSED_INTERVAL_MS,
                    AoiBroadcastStrategy.FrequencyTier.FAR_2HZ);
        }
        if (highPriority) {
            highPriorityKept.incrementAndGet();
        }
        return new SyncDecision(true, false, NORMAL_INTERVAL_MS, tier);
    }

    private boolean isHighPriority(long entityId) {
        return Boolean.TRUE.equals(combatEntities.get(entityId))
                || Boolean.TRUE.equals(bossEntities.get(entityId));
    }

    private final ConcurrentHashMap<Long, Long> lastFusedSyncMs = new ConcurrentHashMap<>();

    private boolean shouldAllowFused(long entityId, long nowMs) {
        long last = lastFusedSyncMs.getOrDefault(entityId, 0L);
        if (nowMs - last >= FUSED_INTERVAL_MS) {
            lastFusedSyncMs.put(entityId, nowMs);
            return true;
        }
        return false;
    }

    public boolean isFused() {
        return fused;
    }

    public void evict(long entityId) {
        combatEntities.remove(entityId);
        bossEntities.remove(entityId);
        lastFusedSyncMs.remove(entityId);
    }

    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fused", fused);
        m.put("bytesSentThisWindow", bytesSentThisWindow.get());
        m.put("bandwidthThresholdBytes", bandwidthThresholdBytes);
        m.put("fuseActivations", fuseActivations.get());
        m.put("fusedBroadcasts", fusedBroadcasts.get());
        m.put("highPriorityKept", highPriorityKept.get());
        m.put("combatTracked", combatEntities.size());
        m.put("bossTracked", bossEntities.size());
        return m;
    }
}
