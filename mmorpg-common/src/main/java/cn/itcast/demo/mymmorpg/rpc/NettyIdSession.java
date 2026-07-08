/**
 * 文件说明
 * 模块：mmorpg-common / RPC 通信
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/rpc/NettyIdSession.java
 * 类型：类
 * 职责：基于 Netty Channel 的 IdSession 实现，负责 RPC 消息的 JSON 编码与写出。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.rpc;

import io.netty.channel.Channel;

/**
 * Netty Channel 上的 RPC 会话实现：将业务 DTO 包装为 {@link RpcWireEnvelope} 后写入 TCP 连接。
 */
public class NettyIdSession implements IdSession {

    /** 与远端服务器通信的 Netty TCP 通道 */
    private final Channel channel;
    /** 将 RPC 消息对象序列化为线上 JSON 信封的编解码器 */
    private final RpcJsonCodec jsonCodec;
    /** 对端服务器在集群中的唯一编号（中心服或战斗服 serverId） */
    private final int serverId;

    /**
     * 绑定一条已建立的 Netty 连接与对端 serverId，构成可路由的 RPC 会话。
     */
    public NettyIdSession(Channel channel, RpcJsonCodec jsonCodec, int serverId) {
        this.channel = channel; // 保存 Netty 通道引用，后续用于 writeAndFlush
        this.jsonCodec = jsonCodec; // 保存 JSON 编解码器，send 时包装消息体
        this.serverId = serverId; // 保存对端 serverId，供路由表按 ID 查找
    }

    @Override
    public int getServerId() {
        return serverId; // 返回对端服务器 ID，供 RpcClientRouter 索引
    }

    @Override
    public boolean isActive() {
        return channel != null && channel.isActive(); // Channel 存在且 TCP 连接未断开
    }

    @Override
    public void send(Object message) {
        if (isActive()) { // 仅向仍在线的对端发送，避免向已断开的 Channel 写入
            channel.writeAndFlush(jsonCodec.wrap(message)); // 编码为 RpcWireEnvelope 并经 Netty 异步写出
        }
    }

    @Override
    public void close() {
        if (channel != null) { // Channel 已创建时才尝试关闭
            channel.close(); // 释放 TCP 连接，触发对端断线处理
        }
    }

    /**
     * 暴露底层 Netty Channel，供握手、心跳等底层网络操作使用。
     */
    public Channel getChannel() {
        return channel;
    }
}
