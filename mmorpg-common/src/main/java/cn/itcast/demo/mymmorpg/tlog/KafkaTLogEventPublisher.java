package cn.itcast.demo.mymmorpg.tlog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Kafka 风格异步 TLog：生产可替换为真实 KafkaTemplate；当前以异步日志模拟管道。
 * 开启：game.tlog.kafka.enabled=true
 */
@Component
@Primary
@ConditionalOnProperty(name = "game.tlog.kafka.enabled", havingValue = "true")
public class KafkaTLogEventPublisher implements TLogEventPublisher {

    private static final Logger log = LoggerFactory.getLogger("TLOG.KAFKA");

    private final String topic;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "tlog-kafka");
        t.setDaemon(true);
        return t;
    });

    public KafkaTLogEventPublisher(@Value("${game.tlog.kafka.topic:mmorpg.tlog}") String topic) {
        this.topic = topic == null || topic.isBlank() ? "mmorpg.tlog" : topic;
    }

    @Override
    public void emit(String eventType, long playerId, Map<String, Object> fields) {
        executor.execute(() -> log.info("kafka topic={} eventType={} playerId={} payload={}",
                topic, eventType, playerId, fields));
    }
}
