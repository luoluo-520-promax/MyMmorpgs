package cn.itcast.demo.mymmorpg.ai;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * LLM Token 消耗计数与预算告警（进程内聚合，可挂 Prometheus）。
 */
public final class AiCostMeter {

    private final LongAdder promptTokens = new LongAdder();
    private final LongAdder completionTokens = new LongAdder();
    private final LongAdder calls = new LongAdder();
    private final LongAdder failures = new LongAdder();
    private final LongAdder budgetAlerts = new LongAdder();
    private final AtomicLong budgetTokens = new AtomicLong(1_000_000L);
    private final AtomicLong windowStartedAtMs = new AtomicLong(System.currentTimeMillis());

    public void setBudgetTokens(long budget) {
        budgetTokens.set(Math.max(1L, budget));
    }

    public void recordSuccess(long prompt, long completion) {
        calls.increment();
        promptTokens.add(Math.max(0L, prompt));
        completionTokens.add(Math.max(0L, completion));
        if (totalTokens() >= budgetTokens.get()) {
            budgetAlerts.increment();
        }
    }

    public void recordFailure() {
        failures.increment();
    }

    public long totalTokens() {
        return promptTokens.sum() + completionTokens.sum();
    }

    public boolean isOverBudget() {
        return totalTokens() >= budgetTokens.get();
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("calls", calls.sum());
        m.put("failures", failures.sum());
        m.put("promptTokens", promptTokens.sum());
        m.put("completionTokens", completionTokens.sum());
        m.put("totalTokens", totalTokens());
        m.put("budgetTokens", budgetTokens.get());
        m.put("overBudget", isOverBudget());
        m.put("budgetAlerts", budgetAlerts.sum());
        m.put("windowStartedAtMs", windowStartedAtMs.get());
        return m;
    }

    public void reset() {
        promptTokens.reset();
        completionTokens.reset();
        calls.reset();
        failures.reset();
        budgetAlerts.reset();
        windowStartedAtMs.set(System.currentTimeMillis());
    }

    /**
     * 粗估 token：中文约 1.5 字/token，英文约 4 字符/token。
     */
    public static long estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0L;
        }
        int cjk = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                cjk++;
            }
        }
        int other = text.length() - cjk;
        return Math.max(1L, (long) (cjk / 1.5 + other / 4.0));
    }
}
