package cn.itcast.demo.mymmorpg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话/多端登录策略与票据刷新参数。
 */
@ConfigurationProperties(prefix = "game.session")
public class SessionLoginProperties {

    /**
     * single_device：账号仅允许一端（默认，兼容旧行为）
     * multi_device_allowlist：白名单 client_type 可并存
     * kick_oldest：超过 max-sessions 时踢最老会话
     */
    private String loginPolicy = "single_device";
    /** multi_device_allowlist 允许的客户端类型（大写比较） */
    private List<String> allowClientTypes = new ArrayList<>(List.of("PC", "MOBILE", "CONSOLE"));
    /** kick_oldest 时同一账号最大并存会话数 */
    private int maxSessions = 3;
    /** ResumeScene 保护期内同设备重连不踢（毫秒），默认对齐 reconnect-grace */
    private long reconnectNoKickMs = 60_000L;
    /** 登录票据 TTL（小时） */
    private long tokenTtlHours = 2;
    /** 客户端建议在 TTL 过半时刷新 */
    private boolean renewEnabled = true;
    /** 会话绑定 client_ip / user-agent 弱校验开关 */
    private boolean bindingCheckEnabled = false;
    /** 登录失败频率限制：窗口秒 */
    private long loginRateWindowSeconds = 60;
    /** 登录失败频率限制：窗口内最大尝试 */
    private int loginRateMaxAttempts = 10;
    /** 在线 Hash 心跳超时（秒），超时由清扫任务清理 */
    private long onlineHeartbeatTimeoutSeconds = 180;
    /** 在线态备份到 MySQL 的周期（秒） */
    private long sessionBackupIntervalSeconds = 30;

    public String getLoginPolicy() {
        return loginPolicy;
    }

    public void setLoginPolicy(String loginPolicy) {
        this.loginPolicy = loginPolicy == null ? "single_device" : loginPolicy.trim();
    }

    public List<String> getAllowClientTypes() {
        return allowClientTypes;
    }

    public void setAllowClientTypes(List<String> allowClientTypes) {
        this.allowClientTypes = allowClientTypes == null ? new ArrayList<>() : allowClientTypes;
    }

    public int getMaxSessions() {
        return maxSessions;
    }

    public void setMaxSessions(int maxSessions) {
        this.maxSessions = Math.max(1, maxSessions);
    }

    public long getReconnectNoKickMs() {
        return reconnectNoKickMs;
    }

    public void setReconnectNoKickMs(long reconnectNoKickMs) {
        this.reconnectNoKickMs = Math.max(0L, reconnectNoKickMs);
    }

    public long getTokenTtlHours() {
        return tokenTtlHours;
    }

    public void setTokenTtlHours(long tokenTtlHours) {
        this.tokenTtlHours = tokenTtlHours <= 0 ? 2 : tokenTtlHours;
    }

    public boolean isRenewEnabled() {
        return renewEnabled;
    }

    public void setRenewEnabled(boolean renewEnabled) {
        this.renewEnabled = renewEnabled;
    }

    public boolean isBindingCheckEnabled() {
        return bindingCheckEnabled;
    }

    public void setBindingCheckEnabled(boolean bindingCheckEnabled) {
        this.bindingCheckEnabled = bindingCheckEnabled;
    }

    public long getLoginRateWindowSeconds() {
        return loginRateWindowSeconds;
    }

    public void setLoginRateWindowSeconds(long loginRateWindowSeconds) {
        this.loginRateWindowSeconds = Math.max(1L, loginRateWindowSeconds);
    }

    public int getLoginRateMaxAttempts() {
        return loginRateMaxAttempts;
    }

    public void setLoginRateMaxAttempts(int loginRateMaxAttempts) {
        this.loginRateMaxAttempts = Math.max(1, loginRateMaxAttempts);
    }

    public long getOnlineHeartbeatTimeoutSeconds() {
        return onlineHeartbeatTimeoutSeconds;
    }

    public void setOnlineHeartbeatTimeoutSeconds(long onlineHeartbeatTimeoutSeconds) {
        this.onlineHeartbeatTimeoutSeconds = Math.max(30L, onlineHeartbeatTimeoutSeconds);
    }

    public long getSessionBackupIntervalSeconds() {
        return sessionBackupIntervalSeconds;
    }

    public void setSessionBackupIntervalSeconds(long sessionBackupIntervalSeconds) {
        this.sessionBackupIntervalSeconds = Math.max(5L, sessionBackupIntervalSeconds);
    }

    public enum Policy {
        SINGLE_DEVICE,
        MULTI_DEVICE_ALLOWLIST,
        KICK_OLDEST;

        public static Policy from(String raw) {
            if (raw == null || raw.isBlank()) {
                return SINGLE_DEVICE;
            }
            String n = raw.trim().toLowerCase().replace('-', '_');
            return switch (n) {
                case "multi_device_allowlist", "multi_device", "allowlist" -> MULTI_DEVICE_ALLOWLIST;
                case "kick_oldest", "kickoldest" -> KICK_OLDEST;
                default -> SINGLE_DEVICE;
            };
        }
    }
}
