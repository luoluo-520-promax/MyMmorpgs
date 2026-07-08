/**
 * 文件说明
 * 模块：mmorpg-common / RPC 通信
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/rpc/RpcLengthJsonEncoder.java
 * 类型：类
 * 职责：Netty 出站编码器，将 {@link RpcWireEnvelope} 转为 [4 字节长度][JSON 字节] 帧。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * 跨服 RPC Netty 出站编码器：{@link RpcWireEnvelope} → [length:4][json bytes]。
 * <p>
 * 游戏服向战斗服/中心服发送握手、转发客户端战斗包等消息时，
 * 业务层先 {@link RpcJsonCodec#wrap(Object)}，再由本 Handler 写入 TCP 通道。
 * </p>
 */
public class RpcLengthJsonEncoder extends MessageToByteEncoder<RpcWireEnvelope> {

    /** 与 RpcJsonCodec 共用 ObjectMapper，保证 kind/body JSON 格式一致 */
    private final ObjectMapper objectMapper;

    /**
     * @param objectMapper Spring 容器中的 Jackson 实例
     */
    public RpcLengthJsonEncoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper; // 编码阶段将信封整体序列化为 JSON 字节数组
    }

    /**
     * Netty 出站回调：把 RPC 信封编码为带长度前缀的二进制帧。
     *
     * @param ctx Netty 通道上下文（游戏服→战斗服/中心服的出站连接）
     * @param msg 已包装的 {@link RpcWireEnvelope}（含 kind 与 body）
     * @param out 待写入的 ByteBuf，最终会 flush 到对端
     */
    @Override
    protected void encode(ChannelHandlerContext ctx, RpcWireEnvelope msg, ByteBuf out) throws Exception {
        byte[] json = objectMapper.writeValueAsBytes(msg); // 整包 JSON：{"kind":"...","body":{...}}
        out.writeInt(json.length); // 先写 4 字节大端长度，对端 RpcLengthJsonDecoder 据此切帧
        out.writeBytes(json); // 再写 JSON 载荷，完成一次跨服 RPC 报文发送
    }
}
