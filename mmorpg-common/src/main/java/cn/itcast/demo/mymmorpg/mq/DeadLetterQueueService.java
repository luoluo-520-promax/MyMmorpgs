package cn.itcast.demo.mymmorpg.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存死信队列（可选 Redis 镜像）：消费失败消息入队，支持列表/重试/丢弃，
 * 并暴露 depth / oldestAgeMs 指标供运维巡检。
 */
@Component
public class DeadLetterQueueService {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterQueueService.class);
    private static final String REDIS_KEY = "mmorpg:mq:dlq";

    public record DeadLetterMessage(
            String messageId,
            String topic,
            String payload,
            String error,
            int failCount,
            long enqueuedAtMs) {
    }

    private final ConcurrentHashMap<String, DeadLetterMessage> store = new ConcurrentHashMap<>();
    private final AtomicLong enqueueTotal = new AtomicLong();
    private final ObjectProvider<StringRedisTemplate> redisProvider;

    public DeadLetterQueueService(ObjectProvider<StringRedisTemplate> redisProvider) {
        this.redisProvider = redisProvider;
    }

    public void enqueue(String topic, String messageId, String payload, String error, int failCount) {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("messageId required");
        }
        DeadLetterMessage msg = new DeadLetterMessage(
                messageId,
                topic == null ? "" : topic,
                payload == null ? "" : payload,
                error == null ? "" : error,
                Math.max(0, failCount),
                System.currentTimeMillis());
        store.put(messageId, msg);
        enqueueTotal.incrementAndGet();
        mirrorToRedis(msg);
        log.warn("DLQ enqueue topic={} messageId={} failCount={} error={}",
                msg.topic(), msg.messageId(), msg.failCount(), truncate(msg.error(), 200));
    }

    public List<DeadLetterMessage> list(int limit) {
        int cap = Math.max(1, limit);
        return store.values().stream()
                .sorted(Comparator.comparingLong(DeadLetterMessage::enqueuedAtMs))
                .limit(cap)
                .toList();
    }

    /**
     * 取出并移除，供调用方重新投递；不存在则 empty。
     */
    public Optional<DeadLetterMessage> retry(String messageId) {
        if (messageId == null) {
            return Optional.empty();
        }
        DeadLetterMessage removed = store.remove(messageId);
        if (removed != null) {
            removeFromRedis(messageId);
            log.info("DLQ retry messageId={} topic={}", messageId, removed.topic());
        }
        return Optional.ofNullable(removed);
    }

    public boolean discard(String messageId) {
        if (messageId == null) {
            return false;
        }
        DeadLetterMessage removed = store.remove(messageId);
        if (removed != null) {
            removeFromRedis(messageId);
            log.info("DLQ discard messageId={} topic={}", messageId, removed.topic());
            return true;
        }
        return false;
    }

    public int depth() {
        return store.size();
    }

    /** 队列中最老消息的存活毫秒数；空队列返回 0。 */
    public long oldestAgeMs() {
        long oldest = Long.MAX_VALUE;
        for (DeadLetterMessage m : store.values()) {
            oldest = Math.min(oldest, m.enqueuedAtMs());
        }
        if (oldest == Long.MAX_VALUE) {
            return 0L;
        }
        return Math.max(0L, System.currentTimeMillis() - oldest);
    }

    public Map<String, Object> metrics() {
        return Map.of(
                "depth", depth(),
                "oldestAgeMs", oldestAgeMs(),
                "enqueueTotal", enqueueTotal.get());
    }

    private void mirrorToRedis(DeadLetterMessage msg) {
        StringRedisTemplate redis = redis();
        if (redis == null) {
            return;
        }
        try {
            String value = msg.topic() + "|" + msg.failCount() + "|" + msg.enqueuedAtMs()
                    + "|" + msg.error() + "|" + msg.payload();
            redis.opsForHash().put(REDIS_KEY, msg.messageId(), value);
        } catch (Exception ex) {
            log.debug("DLQ redis mirror skip: {}", ex.toString());
        }
    }

    private void removeFromRedis(String messageId) {
        StringRedisTemplate redis = redis();
        if (redis == null) {
            return;
        }
        try {
            redis.opsForHash().delete(REDIS_KEY, messageId);
        } catch (Exception ex) {
            log.debug("DLQ redis remove skip: {}", ex.toString());
        }
    }

    private StringRedisTemplate redis() {
        return redisProvider == null ? null : redisProvider.getIfAvailable();
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "...";
    }

    /** 测试辅助：清空内存队列 */
    public void clear() {
        List<String> ids = new ArrayList<>(store.keySet());
        store.clear();
        for (String id : ids) {
            removeFromRedis(id);
        }
    }
}
