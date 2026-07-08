/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/net/GameMessageDecoder.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/net
 * 3) 主要职责：Netty pipeline 入站解码，ByteBuf 转 GameMessage(msgId,payload)。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.net; // player-service Netty/WebSocket 网络层与 YAML 配置加载
import io.netty.buffer.ByteBuf; // LengthFieldBasedFrameDecoder 剥长度后的单帧字节视图
import io.netty.channel.ChannelHandlerContext; // Netty 通道上下文，decode 回调参数
import io.netty.handler.codec.MessageToMessageDecoder; // 一帧 ByteBuf 可解码为 0/1 个 GameMessage
import java.util.List; // MessageToMessageDecoder 解码结果 out 列表，传给 MessageIoDispatcher
/**
 * Netty pipeline 入站解码：LengthFieldBasedFrameDecoder 已剥长度前缀后的单帧 ByteBuf。
 * 读出 msgId(4) + protobuf payload，组装 GameMessage 传给 MessageIoDispatcher。
 */
public class GameMessageDecoder extends MessageToMessageDecoder<ByteBuf> { // BaseServer pipeline 入站，LengthField 之后
    @Override // 实现接口/父类方法
    protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) { // worker 线程每完整帧调用一次
        if (msg.readableBytes() < 4) { // 不足 4 字节无法读 msgId
            return; // 帧不完整，等待更多 TCP 数据，本 decode 不产生下游消息
        } // 编译单元结束

        int msgId = msg.readInt(); // 大端读 4 字节 msgId，GameMessageFactory 路由键
        byte[] payload = new byte[msg.readableBytes()]; // 剩余字节为 protobuf CsReq 体
        msg.readBytes(payload); // 拷贝 Netty 可读区到 heap byte[]
        out.add(new GameMessage(msgId, payload)); // 入 out 列表，pipeline 传给 MessageIoDispatcher.channelRead0
    } // 编译单元结束
} // 编译单元结束
