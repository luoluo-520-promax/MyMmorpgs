/**
 * 文件说明
 * 模块：mmorpg-common / RPC 通信
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/rpc/RpcPacket.java
 * 类型：类
 * 职责：Protostuff RPC 二进制网络传输包（扩展占位），含帧长度与载荷字节。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.rpc;

/**
 * Protostuff RPC 网络传输包：帧头长度 + Protostuff 序列化后的请求/响应字节（扩展占位）。
 * <p>当前主链路使用 JSON {@link RpcWireEnvelope}，本类预留二进制 RPC 升级路径。
 */
public class RpcPacket {

    /** TCP 帧中 payload 部分的字节长度，用于 Netty 粘包拆包 */
    private int length;
    /** Protostuff 序列化后的 RPC 请求或响应原始字节 */
    private byte[] payload;

    /** 获取帧载荷字节长度 */
    public int getLength() {
        return length;
    }

    /** 设置帧载荷字节长度（编码时写入帧头） */
    public void setLength(int length) {
        this.length = length;
    }

    /** 获取 Protostuff 序列化后的原始字节 */
    public byte[] getPayload() {
        return payload;
    }

    /** 设置 Protostuff 序列化后的原始字节 */
    public void setPayload(byte[] payload) {
        this.payload = payload;
    }
}
