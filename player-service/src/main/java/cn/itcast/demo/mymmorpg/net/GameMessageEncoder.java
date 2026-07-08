/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/net/GameMessageEncoder.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/net
 * 3) 主要职责：类 GameMessageEncoder，承载模块内相关实现。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.net; // player-service Netty/WebSocket 网络层与 YAML 配置加载
import io.netty.buffer.ByteBuf; // 出站二进制缓冲区，经 SocketChannel 写回客户端
import io.netty.channel.ChannelHandlerContext; // encode 上下文，与 pipeline 出站方向绑定
import io.netty.handler.codec.MessageToByteEncoder; // 入参 GameMessage，产出带长度前缀的完整 TCP 帧
/**
 * Netty 出站编码：GameMessage -> [length:4][msgId:4][protobuf payload]，与 WebSocket BinaryFrameSender 帧格式对齐。
 * <p>length = 4 + payload.length，表示 msgId 与 protobuf 内容区总字节数（不含 length 自身）。</p>
 */
public class GameMessageEncoder extends MessageToByteEncoder<GameMessage> { // BaseServer pipeline 出站，NettyDispatchSession.send 触发
    @Override // 实现接口/父类方法
    protected void encode(ChannelHandlerContext ctx, GameMessage msg, ByteBuf out) { // writeAndFlush(GameMessage) 时调用
        byte[] payload = msg.payload(); // Facade ProtocolMessage.payload，protobuf ScRsp 序列化字节
        int frameContentLength = 4 + payload.length; // 内容区 = msgId(4) + protobuf 体
        out.writeInt(frameContentLength); // 写入长度前缀，对端 LengthFieldBasedFrameDecoder 据此拆包
        out.writeInt(msg.msgId()); // 写入 ScRsp msgId，客户端路由解析器
        out.writeBytes(payload); // 写入 protobuf 业务体字节
    } // 编译单元结束
} // 编译单元结束
