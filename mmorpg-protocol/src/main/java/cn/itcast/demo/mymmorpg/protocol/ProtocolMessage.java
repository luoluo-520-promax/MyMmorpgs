/**
 * 文件说明
 * 模块：mmorpg-common / 协议
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/protocol/ProtocolMessage.java
 * 类型：记录类
 * 职责：封装 Netty/WebSocket 下行统一回包结构（消息 ID + Protobuf 载荷）。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.protocol; // 协议层数据传输对象

/**
 * Netty / WebSocket 统一回包：命令 ID + Protobuf 序列化字节。
 * <p>业务 Handler 处理完成后构造本对象，由编码器写入二进制帧发给客户端。</p>
 *
 * @param msgId   全局消息 ID，见 {@link MessageId}
 * @param payload Protobuf 序列化后的消息体；无体消息可为空数组
 */
public record ProtocolMessage(int msgId, byte[] payload) { // record 自动生成构造器、getter、equals、hashCode
    // msgId：包头中的命令字，客户端据此选择 Protobuf 类型做 parseFrom
    // payload：消息体原始字节，通常对应某个 XxxScRsp 或 XxxScNotify 的 toByteArray() 结果
}
