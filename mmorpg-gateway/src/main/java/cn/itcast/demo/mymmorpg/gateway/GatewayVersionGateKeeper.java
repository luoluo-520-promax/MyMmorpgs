package cn.itcast.demo.mymmorpg.gateway;

import cn.itcast.demo.mymmorpg.protocol.RetCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gateway 层版本门控（轻量实现，避免 gateway 依赖完整 common 模块）。
 */
@Component
public class GatewayVersionGateKeeper {

    public record GateResult(int retCode, boolean hardReject, Set<Integer> blockedMsgIds) {
        public static GateResult ok() {
            return new GateResult(RetCode.OK, false, Set.of());
        }

        public static GateResult reject(int code) {
            return new GateResult(code, true, Set.of());
        }

        public static GateResult soft(Set<Integer> blocked) {
            return new GateResult(RetCode.OK, false, blocked == null ? Set.of() : Set.copyOf(blocked));
        }
    }

    private static final String PREHEAT_PREFIX = "version:preheat:";
    private static final Duration PREHEAT_TTL = Duration.ofHours(2);
    private static final Set<Integer> SOFT_BLOCKED = Set.of(2600, 2601, 2602, 2603, 2604, 2605);

    private final StringRedisTemplate redisTemplate;
    private final ConcurrentHashMap<String, String> knownProtocolHashes = new ConcurrentHashMap<>();

    private long minSupportedVersion;
    private String currentProtocolHash = "";

    public GatewayVersionGateKeeper(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        knownProtocolHashes.put("proto-v1", "soft");
        knownProtocolHashes.put("proto-combat-v2", "hard");
    }

    @Value("${game.client.min-version:0}")
    void setMinSupportedVersion(long minSupportedVersion) {
        this.minSupportedVersion = Math.max(0L, minSupportedVersion);
    }

    @Value("${game.client.protocol-hash:}")
    void setCurrentProtocolHash(String currentProtocolHash) {
        this.currentProtocolHash = currentProtocolHash == null ? "" : currentProtocolHash.trim();
    }

    public GateResult evaluateLogin(long clientVersion, String protocolSchemaHash) {
        if (minSupportedVersion > 0 && clientVersion < minSupportedVersion) {
            return GateResult.reject(RetCode.CLIENT_TOO_OLD);
        }
        if (protocolSchemaHash == null || protocolSchemaHash.isBlank()) {
            return GateResult.ok();
        }
        String hash = protocolSchemaHash.trim();
        if (currentProtocolHash.isEmpty() || hash.equals(currentProtocolHash)) {
            return GateResult.ok();
        }
        String policy = knownProtocolHashes.get(hash);
        if (policy == null) {
            return GateResult.reject(RetCode.CLIENT_TOO_OLD);
        }
        if ("hard".equals(policy)) {
            return GateResult.reject(RetCode.CLIENT_TOO_OLD);
        }
        return GateResult.soft(SOFT_BLOCKED);
    }

    public boolean isPreheatActive(String versionCode) {
        if (versionCode == null || versionCode.isBlank()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(PREHEAT_PREFIX + versionCode.trim()));
        } catch (Exception ignored) {
            return false;
        }
    }

    public void markPreheat(String versionCode) {
        if (versionCode == null || versionCode.isBlank()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(PREHEAT_PREFIX + versionCode.trim(), "1", PREHEAT_TTL);
        } catch (Exception ignored) {
            // Redis 不可用时跳过
        }
    }
}
