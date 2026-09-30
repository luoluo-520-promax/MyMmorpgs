package cn.itcast.demo.mymmorpg.persist;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 字段级脏标记：大世界高频移动只标记 POSITION，刷盘时只回写变化字段。
 */
public final class DirtyFieldTracker {

    public enum Field {
        POSITION,
        EXP,
        LEVEL,
        CURRENCY,
        QUEST,
        INVENTORY,
        FULL
    }

    private final ConcurrentHashMap<Long, EnumSet<Field>> dirty = new ConcurrentHashMap<>();

    public void mark(long entityId, Field field) {
        if (entityId <= 0 || field == null) {
            return;
        }
        dirty.compute(entityId, (id, set) -> {
            EnumSet<Field> next = set == null ? EnumSet.noneOf(Field.class) : EnumSet.copyOf(set);
            if (field == Field.FULL) {
                next.add(Field.FULL);
            } else {
                next.add(field);
            }
            return next;
        });
    }

    public void markAll(long entityId, Field... fields) {
        if (fields == null) {
            return;
        }
        for (Field f : fields) {
            mark(entityId, f);
        }
    }

    public boolean isDirty(long entityId) {
        Set<Field> set = dirty.get(entityId);
        return set != null && !set.isEmpty();
    }

    public Set<Field> snapshot(long entityId) {
        Set<Field> set = dirty.get(entityId);
        return set == null ? Set.of() : Set.copyOf(set);
    }

    public Set<Field> drain(long entityId) {
        EnumSet<Field> set = dirty.remove(entityId);
        return set == null ? Set.of() : Collections.unmodifiableSet(set);
    }

    public void clear(long entityId) {
        dirty.remove(entityId);
    }

    public Map<Long, Set<Field>> snapshotAll() {
        Map<Long, Set<Field>> out = new ConcurrentHashMap<>();
        dirty.forEach((k, v) -> out.put(k, Set.copyOf(v)));
        return out;
    }

    public int dirtyCount() {
        return dirty.size();
    }
}
