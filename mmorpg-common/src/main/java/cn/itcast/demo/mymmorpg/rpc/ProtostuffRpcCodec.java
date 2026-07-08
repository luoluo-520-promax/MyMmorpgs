/**
 * 文件说明
 * 模块：mmorpg-common / RPC 通信
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/rpc/ProtostuffRpcCodec.java
 * 类型：类
 * 职责：Protostuff 二进制序列化工具（扩展占位，当前主链路使用 JSON {@link RpcJsonCodec}）。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.rpc;

import io.protostuff.LinkedBuffer;
import io.protostuff.ProtostuffIOUtil;
import io.protostuff.runtime.RuntimeSchema;

/**
 * Protostuff 序列化工具，供未来跨服 RPC 切换为二进制协议时使用。
 * <p>
 * 当前 Netty 管道主链路为 {@link RpcLengthJsonEncoder}/{@link RpcLengthJsonDecoder} + JSON；
 * 本类保留以便战斗/中心服高吞吐场景可替换 JSON 而不改业务 POJO 结构。
 * </p>
 */
public final class ProtostuffRpcCodec {

    /** 工具类禁止实例化，避免误创建无状态编解码器对象 */
    private ProtostuffRpcCodec() {
    }

    /**
     * 将 RPC POJO 序列化为 Protostuff 字节流。
     *
     * @param obj   待序列化的跨服消息对象
     * @param clazz 对象运行时类型，用于生成 Schema
     * @return 可用于 Netty writeBytes 的二进制载荷
     */
    public static <T> byte[] serialize(T obj, Class<T> clazz) {
        RuntimeSchema<T> schema = RuntimeSchema.createFrom(clazz); // 按类反射生成 Protostuff 读写模式
        LinkedBuffer buffer = LinkedBuffer.allocate(LinkedBuffer.DEFAULT_BUFFER_SIZE); // 复用缓冲减少 GC
        try {
            return ProtostuffIOUtil.toByteArray(obj, schema, buffer); // 写入二进制，适合替代 JSON 帧体
        } finally {
            buffer.clear(); // 归还 LinkedBuffer 内部块，防止跨请求内存泄漏
        }
    }

    /**
     * 从 Protostuff 字节流反序列化为 RPC POJO。
     *
     * @param data  对端 Netty 通道收到的二进制 body
     * @param clazz 目标 RPC 消息类型
     * @return 反序列化后的对象；空数组或 null 返回 null
     */
    public static <T> T deserialize(byte[] data, Class<T> clazz) {
        if (data == null || data.length == 0) { // 空帧无业务含义，直接跳过
            return null;
        }
        RuntimeSchema<T> schema = RuntimeSchema.createFrom(clazz); // 与 serialize 使用同一 Schema 约定
        T obj = schema.newMessage(); // 分配空消息实例，mergeFrom 向其填充字段
        ProtostuffIOUtil.mergeFrom(data, obj, schema); // 将字节流合并到 POJO，完成跨服消息还原
        return obj;
    }
}
