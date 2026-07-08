/**
 * 文件说明
 * 模块：mmorpg-common / RPC 通信
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/rpc/IdSession.java
 * 类型：接口
 * 职责：抽象跨服 RPC 会话，屏蔽 Netty Channel 细节，供路由层按 serverId 发送消息。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.rpc;

/**
 * 跨服 RPC 会话抽象：代表与某一远端服务器（中心服或战斗服）之间的一条 Netty 连接。
 */
public interface IdSession {

    /**
     * 返回对端服务器的唯一标识（serverId），用于 RpcClientRouter 按 ID 查找会话。
     */
    int getServerId();

    /**
     * 判断底层 Netty Channel 是否仍处于可写状态（已连接且未关闭）。
     */
    boolean isActive();

    /**
     * 向对端写入一条 RPC 业务消息，实现类负责 JSON 编码与 Netty 帧封装。
     *
     * @param message 待发送的 RPC DTO（如 RpcReqServerLogin、Rpc_G2C_FetchFightServerNodes 等）
     */
    void send(Object message);

    /**
     * 主动关闭与该远端服务器之间的 RPC 连接。
     */
    void close();
}
