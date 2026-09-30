package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.config.SessionLoginProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 账号登录令牌：支持单端 / 白名单多端 / 踢最老会话，并可刷新 TTL。
 */
@Service
@EnableConfigurationProperties(SessionLoginProperties.class)
public class AuthTokenService {

    private static final String KEY_ACCOUNT_PREFIX = "auth:account:";
    private static final String KEY_TOKEN_PREFIX = "auth:token:";
    private static final String KEY_ONLINE_ACCOUNTS = "auth:online_accounts";
    private static final String KEY_SESSIONS = "auth:sessions:";
    private static final String KEY_DEVICE = "auth:device:";

    private final StringRedisTemplate redisTemplate;
    private final long maxOnlineAccounts;
    private final SessionLoginProperties sessionProps;
    private final ObjectMapper objectMapper;

    public AuthTokenService(
            StringRedisTemplate redisTemplate,
            ObjectProvider<ObjectMapper> objectMapperProvider,
            SessionLoginProperties sessionProps,
            @org.springframework.beans.factory.annotation.Value("${game.auth.max-online-accounts:10000}") long maxOnlineAccounts) {
        this.redisTemplate = redisTemplate;
        this.sessionProps = sessionProps;
        this.maxOnlineAccounts = maxOnlineAccounts;
        ObjectMapper om = objectMapperProvider == null ? null : objectMapperProvider.getIfAvailable();
        this.objectMapper = om == null ? new ObjectMapper() : om;
    }

    public Duration tokenTtl() {
        return Duration.ofHours(sessionProps.getTokenTtlHours());
    }

    public long tokenExpireAtMillis() {
        return System.currentTimeMillis() + tokenTtl().toMillis();
    }

    /**
     * 按策略签发令牌，返回新 token；被踢下线的旧 deviceId 列表可通过 {@link IssueResult#kickedDeviceIds()} 取得。
     */
    public IssueResult issueToken(long accountId, LoginDeviceContext device) {
        LoginDeviceContext ctx = device == null ? LoginDeviceContext.empty() : device;
        SessionLoginProperties.Policy policy = SessionLoginProperties.Policy.from(sessionProps.getLoginPolicy());
        List<String> kicked = applyPolicyBeforeIssue(accountId, ctx, policy);
        String token = UUID.randomUUID().toString().replace("-", "");
        long expireAt = tokenExpireAtMillis();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("accountId", accountId);
        meta.put("deviceId", ctx.deviceId());
        meta.put("clientType", ctx.clientType());
        meta.put("clientIp", ctx.clientIp());
        meta.put("userAgent", ctx.userAgent());
        meta.put("issuedAt", System.currentTimeMillis());
        meta.put("expireAt", expireAt);
        writeToken(accountId, token, ctx.deviceId(), meta);
        return new IssueResult(token, expireAt, kicked);
    }

    /** 兼容旧调用：等价于 single_device 签发。 */
    public String issueTokenForAccount(long accountId) {
        return issueToken(accountId, LoginDeviceContext.empty()).token();
    }

    public void revokeTokenForAccount(long accountId) {
        Map<Object, Object> sessions = redisTemplate.opsForHash().entries(KEY_SESSIONS + accountId);
        if (sessions != null) {
            for (Object tokenObj : sessions.values()) {
                if (tokenObj != null) {
                    redisTemplate.delete(KEY_TOKEN_PREFIX + tokenObj);
                }
            }
        }
        redisTemplate.delete(KEY_SESSIONS + accountId);
        String legacy = redisTemplate.opsForValue().get(KEY_ACCOUNT_PREFIX + accountId);
        if (legacy != null && !legacy.isEmpty()) {
            redisTemplate.delete(KEY_TOKEN_PREFIX + legacy);
        }
        redisTemplate.delete(KEY_ACCOUNT_PREFIX + accountId);
        redisTemplate.opsForSet().remove(KEY_ONLINE_ACCOUNTS, Long.toString(accountId));
    }

    public void revokeDevice(long accountId, String deviceId) {
        if (accountId <= 0 || deviceId == null || deviceId.isBlank()) {
            return;
        }
        Object token = redisTemplate.opsForHash().get(KEY_SESSIONS + accountId, deviceId);
        if (token != null) {
            redisTemplate.delete(KEY_TOKEN_PREFIX + token);
            redisTemplate.opsForHash().delete(KEY_SESSIONS + accountId, deviceId);
        }
        redisTemplate.delete(KEY_DEVICE + accountId + ":" + deviceId);
        maybeClearLegacy(accountId);
    }

    /**
     * 刷新票据：校验旧 token 后延长 TTL；可选轮换 token 值。
     */
    public IssueResult renewToken(String oldToken, String deviceId) {
        TokenMeta meta = getTokenMeta(oldToken);
        if (meta == null) {
            return null;
        }
        if (sessionProps.isBindingCheckEnabled() && deviceId != null && !deviceId.isBlank()
                && meta.deviceId() != null && !meta.deviceId().isBlank()
                && !meta.deviceId().equals(deviceId)) {
            return null;
        }
        // 轮换：删旧发新，防止长期固定票据被盗用
        redisTemplate.delete(KEY_TOKEN_PREFIX + oldToken);
        LoginDeviceContext ctx = new LoginDeviceContext(
                meta.deviceId(), meta.clientType(), meta.clientIp(), meta.userAgent());
        return issueToken(meta.accountId(), ctx);
    }

    /**
     * 票据过期但会话仍在线时，按 session/account 重新签发（降级重连）。
     */
    public IssueResult reissueByAccount(long accountId, LoginDeviceContext device) {
        if (accountId <= 0) {
            return null;
        }
        return issueToken(accountId, device == null ? LoginDeviceContext.empty() : device);
    }

    public Long getAccountIdByToken(String token) {
        TokenMeta meta = getTokenMeta(token);
        return meta == null ? null : meta.accountId();
    }

    public TokenMeta getTokenMeta(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        String v = redisTemplate.opsForValue().get(KEY_TOKEN_PREFIX + token);
        if (v == null || v.isEmpty()) {
            return null;
        }
        // 兼容旧格式：纯 accountId
        if (!v.startsWith("{")) {
            try {
                return new TokenMeta(Long.parseLong(v), "", "", "", "", 0L, 0L);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        try {
            Map<String, Object> m = objectMapper.readValue(v, new TypeReference<>() {
            });
            return new TokenMeta(
                    asLong(m.get("accountId")),
                    str(m.get("deviceId")),
                    str(m.get("clientType")),
                    str(m.get("clientIp")),
                    str(m.get("userAgent")),
                    asLong(m.get("issuedAt")),
                    asLong(m.get("expireAt")));
        } catch (Exception e) {
            return null;
        }
    }

    public boolean validateBinding(String token, String clientIp, String userAgent) {
        if (!sessionProps.isBindingCheckEnabled()) {
            return true;
        }
        TokenMeta meta = getTokenMeta(token);
        if (meta == null) {
            return false;
        }
        if (meta.clientIp() != null && !meta.clientIp().isBlank()
                && clientIp != null && !clientIp.isBlank()
                && !meta.clientIp().equals(clientIp)) {
            return false;
        }
        if (meta.userAgent() != null && !meta.userAgent().isBlank()
                && userAgent != null && !userAgent.isBlank()
                && !meta.userAgent().equals(userAgent)) {
            return false;
        }
        return true;
    }

    public List<DeviceSession> listSessions(long accountId) {
        Map<Object, Object> sessions = redisTemplate.opsForHash().entries(KEY_SESSIONS + accountId);
        List<DeviceSession> out = new ArrayList<>();
        if (sessions == null) {
            return out;
        }
        for (Map.Entry<Object, Object> e : sessions.entrySet()) {
            String deviceId = String.valueOf(e.getKey());
            String token = String.valueOf(e.getValue());
            TokenMeta meta = getTokenMeta(token);
            out.add(new DeviceSession(deviceId,
                    meta == null ? "" : meta.clientType(),
                    token,
                    meta == null ? 0L : meta.issuedAt(),
                    meta == null ? 0L : meta.expireAt()));
        }
        out.sort(Comparator.comparingLong(DeviceSession::issuedAt));
        return out;
    }

    public boolean isServerOverloaded() {
        return countOnlineAccounts() >= maxOnlineAccounts;
    }

    public long countOnlineAccounts() {
        Long size = redisTemplate.opsForSet().size(KEY_ONLINE_ACCOUNTS);
        return size == null ? 0 : size;
    }

    private List<String> applyPolicyBeforeIssue(long accountId, LoginDeviceContext ctx, SessionLoginProperties.Policy policy) {
        List<String> kicked = new ArrayList<>();
        String deviceKey = normalizeDevice(ctx.deviceId());
        List<DeviceSession> existing = listSessions(accountId);
        // 同设备重登：只替换本设备票，不踢其他端
        if (!deviceKey.isBlank()) {
            for (DeviceSession s : existing) {
                if (deviceKey.equals(s.deviceId())) {
                    revokeDevice(accountId, s.deviceId());
                    return kicked;
                }
            }
        }
        switch (policy) {
            case MULTI_DEVICE_ALLOWLIST -> {
                String type = normalizeType(ctx.clientType());
                boolean allowed = sessionProps.getAllowClientTypes().stream()
                        .anyMatch(t -> normalizeType(t).equals(type));
                if (!allowed) {
                    // 不在白名单：退化为单端
                    for (DeviceSession s : existing) {
                        revokeDevice(accountId, s.deviceId());
                        kicked.add(s.deviceId());
                    }
                } else {
                    // 同 clientType 互踢，不同类型并存
                    for (DeviceSession s : existing) {
                        if (normalizeType(s.clientType()).equals(type)) {
                            revokeDevice(accountId, s.deviceId());
                            kicked.add(s.deviceId());
                        }
                    }
                }
            }
            case KICK_OLDEST -> {
                while (existing.size() >= sessionProps.getMaxSessions()) {
                    DeviceSession oldest = existing.get(0);
                    revokeDevice(accountId, oldest.deviceId());
                    kicked.add(oldest.deviceId());
                    existing.remove(0);
                }
            }
            default -> {
                for (DeviceSession s : existing) {
                    revokeDevice(accountId, s.deviceId());
                    kicked.add(s.deviceId());
                }
                // 清理旧单点键
                String old = redisTemplate.opsForValue().get(KEY_ACCOUNT_PREFIX + accountId);
                if (old != null && !old.isEmpty()) {
                    redisTemplate.delete(KEY_TOKEN_PREFIX + old);
                }
            }
        }
        return kicked;
    }

    private void writeToken(long accountId, String token, String deviceId, Map<String, Object> meta) {
        String device = normalizeDevice(deviceId);
        if (device.isBlank()) {
            device = "default";
            meta.put("deviceId", device);
        }
        Duration ttl = tokenTtl();
        try {
            redisTemplate.opsForValue().set(KEY_TOKEN_PREFIX + token, objectMapper.writeValueAsString(meta), ttl);
        } catch (Exception e) {
            redisTemplate.opsForValue().set(KEY_TOKEN_PREFIX + token, Long.toString(accountId), ttl);
        }
        redisTemplate.opsForHash().put(KEY_SESSIONS + accountId, device, token);
        redisTemplate.expire(KEY_SESSIONS + accountId, ttl);
        // 兼容旧网关：主 token 指向最新签发
        redisTemplate.opsForValue().set(KEY_ACCOUNT_PREFIX + accountId, token, ttl);
        redisTemplate.opsForSet().add(KEY_ONLINE_ACCOUNTS, Long.toString(accountId));
    }

    private void maybeClearLegacy(long accountId) {
        Long size = redisTemplate.opsForHash().size(KEY_SESSIONS + accountId);
        if (size == null || size == 0L) {
            redisTemplate.delete(KEY_ACCOUNT_PREFIX + accountId);
            redisTemplate.opsForSet().remove(KEY_ONLINE_ACCOUNTS, Long.toString(accountId));
        }
    }

    private static String normalizeDevice(String deviceId) {
        return deviceId == null ? "" : deviceId.trim();
    }

    private static String normalizeType(String clientType) {
        return clientType == null || clientType.isBlank() ? "UNKNOWN" : clientType.trim().toUpperCase();
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static long asLong(Object o) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        if (o == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public record LoginDeviceContext(String deviceId, String clientType, String clientIp, String userAgent) {
        public static LoginDeviceContext empty() {
            return new LoginDeviceContext("", "", "", "");
        }
    }

    public record IssueResult(String token, long expireAtMillis, List<String> kickedDeviceIds) {
    }

    public record TokenMeta(long accountId, String deviceId, String clientType, String clientIp,
                            String userAgent, long issuedAt, long expireAt) {
    }

    public record DeviceSession(String deviceId, String clientType, String token, long issuedAt, long expireAt) {
    }
}
