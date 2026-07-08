/**
 * 文件说明
 * 模块：mmorpg-common / 网络
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/net/GameMessage.java
 * 类型：记录类
 * 职责：定义 GameMessage，供各业务模块复用与扩展。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.net;

/**
 * Netty 管线内的单帧消息：命令 ID + Protobuf 负载
 */
// record：不可变数据载体；自动提供构造、getter、equals/hashCode/toString
// msgId：消息类型/命令号；payload：该消息的 Protobuf 序列化后的字节数
/**
 * 记录类 GameMessage：封装相关业务逻辑与数据结构。
 */
public record GameMessage(int msgId, byte[] payload) {
} // record GameMessage 结束
