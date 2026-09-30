package cn.itcast.demo.mymmorpg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Center 场景路由：local=进程内 {@link cn.itcast.demo.mymmorpg.rpc.CenterSceneRegistry}；
 * remote=预留远端中心服（当前仍走本地目录，未配置远端时等同 local）。
 */
@ConfigurationProperties(prefix = "game.center")
public class CenterRoutingProperties {

    /** local | remote */
    private String mode = "local";
    private String localNodeId = "local";
    private String remoteBaseUrl = "";
    private String advertiseHost = "127.0.0.1";
    private int advertisePort = 8089;
    /** remote 模式 HTTP 超时（毫秒） */
    private long remoteTimeoutMs = 2500L;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getLocalNodeId() {
        return localNodeId;
    }

    public void setLocalNodeId(String localNodeId) {
        this.localNodeId = localNodeId;
    }

    public String getRemoteBaseUrl() {
        return remoteBaseUrl;
    }

    public void setRemoteBaseUrl(String remoteBaseUrl) {
        this.remoteBaseUrl = remoteBaseUrl;
    }

    public String getAdvertiseHost() {
        return advertiseHost;
    }

    public void setAdvertiseHost(String advertiseHost) {
        this.advertiseHost = advertiseHost;
    }

    public int getAdvertisePort() {
        return advertisePort;
    }

    public void setAdvertisePort(int advertisePort) {
        this.advertisePort = advertisePort;
    }

    public long getRemoteTimeoutMs() {
        return remoteTimeoutMs;
    }

    public void setRemoteTimeoutMs(long remoteTimeoutMs) {
        this.remoteTimeoutMs = remoteTimeoutMs;
    }

    public boolean isRemoteMode() {
        return mode != null && "remote".equalsIgnoreCase(mode.trim());
    }
}
