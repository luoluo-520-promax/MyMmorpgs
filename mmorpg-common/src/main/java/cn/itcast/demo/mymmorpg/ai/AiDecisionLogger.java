package cn.itcast.demo.mymmorpg.ai;

import cn.itcast.demo.mymmorpg.tlog.TLogEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 决策结构化日志：输出决策原因/权重，并可选写入 TLog（eventType=AI_DECISION）。
 */
public final class AiDecisionLogger {

    public static final String EVENT_TYPE = "AI_DECISION";

    private static final Logger log = LoggerFactory.getLogger(AiDecisionLogger.class);

    private final TLogEventPublisher tlog;

    public AiDecisionLogger() {
        this(null);
    }

    public AiDecisionLogger(TLogEventPublisher tlog) {
        this.tlog = tlog;
    }

    public void log(String component, long playerId, String decision, String reason,
                    Map<String, Object> weights, Map<String, Object> extra) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("component", component == null ? "" : component);
        fields.put("decision", decision == null ? "" : decision);
        fields.put("reason", reason == null ? "" : reason);
        if (weights != null && !weights.isEmpty()) {
            fields.put("weights", weights);
        }
        if (extra != null) {
            fields.putAll(extra);
        }
        log.info("AI_DECISION component={} playerId={} decision={} reason={} weights={}",
                component, playerId, decision, reason, weights);
        if (tlog != null) {
            tlog.emit(EVENT_TYPE, playerId, fields);
        }
    }
}
