/**
 * 文件说明
 * 模块：mmorpg-common / 协议
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/protocol/PayloadPacket.java
 * 类型：抽象类
 * 职责：客户端上行消息的最小载体，仅持有未解析的 Protobuf 原始字节。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.protocol; // 协议入站消息基类

/**
 * 客户端/服务器消息的最小载体：仅保留原始 protobuf payload。
 * <p>具体消息类通过 {@code @MessageMeta(cmd)} 标记其命令 ID；业务层再用 {@code parseFrom(payload)} 反序列化。</p>
 */
public abstract class PayloadPacket { // 抽象基类，由 GamePackets 中各具体请求类继承

    /** 从网络帧解码得到的 Protobuf 字节，解析前不做业务校验 */
    private final byte[] payload; // final 保证入站后载荷不被篡改

    /**
     * 子类构造时传入解码后的消息体。
     *
     * @param payload Protobuf 字节；null 时规范为空数组，避免 NPE
     */
    protected PayloadPacket(byte[] payload) { // protected：仅子类或同包可调用
        this.payload = payload != null ? payload : new byte[0]; // 空消息统一为长度 0 的字节数组
    }

    /**
     * 返回原始载荷，供 Facade/Handler 调用对应 Protobuf {@code parseFrom}。
     *
     * @return 不可为 null 的字节数组（可能长度为 0）
     */
    public final byte[] payload() { // final 防止子类改变访问语义
        return payload; // 直接返回引用；若需防外部修改可改为 clone，当前约定调用方只读
    }
}
