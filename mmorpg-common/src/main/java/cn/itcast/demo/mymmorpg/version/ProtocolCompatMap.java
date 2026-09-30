package cn.itcast.demo.mymmorpg.version;

import cn.itcast.demo.mymmorpg.protocol.RetCode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端多版本共存与协议 Hash 门控：柔性屏蔽新入口，硬性变更强制更新。
 */
public class ProtocolCompatMap {

    public enum Policy { SOFT, HARD }

    public record ProtocolRule(
            String protocolHash,
            long minClientVersion,
            Policy policy,
            Set<Integer> blockedMsgIds) {
    }

    private final ConcurrentHashMap<String, ProtocolRule> rules = new ConcurrentHashMap<>();
    private volatile long minSupportedVersion;
    private volatile String currentProtocolHash = "";

    public void setMinSupportedVersion(long minSupportedVersion) {
        this.minSupportedVersion = Math.max(0L, minSupportedVersion);
    }

    public void setCurrentProtocolHash(String currentProtocolHash) {
        this.currentProtocolHash = currentProtocolHash == null ? "" : currentProtocolHash.trim();
    }

    public void registerRule(String protocolHash, long minClientVersion, Policy policy, Set<Integer> blockedMsgIds) {
        if (protocolHash == null || protocolHash.isBlank()) {
            return;
        }
        rules.put(protocolHash.trim(), new ProtocolRule(
                protocolHash.trim(),
                minClientVersion,
                policy == null ? Policy.SOFT : policy,
                blockedMsgIds == null ? Set.of() : Set.copyOf(blockedMsgIds)));
    }

    public long minSupportedVersion() {
        return minSupportedVersion;
    }

    public String currentProtocolHash() {
        return currentProtocolHash;
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("minSupportedVersion", minSupportedVersion);
        out.put("currentProtocolHash", currentProtocolHash);
        out.put("ruleCount", rules.size());
        return out;
    }

    /**
     * @return null 表示兼容；非 null 为需拦截的 MsgId 集合（SOFT）或强制更新（HARD 时 retCode=601）
     */
    public CompatResult evaluate(long clientVersion, String protocolHash) {
        if (minSupportedVersion > 0 && clientVersion < minSupportedVersion) {
            return CompatResult.hardReject(RetCode.CLIENT_TOO_OLD);
        }
        if (protocolHash == null || protocolHash.isBlank()) {
            return CompatResult.ok();
        }
        String hash = protocolHash.trim();
        if (currentProtocolHash.isEmpty() || hash.equals(currentProtocolHash)) {
            return CompatResult.ok();
        }
        ProtocolRule rule = rules.get(hash);
        if (rule == null) {
            // 未知旧协议：默认 HARD 拒绝，防止反序列化异常
            return CompatResult.hardReject(RetCode.CLIENT_TOO_OLD);
        }
        if (clientVersion < rule.minClientVersion()) {
            return CompatResult.hardReject(RetCode.CLIENT_TOO_OLD);
        }
        if (rule.policy() == Policy.HARD) {
            return CompatResult.hardReject(RetCode.CLIENT_TOO_OLD);
        }
        return CompatResult.softBlock(rule.blockedMsgIds());
    }

    public record CompatResult(int retCode, boolean hardReject, Set<Integer> blockedMsgIds) {
        public static CompatResult ok() {
            return new CompatResult(RetCode.OK, false, Set.of());
        }

        public static CompatResult hardReject(int code) {
            return new CompatResult(code, true, Set.of());
        }

        public static CompatResult softBlock(Set<Integer> blockedMsgIds) {
            return new CompatResult(RetCode.OK, false,
                    blockedMsgIds == null ? Set.of() : Collections.unmodifiableSet(blockedMsgIds));
        }

        public boolean allowed() {
            return retCode == RetCode.OK && !hardReject;
        }
    }
}
