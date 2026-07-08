/**
 * 文件说明
 * 模块：mmorpg-common / RPC 通信
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/rpc/CrossMessageUtil.java
 * 类型：类
 * 职责：业务层向中心服发送跨服 RPC 消息的便捷工具，封装会话查找与活性检查。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.rpc;

import org.springframework.stereotype.Component;

/**
 * 跨服消息发送工具：业务模块通过本类将 RPC DTO 转发至中心服，无需直接操作 Netty 会话。
 */
@Component
public class CrossMessageUtil {

    /** RPC 客户端路由器，维护中心服及战斗服的 IdSession 映射 */
    private final RpcClientRouter rpcClientRouter;

    /**
     * 注入 RPC 客户端路由器，用于查找已连接的中心服会话。
     */
    public CrossMessageUtil(RpcClientRouter rpcClientRouter) {
        this.rpcClientRouter = rpcClientRouter; // 保存路由器，requestToCenter 时获取中心服会话
    }

    /**
     * 向中心服发送一条 RPC 业务消息（如拉取战斗服节点列表）。
     *
     * @param message 待发送的 RPC DTO，将由 RpcJsonCodec 包装为 RpcWireEnvelope 后写出
     */
    public void requestToCenter(Object message) {
        IdSession center = rpcClientRouter.getCenterSession(); // 从路由表获取中心服 Netty 会话
        if (center != null && center.isActive()) { // 中心服已连接且 TCP 通道仍可用
            center.send(message); // 经 IdSession 编码并写入中心服 RPC 连接
        }
    }
}
