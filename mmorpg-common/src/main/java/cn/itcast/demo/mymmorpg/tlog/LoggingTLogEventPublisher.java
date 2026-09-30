package cn.itcast.demo.mymmorpg.tlog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 默认 TLog：结构化日志输出（可被 Kafka 实现替换）。
 */
@Component
@ConditionalOnProperty(name = "game.tlog.kafka.enabled", havingValue = "false", matchIfMissing = true)
public class LoggingTLogEventPublisher implements TLogEventPublisher {

    private static final Logger log = LoggerFactory.getLogger("TLOG");

    @Override
    public void emit(String eventType, long playerId, Map<String, Object> fields) {
        log.info("tlog eventType={} playerId={} fields={}", eventType, playerId, fields);
    }
}
