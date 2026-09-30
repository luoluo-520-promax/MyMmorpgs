package cn.itcast.demo.mymmorpg.anticheat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 轻量行为异常检测（规则近似孤立森林）：分析点击频率、技能间隔、移动抖动，输出异常分。
 * 生产可替换为 OnnxRuntime / Python sidecar；本实现无外部依赖、可单测。
 */
public final class BehaviorAnomalyDetector {

    public record Sample(long atMs, String kind, double value) {
    }

    public record AnomalyResult(double score, boolean suspicious, String reason, Map<String, Double> features) {
    }

    private static final int WINDOW = 64;
    private static final double SUSPICIOUS_THRESHOLD = 0.72;

    private final ConcurrentHashMap<Long, Deque<Sample>> windows = new ConcurrentHashMap<>();

    public void record(long playerId, String kind, double value, long atMs) {
        if (playerId <= 0 || kind == null || kind.isBlank()) {
            return;
        }
        Deque<Sample> q = windows.computeIfAbsent(playerId, id -> new ArrayDeque<>());
        synchronized (q) {
            q.addLast(new Sample(atMs, kind, value));
            while (q.size() > WINDOW) {
                q.removeFirst();
            }
        }
    }

    public AnomalyResult evaluate(long playerId, long nowMs) {
        Deque<Sample> q = windows.get(playerId);
        if (q == null || q.isEmpty()) {
            return new AnomalyResult(0.0, false, "insufficient_samples", Map.of());
        }
        synchronized (q) {
            double clickRate = rate(q, "click", nowMs, 5_000L);
            double skillIntervalCv = intervalCv(q, "skill");
            double moveJitter = mean(q, "move_jitter");
            double damageBurst = mean(q, "damage_burst");

            // 孤立式启发式：过高点击、过低技能间隔方差（机器人节奏）、过高位移抖动/伤害爆发
            double clickScore = clamp01((clickRate - 8.0) / 12.0);
            double robotRhythm = clamp01(1.0 - skillIntervalCv * 2.0); // CV 低 → 更像脚本
            double jitterScore = clamp01((moveJitter - 2.0) / 8.0);
            double burstScore = clamp01((damageBurst - 1.5) / 3.0);

            double score = clamp01(0.25 * clickScore + 0.45 * robotRhythm + 0.20 * jitterScore + 0.10 * burstScore);
            // 固定技能间隔 + 足够样本 → 直接抬高异常分（脚本特征）
            int skillSamples = 0;
            for (Sample s : q) {
                if ("skill".equals(s.kind())) {
                    skillSamples++;
                }
            }
            if (robotRhythm >= 0.85 && skillSamples >= 8) {
                score = Math.max(score, 0.78);
            }
            if (clickRate >= 12.0) {
                score = Math.max(score, 0.75);
            }
            Map<String, Double> features = new LinkedHashMap<>();
            features.put("clickRate", clickRate);
            features.put("skillIntervalCv", skillIntervalCv);
            features.put("moveJitter", moveJitter);
            features.put("damageBurst", damageBurst);
            features.put("clickScore", clickScore);
            features.put("robotRhythm", robotRhythm);

            String reason = "normal";
            if (score >= SUSPICIOUS_THRESHOLD) {
                if (clickScore >= robotRhythm && clickScore >= jitterScore) {
                    reason = "click_flood";
                } else if (robotRhythm >= clickScore) {
                    reason = "robot_skill_rhythm";
                } else if (burstScore > 0.6) {
                    reason = "damage_burst";
                } else {
                    reason = "trajectory_jitter";
                }
            }
            return new AnomalyResult(score, score >= SUSPICIOUS_THRESHOLD, reason, features);
        }
    }

    public void clear(long playerId) {
        windows.remove(playerId);
    }

    private static double rate(Deque<Sample> q, String kind, long nowMs, long windowMs) {
        int n = 0;
        for (Sample s : q) {
            if (kind.equals(s.kind()) && nowMs - s.atMs() <= windowMs) {
                n++;
            }
        }
        return n * 1000.0 / windowMs;
    }

    private static double intervalCv(Deque<Sample> q, String kind) {
        long prev = -1L;
        double sum = 0;
        double sumSq = 0;
        int n = 0;
        for (Sample s : q) {
            if (!kind.equals(s.kind())) {
                continue;
            }
            if (prev > 0) {
                double d = s.atMs() - prev;
                sum += d;
                sumSq += d * d;
                n++;
            }
            prev = s.atMs();
        }
        if (n < 2) {
            return 1.0; // 样本不足时视为人类（高方差）
        }
        double mean = sum / n;
        if (mean <= 1e-6) {
            return 0.0;
        }
        double var = Math.max(0.0, sumSq / n - mean * mean);
        return Math.sqrt(var) / mean;
    }

    private static double mean(Deque<Sample> q, String kind) {
        double sum = 0;
        int n = 0;
        for (Sample s : q) {
            if (kind.equals(s.kind())) {
                sum += s.value();
                n++;
            }
        }
        return n == 0 ? 0.0 : sum / n;
    }

    private static double clamp01(double v) {
        if (v < 0) {
            return 0;
        }
        if (v > 1) {
            return 1;
        }
        return v;
    }
}
