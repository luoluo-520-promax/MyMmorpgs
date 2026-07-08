/**
 * 文件说明
 * 模块：mmorpg-common / 网络
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/net/ServerConfig.java
 * 类型：类
 * 职责：定义 ServerConfig，供各业务模块复用与扩展。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.net;


import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 合并 common.yml 与类型专属 YAML 后的网络与端口配置（优先级：专属 YAML 覆盖 common，再叠加应用默认值）。
 */
@ConfigurationProperties
public class ServerConfig {

    /**
     * 构造 SocketConfig 实例
     */
    private SocketConfig socket = new SocketConfig();
    /**
     * 构造 RpcConfig 实例
     */
    private RpcConfig rpc = new RpcConfig();
    /**
     * 构造 HttpConfig 实例
     */
    private HttpConfig http = new HttpConfig();
    /**
     * 构造 WebSocketConfig 实例
     */
    private WebSocketConfig webSocket = new WebSocketConfig();

    /**
     * 获取服务器标识属性值
     */
    public int getServerId() {
        return socket.getId();
    }

    /**
     * 获取服务器ip属性值
     */
    public String getServerIp() {
        return socket.getServerIp() != null ? socket.getServerIp() : "";
    }

    /**
     * 获取服务器端口属性值
     */
    public int getServerPort() {
        return socket.getPort();
    }

    /**
     * 获取RPC端口属性值
     */
    public int getRpcPort() {
        return rpc.getPort();
    }

    /**
     * 获取websocket端口属性值
     */
    public int getWebSocketPort() {
        return webSocket.getPort();
    }

    /**
     * 获取http端口属性值
     */
    public int getHttpPort() {
        return http.getPort();
    }

    /**
     * 获取socket属性值
     */
    public SocketConfig getSocket() {
        return socket;
    }

    /**
     * 设置socket属性值
     */
    public void setSocket(SocketConfig socket) {
        this.socket = socket;
    }

    /**
     * 获取RPC属性值
     */
    public RpcConfig getRpc() {
        return rpc;
    }

    /**
     * 设置RPC属性值
     */
    public void setRpc(RpcConfig rpc) {
        this.rpc = rpc;
    }

    /**
     * 获取http属性值
     */
    public HttpConfig getHttp() {
        return http;
    }

    /**
     * 设置http属性值
     */
    public void setHttp(HttpConfig http) {
        this.http = http;
    }

    /**
     * 获取websocket属性值
     */
    public WebSocketConfig getWebSocket() {
        return webSocket;
    }

    /**
     * 设置websocket属性值
     */
    public void setWebSocket(WebSocketConfig webSocket) {
        this.webSocket = webSocket;
    }
}
