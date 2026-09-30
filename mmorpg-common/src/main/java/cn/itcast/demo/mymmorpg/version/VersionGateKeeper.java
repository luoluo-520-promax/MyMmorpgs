package cn.itcast.demo.mymmorpg.version;

import cn.itcast.demo.mymmorpg.protocol.RetCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gateway / 登录层版本门控：ClientVersion + ProtocolSchemaHash 双校验。
 */
@Service
public class VersionGateKeeper {

    public record GateResult(
            int retCode,
            boolean recommendUpdate,
            boolean hardReject,
            Set<Integer> blockedMsgIds) {

        public static GateResult ok(boolean recommend) {
            return new GateResult(RetCode.OK, recommend, false, Set.of());
        }

        public static GateResult reject(int code) {
            return new GateResult(code, false, true, Set.of());
        }

        public static GateResult soft(Set<Integer> blocked) {
            return new GateResult(RetCode.OK, false, false,
                    blocked == null ? Set.of() : Collections.unmodifiableSet(blocked));
        }
    }

    private static final String PREHEAT_PREFIX = "version:preheat:";
    private static final Duration PREHEAT_TTL = Duration.ofHours(2);

    private final ProtocolCompatMap compatMap;
    private final ObjectProvider<StringRedisTemplate> stringRedisTemplate;
    private final ConcurrentHashMap<Long, Set<Integer>> accountSoftBlocks = new ConcurrentHashMap<>();

    private long optionalClientVersion;

    public VersionGateKeeper(
            ProtocolCompatMap compatMap,
            ObjectProvider<StringRedisTemplate> stringRedisTemplate) {
        this.compatMap = compatMap;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Value("${game.client.min-version:0}")
    void bindMinVersion(long minVersion) {
        compatMap.setMinSupportedVersion(minVersion);
    }

    @Value("${game.client.optional-version:0}")
    void bindOptionalVersion(long optionalClientVersion) {
        this.optionalClientVersion = Math.max(0L, optionalClientVersion);
    }

    @Value("${game.client.protocol-hash:}")
    void bindCurrentProtocolHash(String hash) {
        compatMap.setCurrentProtocolHash(hash);
    }

    public GateResult evaluateLogin(long clientVersion, String protocolSchemaHash, long accountId) {
        ProtocolCompatMap.CompatResult compat = compatMap.evaluate(clientVersion, protocolSchemaHash);
        if (compat.hardReject()) {
            return GateResult.reject(compat.retCode());
        }
        if (!compat.blockedMsgIds().isEmpty() && accountId > 0) {
            accountSoftBlocks.put(accountId, compat.blockedMsgIds());
        }
        boolean recommend = optionalClientVersion > 0 && clientVersion < optionalClientVersion;
        if (!compat.blockedMsgIds().isEmpty()) {
            return GateResult.soft(compat.blockedMsgIds());
        }
        return GateResult.ok(recommend);
    }

    /** Gateway 识别预下载预热标记，引导客户端提前拉取高清资源。 */
    public boolean isPreheatActive(String versionCode) {
        if (versionCode == null || versionCode.isBlank()) {
            return false;
        }
        StringRedisTemplate redis = stringRedisTemplate.getIfAvailable();
        if (redis == null) {
            return false;
        }
        try {
            Boolean exists = redis.hasKey(PREHEAT_PREFIX + versionCode.trim());
            return Boolean.TRUE.equals(exists);
        } catch (Exception ignored) {
            return false;
        }
    }

    public void markPreheat(String versionCode) {
        if (versionCode == null || versionCode.isBlank()) {
            return;
        }
        StringRedisTemplate redis = stringRedisTemplate.getIfAvailable();
        if (redis == null) {
            return;
        }
        try {
            redis.opsForValue().set(PREHEAT_PREFIX + versionCode.trim(), "1", PREHEAT_TTL);
        } catch (Exception ignored) {
            // Redis 不可用时跳过
        }
    }

    public Set<Integer> blockedMsgIdsForAccount(long accountId) {
        return accountSoftBlocks.getOrDefault(accountId, Set.of());
    }

    public ProtocolCompatMap compatMap() {
        return compatMap;
    }
}
