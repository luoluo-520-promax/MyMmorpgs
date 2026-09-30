package cn.itcast.demo.mymmorpg.ai.content;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

/**
 * 数据驱动活动参数优化：根据参与率/付费转化调整下一轮奖励与折扣。
 */
public final class ActivityParamOptimizer {

    public record Metrics(double joinRate, double payConvertRate, double avgRewardCost) {
    }

    public record Params(double rewardMul, double discount, String reason) {
    }

    private final ConcurrentHashMap<String, LongAdder> joins = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> exposures = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> pays = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DoubleAdder> rewardCost = new ConcurrentHashMap<>();

    public void recordExposure(String activityId) {
        if (activityId != null) {
            exposures.computeIfAbsent(activityId, k -> new LongAdder()).increment();
        }
    }

    public void recordJoin(String activityId) {
        if (activityId != null) {
            joins.computeIfAbsent(activityId, k -> new LongAdder()).increment();
        }
    }

    public void recordPay(String activityId, double cost) {
        if (activityId != null) {
            pays.computeIfAbsent(activityId, k -> new LongAdder()).increment();
            rewardCost.computeIfAbsent(activityId, k -> new DoubleAdder()).add(Math.max(0, cost));
        }
    }

    public Metrics metrics(String activityId) {
        long exp = exposures.getOrDefault(activityId, new LongAdder()).sum();
        long join = joins.getOrDefault(activityId, new LongAdder()).sum();
        long pay = pays.getOrDefault(activityId, new LongAdder()).sum();
        double cost = rewardCost.getOrDefault(activityId, new DoubleAdder()).sum();
        double joinRate = exp <= 0 ? 0 : (double) join / exp;
        double payRate = join <= 0 ? 0 : (double) pay / join;
        double avgCost = pay <= 0 ? 0 : cost / pay;
        return new Metrics(round3(joinRate), round3(payRate), round3(avgCost));
    }

    public Params optimizeNext(String activityId, Params current) {
        Params cur = current == null ? new Params(1.0, 1.0, "default") : current;
        Metrics m = metrics(activityId);
        double rewardMul = cur.rewardMul();
        double discount = cur.discount();
        String reason;

        if (m.joinRate() < 0.15) {
            rewardMul = clamp(rewardMul * 1.15, 0.8, 2.5);
            discount = clamp(discount * 0.95, 0.5, 1.0);
            reason = "low_join_boost_reward";
        } else if (m.payConvertRate() < 0.05 && m.joinRate() >= 0.3) {
            discount = clamp(discount * 0.9, 0.5, 1.0);
            reason = "low_pay_boost_discount";
        } else if (m.avgRewardCost() > 500 && m.payConvertRate() > 0.2) {
            rewardMul = clamp(rewardMul * 0.92, 0.8, 2.5);
            reason = "cost_control";
        } else {
            reason = "stable";
        }
        return new Params(round2(rewardMul), round2(discount), reason);
    }

    public Map<String, Object> snapshot(String activityId) {
        Metrics m = metrics(activityId);
        Params next = optimizeNext(activityId, new Params(1.2, 0.9, "baseline"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("activityId", activityId);
        out.put("joinRate", m.joinRate());
        out.put("payConvertRate", m.payConvertRate());
        out.put("avgRewardCost", m.avgRewardCost());
        out.put("nextRewardMul", next.rewardMul());
        out.put("nextDiscount", next.discount());
        out.put("reason", next.reason());
        return out;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
