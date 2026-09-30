package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.config.SessionLoginProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 登录频率限制：{@code rate-limit:login:{ip}:{account}}。
 */
@Service
@EnableConfigurationProperties(SessionLoginProperties.class)
public class LoginRateLimiter {

    private static final String KEY = "rate-limit:login:";

    private final StringRedisTemplate redis;
    private final SessionLoginProperties props;

    public LoginRateLimiter(StringRedisTemplate redis, SessionLoginProperties props) {
        this.redis = redis;
        this.props = props;
    }

    /** @return true 表示已超限，应拒绝登录 */
    public boolean isLimited(String clientIp, String accountName) {
        String ip = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp.trim();
        String acc = accountName == null || accountName.isBlank() ? "_" : accountName.trim().toLowerCase();
        String key = KEY + ip + ":" + acc;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofSeconds(props.getLoginRateWindowSeconds()));
        }
        return count != null && count > props.getLoginRateMaxAttempts();
    }

    public void clearOnSuccess(String clientIp, String accountName) {
        String ip = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp.trim();
        String acc = accountName == null || accountName.isBlank() ? "_" : accountName.trim().toLowerCase();
        redis.delete(KEY + ip + ":" + acc);
    }
}
