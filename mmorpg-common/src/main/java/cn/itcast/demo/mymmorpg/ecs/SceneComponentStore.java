package cn.itcast.demo.mymmorpg.ecs;

import com.carrotsearch.hppc.LongIntHashMap;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ECS 风格组件平铺存储：坐标/速度/血量按 slot 索引到连续 float[] / int[]，
 * entityId → slot 映射用 HPPC LongIntHashMap，避免 Long 装箱与嵌套 OOP 引用链。
 * 每个分线实例独立持有一份（非 Spring 单例）。
 */
public class SceneComponentStore {

    private static final int INIT_CAP = 512;

    private float[] xCoords = new float[INIT_CAP];
    private float[] yCoords = new float[INIT_CAP];
    private float[] zCoords = new float[INIT_CAP];
    private float[] vx = new float[INIT_CAP];
    private float[] vy = new float[INIT_CAP];
    private float[] vz = new float[INIT_CAP];
    private int[] hp = new int[INIT_CAP];
    private long[] entityIds = new long[INIT_CAP];
    private boolean[] dirty = new boolean[INIT_CAP];

    private final LongIntHashMap idToSlot = new LongIntHashMap(INIT_CAP);
    private final AtomicInteger count = new AtomicInteger();
    private final AtomicLong dirtyTicks = new AtomicLong();
    private final AtomicLong upserts = new AtomicLong();

    public int allocate(long entityId, float x, float y, float z, int hpVal) {
        int slot = idToSlot.getOrDefault(entityId, -1);
        if (slot < 0) {
            slot = count.getAndIncrement();
            ensureCapacity(slot + 1);
            entityIds[slot] = entityId;
            idToSlot.put(entityId, slot);
        }
        xCoords[slot] = x;
        yCoords[slot] = y;
        zCoords[slot] = z;
        hp[slot] = hpVal;
        markDirty(slot);
        upserts.incrementAndGet();
        return slot;
    }

    public void setVelocity(long entityId, float vxVal, float vyVal, float vzVal) {
        int slot = idToSlot.getOrDefault(entityId, -1);
        if (slot < 0) {
            return;
        }
        vx[slot] = vxVal;
        vy[slot] = vyVal;
        vz[slot] = vzVal;
        markDirty(slot);
    }

    public void setPosition(long entityId, float x, float y, float z) {
        int slot = idToSlot.getOrDefault(entityId, -1);
        if (slot < 0) {
            return;
        }
        xCoords[slot] = x;
        yCoords[slot] = y;
        zCoords[slot] = z;
        markDirty(slot);
    }

    /** Tick 内推进位置，不重新置 Dirty（避免 forEachDirty 死循环）。 */
    public void applyPositionInTick(long entityId, float x, float y, float z) {
        int slot = idToSlot.getOrDefault(entityId, -1);
        if (slot < 0) {
            return;
        }
        xCoords[slot] = x;
        yCoords[slot] = y;
        zCoords[slot] = z;
    }

    public void setHp(long entityId, int hpVal) {
        int slot = idToSlot.getOrDefault(entityId, -1);
        if (slot < 0) {
            return;
        }
        hp[slot] = hpVal;
        markDirty(slot);
    }

    public float getX(long entityId) {
        int slot = idToSlot.getOrDefault(entityId, -1);
        return slot < 0 ? 0f : xCoords[slot];
    }

    public float getY(long entityId) {
        int slot = idToSlot.getOrDefault(entityId, -1);
        return slot < 0 ? 0f : yCoords[slot];
    }

    public float getZ(long entityId) {
        int slot = idToSlot.getOrDefault(entityId, -1);
        return slot < 0 ? 0f : zCoords[slot];
    }

    public int getHp(long entityId) {
        int slot = idToSlot.getOrDefault(entityId, -1);
        return slot < 0 ? 0 : hp[slot];
    }

    public boolean contains(long entityId) {
        return idToSlot.containsKey(entityId);
    }

    public boolean remove(long entityId) {
        if (!idToSlot.containsKey(entityId)) {
            return false;
        }
        int slot = idToSlot.remove(entityId);
        int last = count.decrementAndGet();
        if (slot != last && last >= 0) {
            swapSlot(slot, last);
        }
        if (last >= 0) {
            dirty[last] = false;
            entityIds[last] = 0L;
        }
        return true;
    }

    /** 仅遍历 dirty 标记的 slot，供 SceneTick 批处理。 */
    public void integrateVelocityAtSlot(int slot, float dt) {
        if (slot < 0 || slot >= count.get()) {
            return;
        }
        xCoords[slot] += vx[slot] * dt;
        yCoords[slot] += vy[slot] * dt;
        zCoords[slot] += vz[slot] * dt;
    }

    public int forEachDirty(DirtyConsumer consumer) {
        int processed = 0;
        int c = count.get();
        for (int i = 0; i < c; i++) {
            if (!dirty[i]) {
                continue;
            }
            consumer.accept(i, entityIds[i], xCoords[i], yCoords[i], zCoords[i],
                    vx[i], vy[i], vz[i], hp[i]);
            dirty[i] = false;
            processed++;
        }
        if (processed > 0) {
            dirtyTicks.incrementAndGet();
        }
        return processed;
    }

    public int size() {
        return count.get();
    }

    /** 返回当前活跃实体 ID 列表（供 Logic/Sync 流水线分片）。 */
    public List<Long> entityIds() {
        int c = count.get();
        Long[] ids = new Long[c];
        for (int i = 0; i < c; i++) {
            ids[i] = entityIds[i];
        }
        return List.of(ids);
    }

    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("count", count.get());
        m.put("capacity", xCoords.length);
        m.put("upserts", upserts.get());
        m.put("dirtyTicks", dirtyTicks.get());
        m.put("idMapSize", idToSlot.size());
        return m;
    }

    private void markDirty(int slot) {
        dirty[slot] = true;
    }

    private void swapSlot(int slot, int last) {
        long movedId = entityIds[last];
        entityIds[slot] = movedId;
        xCoords[slot] = xCoords[last];
        yCoords[slot] = yCoords[last];
        zCoords[slot] = zCoords[last];
        vx[slot] = vx[last];
        vy[slot] = vy[last];
        vz[slot] = vz[last];
        hp[slot] = hp[last];
        dirty[slot] = dirty[last];
        idToSlot.put(movedId, slot);
    }

    private void ensureCapacity(int needed) {
        if (needed <= xCoords.length) {
            return;
        }
        int newLen = Math.max(needed, xCoords.length * 2);
        xCoords = Arrays.copyOf(xCoords, newLen);
        yCoords = Arrays.copyOf(yCoords, newLen);
        zCoords = Arrays.copyOf(zCoords, newLen);
        vx = Arrays.copyOf(vx, newLen);
        vy = Arrays.copyOf(vy, newLen);
        vz = Arrays.copyOf(vz, newLen);
        hp = Arrays.copyOf(hp, newLen);
        entityIds = Arrays.copyOf(entityIds, newLen);
        dirty = Arrays.copyOf(dirty, newLen);
    }

    @FunctionalInterface
    public interface DirtyConsumer {
        void accept(int slot, long entityId, float x, float y, float z,
                    float vx, float vy, float vz, int hp);
    }
}
