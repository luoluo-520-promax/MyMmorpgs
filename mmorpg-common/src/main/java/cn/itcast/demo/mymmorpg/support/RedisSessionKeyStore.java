package cn.itcast.demo.mymmorpg.support;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Base64;

class RedisSessionKeyStore implements SessionKeyStore {

    private static final String KEY_PREFIX = "session:crypto:";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    RedisSessionKeyStore(StringRedisTemplate redisTemplate, Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
    }

    @Override
    public void put(String sessionId, byte[] keyBytes) {
        redisTemplate.opsForValue().set(
                KEY_PREFIX + sessionId,
                Base64.getEncoder().encodeToString(keyBytes),
                ttl);
    }

    @Override
    public byte[] get(String sessionId) {
        String encoded = redisTemplate.opsForValue().get(KEY_PREFIX + sessionId);
        return encoded == null ? null : Base64.getDecoder().decode(encoded);
    }

    @Override
    public void remove(String sessionId) {
        redisTemplate.delete(KEY_PREFIX + sessionId);
    }
}
