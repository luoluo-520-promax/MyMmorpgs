package cn.itcast.demo.mymmorpg.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MQ 消费延迟监控：按 topic 记录生产/消费时间戳，计算 lagMs 与 backlog，
 * 超过 alertThresholdMs 打 warn（可接 Prometheus alert）。
 */
@Component
public class MqLagMonitor {

    private static final Logger log = LoggerFactory.getLogger(MqLagMonitor.class);

    private final ConcurrentHashMap<String, TopicLag> topics = new ConcurrentHashMap<>();
    private volatile long alertThresholdMs;

    public MqLagMonitor(@Value("${game.mq.lag.alert-threshold-ms:30000}") long alertThresholdMs) {
        setAlertThresholdMs(alertThresholdMs);
    }

    public void setAlertThresholdMs(long alertThresholdMs) {
        this.alertThresholdMs = Math.max(1L, alertThresholdMs);
    }

    public long getAlertThresholdMs() {
        return alertThresholdMs;
    }

    public void recordProduce(String topic) {
        recordProduce(topic, System.currentTimeMillis());
    }

    public void recordProduce(String topic, long producedAtMs) {
        topicLag(topic).recordProduce(producedAtMs);
        maybeAlert(topic);
    }

    public void recordConsume(String topic) {
        topicLag(topic).recordConsume();
    }

    /** 当前消费延迟（毫秒）：积压时为 now - 最老未消费生产时间，否则 0。 */
    public long lagMs(String topic) {
        TopicLag lag = topics.get(normalize(topic));
        return lag == null ? 0L : lag.lagMs();
    }

    public int backlog(String topic) {
        TopicLag lag = topics.get(normalize(topic));
        return lag == null ? 0 : lag.backlog();
    }

    public Map<String, Object> snapshot(String topic) {
        return Map.of(
                "topic", normalize(topic),
                "lagMs", lagMs(topic),
                "backlog", backlog(topic),
                "alertThresholdMs", alertThresholdMs,
                "alerting", lagMs(topic) >= alertThresholdMs && backlog(topic) > 0);
    }

    private void maybeAlert(String topic) {
        long lag = lagMs(topic);
        int backlog = backlog(topic);
        if (backlog > 0 && lag >= alertThresholdMs) {
            log.warn("MQ lag alert topic={} lagMs={} backlog={} thresholdMs={}",
                    normalize(topic), lag, backlog, alertThresholdMs);
        }
    }

    private TopicLag topicLag(String topic) {
        return topics.computeIfAbsent(normalize(topic), t -> new TopicLag());
    }

    private static String normalize(String topic) {
        return topic == null || topic.isBlank() ? "_default" : topic;
    }

    private static final class TopicLag {
        private final Deque<Long> pendingProduceTimes = new ArrayDeque<>();

        synchronized void recordProduce(long producedAtMs) {
            pendingProduceTimes.addLast(Math.max(0L, producedAtMs));
        }

        synchronized void recordConsume() {
            if (!pendingProduceTimes.isEmpty()) {
                pendingProduceTimes.removeFirst();
            }
        }

        synchronized long lagMs() {
            Long oldest = pendingProduceTimes.peekFirst();
            if (oldest == null) {
                return 0L;
            }
            return Math.max(0L, System.currentTimeMillis() - oldest);
        }

        synchronized int backlog() {
            return pendingProduceTimes.size();
        }
    }
}
