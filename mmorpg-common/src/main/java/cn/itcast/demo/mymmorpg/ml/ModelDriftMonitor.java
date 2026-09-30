package cn.itcast.demo.mymmorpg.ml;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

/**
 * 模型漂移 / 质量监控：准确率下降超阈值时触发告警（建议重训）。
 */
public final class ModelDriftMonitor {

    public record Alert(boolean alert, String modelName, double baselineAcc, double currentAcc,
                        double drop, String message) {
    }

    private final ConcurrentHashMap<String, Double> baseline = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> samples = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DoubleAdder> correct = new ConcurrentHashMap<>();
    private final double dropThreshold;

    public ModelDriftMonitor() {
        this(0.05);
    }

    public ModelDriftMonitor(double dropThreshold) {
        this.dropThreshold = Math.max(0.01, dropThreshold);
    }

    public void setBaseline(String modelName, double accuracy) {
        if (modelName != null) {
            baseline.put(modelName, clamp(accuracy));
        }
    }

    public void record(String modelName, boolean hit) {
        if (modelName == null) {
            return;
        }
        samples.computeIfAbsent(modelName, k -> new LongAdder()).increment();
        if (hit) {
            correct.computeIfAbsent(modelName, k -> new DoubleAdder()).add(1.0);
        }
    }

    public double currentAccuracy(String modelName) {
        long n = samples.getOrDefault(modelName, new LongAdder()).sum();
        if (n <= 0) {
            return baseline.getOrDefault(modelName, 1.0);
        }
        double c = correct.getOrDefault(modelName, new DoubleAdder()).sum();
        return c / n;
    }

    public Alert check(String modelName) {
        double base = baseline.getOrDefault(modelName, 0.8);
        double cur = currentAccuracy(modelName);
        double drop = base - cur;
        boolean alert = drop >= dropThreshold && samples.getOrDefault(modelName, new LongAdder()).sum() >= 20;
        String msg = alert
                ? "accuracy_drop_" + Math.round(drop * 1000) / 10.0 + "%_retrain_suggested"
                : "healthy";
        return new Alert(alert, modelName, round3(base), round3(cur), round3(drop), msg);
    }

    public Map<String, Object> snapshot(String modelName) {
        Alert a = check(modelName);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("modelName", modelName);
        m.put("alert", a.alert());
        m.put("baselineAcc", a.baselineAcc());
        m.put("currentAcc", a.currentAcc());
        m.put("drop", a.drop());
        m.put("message", a.message());
        m.put("samples", samples.getOrDefault(modelName, new LongAdder()).sum());
        return m;
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
