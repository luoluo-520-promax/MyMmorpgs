/**
 * 文件说明
 * 模块：mmorpg-common / RPC 通信
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/rpc/RpcLengthJsonDecoder.java
 * 类型：类
 * 职责：Netty 入站解码器，将 [4 字节长度][JSON 字节] 帧还原为 {@link RpcWireEnvelope}。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

/**
 * 跨服 RPC Netty 入站解码器：[length:4][json bytes] → {@link RpcWireEnvelope}。
 * <p>
 * 战斗服/中心服回包或推送目录同步结果时，TCP 字节流经本 Handler 还原为信封，
 * 后续由 {@link FightOutboundHandler}、{@link CallBackService} 等按 kind 分发。
 * </p>
 */
public class RpcLengthJsonDecoder extends ByteToMessageDecoder {

    /** 单帧最大 1MB，防止恶意或异常长度导致 OOM（跨服长连接安全边界） */
    private static final int MAX_FRAME = 1 << 20;

    /** 解码 JSON 为 RpcWireEnvelope 的 Jackson 实例 */
    private final ObjectMapper objectMapper;

    /**
     * @param objectMapper 与编码端一致的 ObjectMapper
     */
    public RpcLengthJsonDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper; // 用于 readValue 还原 kind/body 结构
    }

    /**
     * Netty 入站回调：从 ByteBuf 累积区切出一完整 RPC 帧并解码。
     *
     * @param ctx Netty 通道上下文
     * @param in  可读字节缓冲（可能含半包，需多次 decode 调用）
     * @param out 解码成功后追加 RpcWireEnvelope，传递给下游 Handler
     */
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() < 4) { // 长度头不足 4 字节，等待更多 TCP 数据
            return;
        }
        in.markReaderIndex(); // 标记读指针，半包时回滚避免误消费长度字段
        int length = in.readInt(); // 读取对端 RpcLengthJsonEncoder 写入的 JSON 体长度
        if (length < 0 || length > MAX_FRAME) { // 非法长度直接断连，保护跨服节点
            throw new IllegalStateException("invalid rpc frame length=" + length);
        }
        if (in.readableBytes() < length) { // JSON 体尚未收齐，属于 TCP 半包
            in.resetReaderIndex(); // 回退到 mark 位置，下次有数据再试
            return;
        }
        byte[] json = new byte[length]; // 分配与帧长一致的缓冲区
        in.readBytes(json); // 从 ByteBuf 拷贝完整 JSON 字节
        RpcWireEnvelope envelope = objectMapper.readValue(json, RpcWireEnvelope.class); // 还原跨服 RPC 信封
        out.add(envelope); // 交给 pipeline 下一 Handler（如 FightOutboundHandler）
    }
}
