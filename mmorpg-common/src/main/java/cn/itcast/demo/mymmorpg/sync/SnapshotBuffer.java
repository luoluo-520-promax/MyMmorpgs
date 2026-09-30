package cn.itcast.demo.mymmorpg.sync;

/**
 * 位置快照环形缓冲：保留约 500ms 历史，供延迟补偿 / 命中回滚校验与客户端插值。
 */
public final class SnapshotBuffer {

    public record PosSnapshot(long timestampMs, float x, float y, float z, float speed) {
    }

    private final PosSnapshot[] ring;
    private final long windowMs;
    private int head;
    private int size;

    public SnapshotBuffer(int capacity, long windowMs) {
        this.ring = new PosSnapshot[Math.max(8, capacity)];
        this.windowMs = Math.max(100L, windowMs);
    }

    public static SnapshotBuffer forLagCompensation() {
        return new SnapshotBuffer(32, 500L);
    }

    public synchronized void push(long timestampMs, float x, float y, float z, float speed) {
        ring[head] = new PosSnapshot(timestampMs, x, y, z, speed);
        head = (head + 1) % ring.length;
        if (size < ring.length) {
            size++;
        }
        purgeOlderThan(timestampMs - windowMs);
    }

    /**
     * 回滚到 clientTimestamp 附近的历史位置（找不到则返回最近一条）。
     */
    public synchronized PosSnapshot rewindTo(long clientTimestampMs) {
        if (size == 0) {
            return null;
        }
        PosSnapshot best = null;
        long bestDelta = Long.MAX_VALUE;
        for (int i = 0; i < size; i++) {
            int idx = (head - 1 - i + ring.length * 2) % ring.length;
            PosSnapshot s = ring[idx];
            if (s == null) {
                continue;
            }
            long delta = Math.abs(s.timestampMs() - clientTimestampMs);
            if (delta < bestDelta) {
                bestDelta = delta;
                best = s;
            }
            if (s.timestampMs() <= clientTimestampMs && delta <= windowMs) {
                return s;
            }
        }
        return best;
    }

    /**
     * 延迟补偿：用历史点校验「当时」位移是否在合理速度内，而不是直接拒绝超速。
     */
    public synchronized boolean validateWithLagCompensation(
            long clientTimestampMs, float toX, float toY, float toZ, float maxSpeed, float slack) {
        PosSnapshot past = rewindTo(clientTimestampMs);
        if (past == null) {
            return true;
        }
        float dx = toX - past.x();
        float dy = toY - past.y();
        float dz = toZ - past.z();
        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        long dt = Math.max(1L, System.currentTimeMillis() - past.timestampMs());
        float allowed = maxSpeed * (dt / 1000f) + Math.max(0f, slack);
        return dist <= allowed;
    }

    public synchronized int size() {
        return size;
    }

    private void purgeOlderThan(long minTs) {
        while (size > 0) {
            int oldestIdx = (head - size + ring.length) % ring.length;
            PosSnapshot oldest = ring[oldestIdx];
            if (oldest == null || oldest.timestampMs() >= minTs) {
                break;
            }
            ring[oldestIdx] = null;
            size--;
        }
    }
}
