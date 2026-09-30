package cn.itcast.demo.mymmorpg.aoi;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基本类型网格存储：用 long[] 存 entityId + 坐标，避免 HashMap&lt;Integer&gt; 装箱与短生命周期 Entry。
 */
@Component
public class PrimitiveGridStore {

    /** 每条记录 4 个 long：entityId, xBits, yBits, zBits（坐标按 milli 定点） */
    public static final int FIELDS_PER_CELL = 4;

    private long[] cells = new long[256 * FIELDS_PER_CELL];
    private final AtomicInteger count = new AtomicInteger();

    public int size() {
        return count.get();
    }

    public void upsert(long entityId, float x, float y, float z) {
        int idx = indexOf(entityId);
        if (idx < 0) {
            ensureCapacity(count.get() + 1);
            idx = count.getAndIncrement();
        }
        int base = idx * FIELDS_PER_CELL;
        cells[base] = entityId;
        cells[base + 1] = Float.floatToRawIntBits(x);
        cells[base + 2] = Float.floatToRawIntBits(y);
        cells[base + 3] = Float.floatToRawIntBits(z);
    }

    public boolean remove(long entityId) {
        int idx = indexOf(entityId);
        if (idx < 0) {
            return false;
        }
        int last = count.get() - 1;
        if (idx != last) {
            int base = idx * FIELDS_PER_CELL;
            int lastBase = last * FIELDS_PER_CELL;
            System.arraycopy(cells, lastBase, cells, base, FIELDS_PER_CELL);
        }
        count.decrementAndGet();
        return true;
    }

    public float[] get(long entityId) {
        int idx = indexOf(entityId);
        if (idx < 0) {
            return null;
        }
        int base = idx * FIELDS_PER_CELL;
        return new float[]{
                Float.intBitsToFloat((int) cells[base + 1]),
                Float.intBitsToFloat((int) cells[base + 2]),
                Float.intBitsToFloat((int) cells[base + 3])
        };
    }

    /**
     * 半径查询：返回 entityId 数组（无装箱）。
     */
    public long[] queryRadius(float cx, float cy, float cz, float radius) {
        float r2 = radius * radius;
        long[] hits = new long[count.get()];
        int n = 0;
        int c = count.get();
        for (int i = 0; i < c; i++) {
            int base = i * FIELDS_PER_CELL;
            float x = Float.intBitsToFloat((int) cells[base + 1]);
            float y = Float.intBitsToFloat((int) cells[base + 2]);
            float z = Float.intBitsToFloat((int) cells[base + 3]);
            float dx = x - cx;
            float dy = y - cy;
            float dz = z - cz;
            if (dx * dx + dy * dy + dz * dz <= r2) {
                hits[n++] = cells[base];
            }
        }
        return n == hits.length ? hits : Arrays.copyOf(hits, n);
    }

    private int indexOf(long entityId) {
        int c = count.get();
        for (int i = 0; i < c; i++) {
            if (cells[i * FIELDS_PER_CELL] == entityId) {
                return i;
            }
        }
        return -1;
    }

    private void ensureCapacity(int needed) {
        int required = needed * FIELDS_PER_CELL;
        if (required <= cells.length) {
            return;
        }
        int newLen = Math.max(required, cells.length * 2);
        cells = Arrays.copyOf(cells, newLen);
    }

    public Map<String, Object> stats() {
        return Map.of("count", count.get(), "capacity", cells.length / FIELDS_PER_CELL);
    }
}
