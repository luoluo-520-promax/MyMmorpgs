/**
 * 文件说明
 * 模块：mmorpg-common / RPC 通信
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/rpc/RpcReqServerLogin.java
 * 类型：类
 * 职责：跨服 RPC 握手请求，游戏服/战斗服连接中心服后发送的首条鉴权消息。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.rpc;

/**
 * 跨服 RPC 握手请求：携带 serverId、MD5(serverId + "_" + signKey) 签名及服务器元信息。
 * <p>中心服校验通过后返回 {@link RpcRespServerLogin}，并将节点注册至 {@link CenterFightRegistry}。
 */
public class RpcReqServerLogin {

    /** 本服务器在集群中的唯一编号 */
    private int serverId;
    /** MD5(serverId + "_" + signKey) 签名，防止非法节点接入 RPC 网络 */
    private String sign;
    /** 服务器类型（游戏服、战斗服等），中心服据此决定注册逻辑 */
    private int serverType;
    /** 本服务器对外 RPC 监听地址（IP 或主机名），供其他节点反向连接 */
    private String host;
    /** 本服务器 RPC 监听端口 */
    private int rpcPort;

    /** 无参构造，供 JSON 反序列化使用 */
    public RpcReqServerLogin() {
    }

    /**
     * 最小握手构造：仅含 serverId 与签名，其余字段可后续 setter 补充。
     */
    public RpcReqServerLogin(int serverId, String sign) {
        this.serverId = serverId; // 声明本节点 serverId
        this.sign = sign; // 携带预计算的 MD5 签名供中心服校验
    }

    /** 获取本服务器 serverId */
    public int getServerId() {
        return serverId;
    }

    /** 设置本服务器 serverId */
    public void setServerId(int serverId) {
        this.serverId = serverId;
    }

    /** 获取 MD5 握手签名 */
    public String getSign() {
        return sign;
    }

    /** 设置 MD5 握手签名 */
    public void setSign(String sign) {
        this.sign = sign;
    }

    /** 获取服务器类型 */
    public int getServerType() {
        return serverType;
    }

    /** 设置服务器类型（游戏服/战斗服等） */
    public void setServerType(int serverType) {
        this.serverType = serverType;
    }

    /** 获取 RPC 监听地址 */
    public String getHost() {
        return host;
    }

    /** 设置 RPC 监听地址，注册到中心服目录供其他节点连接 */
    public void setHost(String host) {
        this.host = host;
    }

    /** 获取 RPC 监听端口 */
    public int getRpcPort() {
        return rpcPort;
    }

    /** 设置 RPC 监听端口 */
    public void setRpcPort(int rpcPort) {
        this.rpcPort = rpcPort;
    }
}
