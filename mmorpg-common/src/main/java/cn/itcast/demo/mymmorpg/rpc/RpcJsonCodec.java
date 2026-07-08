/**
 * 文件说明
 * 模块：mmorpg-common / RPC 通信
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/rpc/RpcJsonCodec.java
 * 类型：类
 * 职责：将 Java RPC 消息对象与 {@link RpcWireEnvelope} 互转，供 Netty 长度帧编解码器使用。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

/**
 * 跨服 RPC 的 JSON 编解码辅助类。
 * <p>
 * 游戏服/战斗服/中心服之间通过 {@link RpcWireEnvelope} 携带消息类型名（kind）与 JSON 体（body），
 * 本类负责在发送前包装信封、在接收后按目标类型反序列化 body。
 * </p>
 */
@Component
public class RpcJsonCodec {

    /** Spring 注入的 Jackson 实例，与 Netty 编解码器共用同一套 JSON 配置 */
    private final ObjectMapper objectMapper;

    /**
     * @param objectMapper 全局 ObjectMapper，保证 RPC 序列化规则与 HTTP/配置一致
     */
    public RpcJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper; // 保存引用，供 parseBody 做类型转换
    }

    /**
     * 将任意 RPC 消息对象包装为线上传输用的 {@link RpcWireEnvelope}。
     *
     * @param message 待发送的 RPC 请求/响应 POJO（如 {@link RpcForwardClientMessage}）
     * @return kind 为类简单名、body 为原对象的信封；null 消息返回空信封
     */
    public RpcWireEnvelope wrap(Object message) {
        if (message == null) { // 空消息无法推断 kind，返回占位信封避免 NPE
            return new RpcWireEnvelope(null, null);
        }
        // kind 使用简单类名，对端 RpcLengthJsonDecoder 解码后由 CallBackService 等按字符串匹配分发
        return new RpcWireEnvelope(message.getClass().getSimpleName(), message);
    }

    /**
     * 从信封 body 反序列化为具体 RPC 类型。
     *
     * @param body 解码后的 body（通常为 LinkedHashMap 或已映射对象）
     * @param type 目标 Java 类型（如 RpcForwardClientResponse.class）
     * @return 转换后的 POJO；body 为 null 时返回 null
     */
    public <T> T parseBody(Object body, Class<T> type) {
        if (body == null) { // 无载荷则无需反序列化
            return null;
        }
        return objectMapper.convertValue(body, type); // Jackson 将 JSON 结构映射为强类型 RPC 对象
    }
}
