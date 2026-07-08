/**
 * 文件说明
 * 模块：mmorpg-common / 网络
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/net/BinaryFrameSender.java
 * 类型：类
 * 职责：定义 BinaryFrameSender，供各业务模块复用与扩展。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.net;

// Spring WebSocket：二进制消息封装

import org.springframework.web.socket.BinaryMessage;
// Spring WebSocket：一次连接的会话对象

import org.springframework.web.socket.WebSocketSession;

// NIO 字节缓冲区：组帧、读写字

import java.nio.ByteBuffer;
// 字节序：大端/小端，网络协议通常用大端（BIG_ENDIAN?

import java.nio.ByteOrder;

/**
 * WebSocket 二进制帧：[length:4][msgId:4][payload]，与 Netty 管线一致
 */
// 工具类：只提供静态发送方法，不允许 new
public final class BinaryFrameSender {

    // 私有构造：禁止外部创建实例
    /**
     * 构造 BinaryFrameSender 实例
     */
    private BinaryFrameSender() {
    } // BinaryFrameSender 构造结

    // 向指定 WebSocket 会话发送一帧：msgId + payload，外层再加 4 字节长度
    /**
     * sendwebsocket；参数：WebSocketSession session, int msgId, byte[] payload
     */
    public static void sendWebSocket(WebSocketSession session, int msgId, byte[] payload) throws Exception {
        // 会话为空或已关闭则直接返回，避免 NPE 或发送失
        if (session == null || !session.isOpen()) { // 条件分支判断
            return;  // 执行语句
        } // if 结束
        // 帧内容长度 = 4 字节 msgId + 负载长度（不含最前面 4 字节“总长度”字段）
        int frameContentLength = 4 + payload.length;  // 执行语句
        // 分配缓冲区：前 4 字节 frameContentLength，再 4 字节 msgId，再加 payload
        ByteBuffer out = ByteBuffer.allocate(4 + 4 + payload.length); // 拼接 IV 与密文或解析缓冲区
        // 网络序：高位字节在前
        out.order(ByteOrder.BIG_ENDIAN);
        // 写入“内容区”长度（?frameContentLength?
        out.putInt(frameContentLength);
        // 写入消息
        out.putInt(msgId);
        // 写入 Protobuf 等二进制负载
        out.put(payload);  // 向映射写入键值
        // 从写模式切换到读模式：position=0，供对方按顺序读
        out.flip();
        // 以二进制 WebSocket 消息发出
        /**
         * 构造 BinaryMessage 实例
         */
        session.sendMessage(new BinaryMessage(out));
    } // sendWebSocket 结束
} // class BinaryFrameSender 结束
