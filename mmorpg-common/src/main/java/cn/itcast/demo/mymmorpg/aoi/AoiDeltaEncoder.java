package cn.itcast.demo.mymmorpg.aoi;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AOI 增量快照：仅广播发生变化的字段（如只有 X 变了则只发 entityId + deltaX）。
 */
@Component
public class AoiDeltaEncoder {

    public static final int MASK_X = 1;
    public static final int MASK_Y = 2;
    public static final int MASK_Z = 4;
    public static final int MASK_HP = 8;
    public static final int MASK_SYNC_TYPE = 16;

    public record EntityState(float x, float y, float z, int hp, int syncType) {
    }

    public record DeltaUpdate(long entityId, int changedMask, float x, float y, float z, int hp, int syncType) {
    }

    private final ConcurrentHashMap<Long, EntityState> lastKnown = new ConcurrentHashMap<>();
    private final AtomicLong deltasEmitted = new AtomicLong();
    private final AtomicLong fullSnapshots = new AtomicLong();
    private volatile float epsilon = 0.01f;

    public void configure(float positionEpsilon) {
        this.epsilon = Math.max(0.001f, positionEpsilon);
    }

    /**
     * 与上次状态对比，生成增量；首次出现发全量。
     */
    public DeltaUpdate encode(long entityId, float x, float y, float z, int hp, int syncType) {
        EntityState prev = lastKnown.get(entityId);
        int mask = 0;
        if (prev == null) {
            mask = MASK_X | MASK_Y | MASK_Z | MASK_HP | MASK_SYNC_TYPE;
            fullSnapshots.incrementAndGet();
        } else {
            if (Math.abs(prev.x() - x) > epsilon) {
                mask |= MASK_X;
            }
            if (Math.abs(prev.y() - y) > epsilon) {
                mask |= MASK_Y;
            }
            if (Math.abs(prev.z() - z) > epsilon) {
                mask |= MASK_Z;
            }
            if (prev.hp() != hp) {
                mask |= MASK_HP;
            }
            if (prev.syncType() != syncType) {
                mask |= MASK_SYNC_TYPE;
            }
            if (mask != 0) {
                deltasEmitted.incrementAndGet();
            }
        }
        lastKnown.put(entityId, new EntityState(x, y, z, hp, syncType));
        return new DeltaUpdate(entityId, mask, x, y, z, hp, syncType);
    }

    public List<Map<String, Object>> packBatch(List<DeltaUpdate> updates) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (DeltaUpdate u : updates) {
            if (u.changedMask() == 0) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("entityId", u.entityId());
            row.put("mask", u.changedMask());
            if ((u.changedMask() & MASK_X) != 0) {
                row.put("x", u.x());
            }
            if ((u.changedMask() & MASK_Y) != 0) {
                row.put("y", u.y());
            }
            if ((u.changedMask() & MASK_Z) != 0) {
                row.put("z", u.z());
            }
            if ((u.changedMask() & MASK_HP) != 0) {
                row.put("hp", u.hp());
            }
            if ((u.changedMask() & MASK_SYNC_TYPE) != 0) {
                row.put("syncType", u.syncType());
            }
            out.add(row);
        }
        return out;
    }

    public void evict(long entityId) {
        lastKnown.remove(entityId);
    }

    public Map<String, Object> stats() {
        return Map.of(
                "tracked", lastKnown.size(),
                "deltasEmitted", deltasEmitted.get(),
                "fullSnapshots", fullSnapshots.get(),
                "epsilon", epsilon);
    }
}
