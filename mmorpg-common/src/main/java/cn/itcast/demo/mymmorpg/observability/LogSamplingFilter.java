package cn.itcast.demo.mymmorpg.observability;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 日志采样：INFO/DEBUG 按配置比例放行，WARN/ERROR 始终保留。
 * 可用于高频战斗/移动路径，避免日志洪泛。
 */
public class LogSamplingFilter {

    public enum Level {
        TRACE, DEBUG, INFO, WARN, ERROR
    }

    private volatile double sampleRate;
    private final AtomicLong sampled = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();

    public LogSamplingFilter() {
        this(1.0);
    }

    public LogSamplingFilter(double sampleRate) {
        setSampleRate(sampleRate);
    }

    public void setSampleRate(double sampleRate) {
        if (Double.isNaN(sampleRate) || sampleRate < 0.0) {
            this.sampleRate = 0.0;
        } else if (sampleRate > 1.0) {
            this.sampleRate = 1.0;
        } else {
            this.sampleRate = sampleRate;
        }
    }

    public double getSampleRate() {
        return sampleRate;
    }

    /**
     * @param level      日志级别（大小写无关；warn/error 始终 true）
     * @param sampleRate 采样率 [0,1]，例如 0.1 表示约 10% INFO 放行
     */
    public static boolean shouldSample(String level, double sampleRate) {
        Level lv = parseLevel(level);
        return shouldSample(lv, sampleRate);
    }

    public static boolean shouldSample(Level level, double sampleRate) {
        if (level == null) {
            return true;
        }
        if (level == Level.WARN || level == Level.ERROR) {
            return true;
        }
        double rate = clamp(sampleRate);
        if (rate >= 1.0) {
            return true;
        }
        if (rate <= 0.0) {
            return false;
        }
        return ThreadLocalRandom.current().nextDouble() < rate;
    }

    /** 使用实例配置的采样率，并累计采样/丢弃计数。 */
    public boolean accept(String level) {
        boolean keep = shouldSample(level, sampleRate);
        if (keep) {
            sampled.incrementAndGet();
        } else {
            dropped.incrementAndGet();
        }
        return keep;
    }

    public long sampledCount() {
        return sampled.get();
    }

    public long droppedCount() {
        return dropped.get();
    }

    private static Level parseLevel(String level) {
        if (level == null || level.isBlank()) {
            return Level.INFO;
        }
        try {
            return Level.valueOf(level.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return Level.INFO;
        }
    }

    private static double clamp(double rate) {
        if (Double.isNaN(rate) || rate < 0.0) {
            return 0.0;
        }
        return Math.min(1.0, rate);
    }
}
