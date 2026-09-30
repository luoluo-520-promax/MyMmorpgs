package cn.itcast.demo.mymmorpg.support;

/**
 * 雪花算法全局 UID：支持短暂时钟回拨等待补偿。
 */
public final class SnowflakeIdGenerator {

    private static final long EPOCH = 1_704_067_200_000L; // 2024-01-01 UTC
    private static final long WORKER_BITS = 5L;
    private static final long DATACENTER_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_WORKER = ~(-1L << WORKER_BITS);
    private static final long MAX_DATACENTER = ~(-1L << DATACENTER_BITS);
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    private static final long WORKER_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_SHIFT = SEQUENCE_BITS + WORKER_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_BITS + DATACENTER_BITS;

    /** 允许等待的最大回拨毫秒数，超过则跳号到 lastTimestamp+1 */
    private static final long MAX_CLOCK_BACKWARD_MS = 5_000L;

    private final long workerId;
    private final long datacenterId;
    private long sequence;
    private long lastTimestamp = -1L;

    public SnowflakeIdGenerator(long workerId, long datacenterId) {
        if (workerId > MAX_WORKER || workerId < 0) {
            throw new IllegalArgumentException("workerId out of range");
        }
        if (datacenterId > MAX_DATACENTER || datacenterId < 0) {
            throw new IllegalArgumentException("datacenterId out of range");
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
    }

    public long getWorkerId() {
        return workerId;
    }

    public long getDatacenterId() {
        return datacenterId;
    }

    public synchronized long nextId() {
        long ts = System.currentTimeMillis();
        if (ts < lastTimestamp) {
            long backward = lastTimestamp - ts;
            if (backward <= MAX_CLOCK_BACKWARD_MS) {
                ts = waitUntil(lastTimestamp);
            } else {
                // 大幅回拨：跳号到 lastTimestamp+1，避免抛异常中断业务
                ts = lastTimestamp + 1;
            }
        }
        if (ts == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                ts = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = ts;
        return ((ts - EPOCH) << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_SHIFT)
                | (workerId << WORKER_SHIFT)
                | sequence;
    }

    private static long waitNextMillis(long last) {
        long ts = System.currentTimeMillis();
        while (ts <= last) {
            ts = System.currentTimeMillis();
        }
        return ts;
    }

    private static long waitUntil(long target) {
        long ts = System.currentTimeMillis();
        while (ts < target) {
            try {
                Thread.sleep(Math.min(50L, target - ts));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            ts = System.currentTimeMillis();
        }
        return Math.max(ts, target);
    }
}
