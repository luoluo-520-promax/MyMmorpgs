package cn.itcast.demo.mymmorpg.aoi;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AOI 广播优化：全量 / 兴趣集 / 脏实体差分压缩，并统计被抑制的广播次数。
 */
@Component
public class AoiBroadcastStrategy {

    public enum Mode {
        FULL, INTEREST_ONLY, DELTA_COMPRESSED
    }

    public enum FrequencyTier {
        /** 10m 内 ~20Hz 全量 */
        NEAR_20HZ,
        /** 30m 内 ~5Hz 降频 */
        MID_5HZ,
        /** 50m 内 ~2Hz 远距 */
        FAR_2HZ,
        /** 视野外仅属性 */
        OUT_STATE_ONLY
    }

    private final AtomicLong suppressedBroadcasts = new AtomicLong();
    private final AtomicLong acceptedBroadcasts = new AtomicLong();
    private volatile float maxBroadcastDistance = 80f;
    private volatile float interestPriorityFloor = 0.2f;
    private volatile float nearDistance = 10f;
    private volatile float midDistance = 30f;
    private volatile float farDistance = 50f;

    public void configure(float maxBroadcastDistance, float interestPriorityFloor) {
        this.maxBroadcastDistance = Math.max(1f, maxBroadcastDistance);
        this.interestPriorityFloor = Math.max(0f, Math.min(1f, interestPriorityFloor));
    }

    public void configureDistanceTiers(float nearDistance, float maxBroadcastDistance) {
        configureDistanceTiers(nearDistance, (nearDistance + maxBroadcastDistance) / 2f, maxBroadcastDistance);
    }

    /** 三级距离：10m / 30m / 50m 同步频次分级 */
    public void configureDistanceTiers(float nearDistance, float midDistance, float farDistance) {
        this.nearDistance = Math.max(1f, nearDistance);
        this.midDistance = Math.max(this.nearDistance + 1f, midDistance);
        this.farDistance = Math.max(this.midDistance + 1f, farDistance);
        this.maxBroadcastDistance = Math.max(this.farDistance + 1f, maxBroadcastDistance);
    }

    public FrequencyTier frequencyTier(float distance) {
        if (distance <= nearDistance) {
            return FrequencyTier.NEAR_20HZ;
        }
        if (distance <= midDistance) {
            return FrequencyTier.MID_5HZ;
        }
        if (distance <= farDistance) {
            return FrequencyTier.FAR_2HZ;
        }
        return FrequencyTier.OUT_STATE_ONLY;
    }

    /**
     * 同步频次：近 20Hz / 中 5Hz / 远 2Hz / 视野外偶发。
     */
    public boolean allowAtTick(FrequencyTier tier, long tickSeq) {
        if (tier == null || tier == FrequencyTier.NEAR_20HZ) {
            return true;
        }
        if (tier == FrequencyTier.MID_5HZ) {
            return tickSeq % 4 == 0;
        }
        if (tier == FrequencyTier.FAR_2HZ) {
            return tickSeq % 10 == 0;
        }
        return tickSeq % 20 == 0;
    }

    /**
     * 按模式筛选接收者：全量返回全部 viewer；兴趣模式取交集；差分模式仅在存在脏实体时向兴趣集广播。
     */
    public List<Long> selectRecipients(
            Collection<Long> viewerIds,
            Set<Long> interestSet,
            Set<Long> dirtyEntityIds) {
        return selectRecipients(Mode.DELTA_COMPRESSED, viewerIds, interestSet, dirtyEntityIds);
    }

    public List<Long> selectRecipients(
            Mode mode,
            Collection<Long> viewerIds,
            Set<Long> interestSet,
            Set<Long> dirtyEntityIds) {
        if (viewerIds == null || viewerIds.isEmpty()) {
            return List.of();
        }
        Mode m = mode == null ? Mode.FULL : mode;
        Set<Long> viewers = new LinkedHashSet<>(viewerIds);
        return switch (m) {
            case FULL -> new ArrayList<>(viewers);
            case INTEREST_ONLY -> intersect(viewers, interestSet);
            case DELTA_COMPRESSED -> {
                if (dirtyEntityIds == null || dirtyEntityIds.isEmpty()) {
                    yield List.of();
                }
                // 仅向兴趣集内的观察者推送脏实体差分
                yield intersect(viewers, interestSet == null || interestSet.isEmpty() ? viewers : interestSet);
            }
        };
    }

    private static List<Long> intersect(Set<Long> viewers, Set<Long> interestSet) {
        if (interestSet == null || interestSet.isEmpty()) {
            return List.of();
        }
        List<Long> out = new ArrayList<>();
        for (Long id : viewers) {
            if (interestSet.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    /**
     * 判断单次广播是否应发出；抑制时累加 suppressed 计数。
     *
     * @param entityDirty       实体是否脏
     * @param distance          与观察者距离
     * @param interestPriority  兴趣优先级 [0,1]
     */
    public boolean shouldBroadcast(Mode mode, boolean entityDirty, float distance, float interestPriority) {
        Mode m = mode == null ? Mode.FULL : mode;
        boolean allow;
        if (distance > maxBroadcastDistance) {
            allow = false;
        } else {
            allow = switch (m) {
                case FULL -> true;
                case INTEREST_ONLY -> interestPriority >= interestPriorityFloor;
                case DELTA_COMPRESSED -> entityDirty && interestPriority >= interestPriorityFloor;
            };
        }
        if (allow) {
            acceptedBroadcasts.incrementAndGet();
        } else {
            suppressedBroadcasts.incrementAndGet();
        }
        return allow;
    }

    public long suppressedBroadcasts() {
        return suppressedBroadcasts.get();
    }

    public long acceptedBroadcasts() {
        return acceptedBroadcasts.get();
    }

    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("suppressedBroadcasts", suppressedBroadcasts.get());
        m.put("acceptedBroadcasts", acceptedBroadcasts.get());
        m.put("maxBroadcastDistance", maxBroadcastDistance);
        m.put("nearDistance", nearDistance);
        m.put("midDistance", midDistance);
        m.put("farDistance", farDistance);
        m.put("interestPriorityFloor", interestPriorityFloor);
        return m;
    }

    public void resetStats() {
        suppressedBroadcasts.set(0);
        acceptedBroadcasts.set(0);
    }
}
