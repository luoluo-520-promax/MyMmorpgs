package cn.itcast.demo.mymmorpg.metrics;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * AI 性能指标：BT 耗时、LLM 成功率、NPC 响应延迟、决策 QPS（进程内聚合）。
 */
public final class AiMetrics {

    private final LongAdder btTicks = new LongAdder();
    private final LongAdder btLatencyNanos = new LongAdder();
    private final LongAdder llmCalls = new LongAdder();
    private final LongAdder llmSuccess = new LongAdder();
    private final LongAdder llmLatencyNanos = new LongAdder();
    private final LongAdder npcResponses = new LongAdder();
    private final LongAdder npcLatencyNanos = new LongAdder();
    private final LongAdder decisions = new LongAdder();
    private final ConcurrentHashMap<String, LongAdder> decisionByType = new ConcurrentHashMap<>();
    private final long startedAtMs = System.currentTimeMillis();

    public void recordBtTick(long latencyNanos) {
        btTicks.increment();
        btLatencyNanos.add(Math.max(0L, latencyNanos));
        decisions.increment();
        decisionByType.computeIfAbsent("bt", k -> new LongAdder()).increment();
    }

    public void recordLlm(boolean success, long latencyNanos) {
        llmCalls.increment();
        if (success) {
            llmSuccess.increment();
        }
        llmLatencyNanos.add(Math.max(0L, latencyNanos));
        decisions.increment();
        decisionByType.computeIfAbsent("llm", k -> new LongAdder()).increment();
    }

    public void recordNpc(long latencyNanos) {
        npcResponses.increment();
        npcLatencyNanos.add(Math.max(0L, latencyNanos));
        decisions.increment();
        decisionByType.computeIfAbsent("npc", k -> new LongAdder()).increment();
    }

    public void recordDecision(String type) {
        decisions.increment();
        String key = type == null || type.isBlank() ? "other" : type;
        decisionByType.computeIfAbsent(key, k -> new LongAdder()).increment();
    }

    public double llmSuccessRate() {
        long total = llmCalls.sum();
        return total == 0 ? 1.0 : (double) llmSuccess.sum() / total;
    }

    public double btAvgLatencyMs() {
        long n = btTicks.sum();
        return n == 0 ? 0.0 : TimeUnit.NANOSECONDS.toMicros(btLatencyNanos.sum() / n) / 1000.0;
    }

    public double npcP99LatencyMsEstimate() {
        // 进程内无直方图时用均值 * 2.5 近似 P99 上界提示
        long n = npcResponses.sum();
        if (n == 0) {
            return 0.0;
        }
        double avgMs = TimeUnit.NANOSECONDS.toMicros(npcLatencyNanos.sum() / n) / 1000.0;
        return avgMs * 2.5;
    }

    public double decisionQps() {
        long elapsedSec = Math.max(1L, (System.currentTimeMillis() - startedAtMs) / 1000L);
        return decisions.sum() / (double) elapsedSec;
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("btTicks", btTicks.sum());
        m.put("btAvgLatencyMs", btAvgLatencyMs());
        m.put("llmCalls", llmCalls.sum());
        m.put("llmSuccess", llmSuccess.sum());
        m.put("llmSuccessRate", llmSuccessRate());
        m.put("npcResponses", npcResponses.sum());
        m.put("npcP99LatencyMsEstimate", npcP99LatencyMsEstimate());
        m.put("decisionQps", decisionQps());
        m.put("decisions", decisions.sum());
        Map<String, Long> byType = new LinkedHashMap<>();
        decisionByType.forEach((k, v) -> byType.put(k, v.sum()));
        m.put("decisionByType", byType);
        return m;
    }
}
