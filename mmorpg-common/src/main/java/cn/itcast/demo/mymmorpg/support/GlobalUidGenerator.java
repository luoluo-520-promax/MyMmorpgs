package cn.itcast.demo.mymmorpg.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.UUID;

/**
 * 全局 UID 门面：优先从 Redis 自动申请 workerId（适配 K8s 动态扩缩），否则回退配置值。
 */
@Component
public class GlobalUidGenerator {

    private static final Logger log = LoggerFactory.getLogger(GlobalUidGenerator.class);
    private static final String WORKER_KEY_PREFIX = "uid:snowflake:worker:";
    private static final String WORKER_SET = "uid:snowflake:workers";

    private final SnowflakeIdGenerator snowflake;
    private final StringRedisTemplate redis;
    private final long claimedWorkerId;
    private final String claimToken;

    public GlobalUidGenerator(
            ObjectProvider<StringRedisTemplate> redisProvider,
            @Value("${game.uid.worker-id:-1}") long configuredWorkerId,
            @Value("${game.uid.datacenter-id:1}") long datacenterId,
            @Value("${game.uid.auto-claim-worker:true}") boolean autoClaim) {
        this.redis = redisProvider == null ? null : redisProvider.getIfAvailable();
        this.claimToken = UUID.randomUUID().toString().replace("-", "");
        long workerId = configuredWorkerId;
        if (autoClaim && this.redis != null && configuredWorkerId < 0) {
            workerId = claimWorkerId(datacenterId);
        } else if (configuredWorkerId < 0) {
            workerId = Math.floorMod(UUID.randomUUID().hashCode(), 32);
        }
        this.claimedWorkerId = workerId;
        this.snowflake = new SnowflakeIdGenerator(workerId, Math.floorMod(datacenterId, 32));
        log.info("GlobalUidGenerator ready workerId={} datacenterId={} autoClaim={}",
                workerId, datacenterId, autoClaim);
    }

    /** 单测 / 非 Spring：固定 workerId。 */
    public GlobalUidGenerator(long workerId, long datacenterId) {
        this.redis = null;
        this.claimToken = "";
        this.claimedWorkerId = workerId;
        this.snowflake = new SnowflakeIdGenerator(workerId, datacenterId);
    }

    public long nextId() {
        return snowflake.nextId();
    }

    public long workerId() {
        return claimedWorkerId;
    }

    @PreDestroy
    public void release() {
        if (redis == null || claimToken.isEmpty()) {
            return;
        }
        try {
            String key = WORKER_KEY_PREFIX + claimedWorkerId;
            String v = redis.opsForValue().get(key);
            if (claimToken.equals(v)) {
                redis.delete(key);
                redis.opsForSet().remove(WORKER_SET, Long.toString(claimedWorkerId));
            }
        } catch (Exception e) {
            log.warn("release workerId failed: {}", e.getMessage());
        }
    }

    private long claimWorkerId(long datacenterId) {
        for (int i = 0; i < 32; i++) {
            String key = WORKER_KEY_PREFIX + i;
            Boolean ok = redis.opsForValue().setIfAbsent(key, claimToken, Duration.ofHours(24));
            if (Boolean.TRUE.equals(ok)) {
                redis.opsForSet().add(WORKER_SET, Long.toString(i));
                return i;
            }
        }
        long fallback = Math.floorMod((datacenterId + claimToken.hashCode()), 32);
        log.warn("all snowflake workerIds busy, fallback={}", fallback);
        return fallback;
    }
}
