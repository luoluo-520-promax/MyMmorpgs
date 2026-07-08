/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/handler/MessageIoDispatcher.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/handler
 * 3) 主要职责：类 MessageIoDispatcher，承载模块内相关实现。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.handler; // player-service 协议 Facade 与消息 dispatch 管道
import cn.itcast.demo.mymmorpg.net.GameMessage; // GameMessageDecoder 解码产物：[msgId:4][protobuf bytes]
import cn.itcast.demo.mymmorpg.net.ChannelAttrs; // 断线时读取 PLAYER_ID 做 unbind
import cn.itcast.demo.mymmorpg.service.PlayerPushRegistry; // channelInactive 解除 playerId 推送绑定
import io.netty.channel.ChannelHandler; // @Sharable 标记，同一实例可挂到多个 Channel pipeline
import io.netty.channel.ChannelHandlerContext; // 构造 NettyDispatchSession、异常时 close Channel
import io.netty.channel.SimpleChannelInboundHandler; // 泛型 GameMessage，自动释放 ByteBuf
import org.slf4j.Logger; // 记录 pipeline 异常，便于排查半开连接
import org.slf4j.LoggerFactory; // 按类名创建 SLF4J Logger
import org.springframework.stereotype.Component; // 注入 BaseServer pipeline，与 MessageDispatchPipeline 联动
/**
 * Netty 入站终端 Handler：GameMessageDecoder 之后、GameMessageEncoder 之前，将帧交给 MessageDispatchPipeline。
 */
@Component // Spring 单例，@Sharable 安全复用于所有客户端 TCP 连接
@ChannelHandler.Sharable // 无 per-channel 状态，共享 pipeline 与 playerPushRegistry 引用
public class MessageIoDispatcher extends SimpleChannelInboundHandler<GameMessage> { // Netty pipeline 入站终端，对接 MessageDispatchPipeline
    private static final Logger log = LoggerFactory.getLogger(MessageIoDispatcher.class); // 记录解码/分发异常
    /** 共享分发管道：preHandle + 业务线程 dispatch，WebSocket 侧复用同一实例 */
    private final MessageDispatchPipeline pipeline; // handle(session,msgId,payload) 统一入口
    /** 推送注册表，连接断开时 unbind 避免向僵尸 Channel 推送 */
    private final PlayerPushRegistry playerPushRegistry; // channelInactive 读 PLAYER_ID 后 unbind
    public MessageIoDispatcher(PlayerPushRegistry playerPushRegistry, // 构造注入推送表与 dispatch 管道
                               MessageDispatchPipeline pipeline) { // BaseServer childHandler 注入
        // 历史构造器曾直接注入 dispatchThreadModel/factory，现已收敛到 pipeline 内部
        this.pipeline = pipeline; // Netty 与 WebSocket 共用 MessageDispatchPipeline
        this.playerPushRegistry = playerPushRegistry; // 断线解绑 playerId -> Channel 推送映射
    } // 编译单元结束

    @Override // 实现接口/父类方法
    protected void channelRead0(ChannelHandlerContext ctx, GameMessage msg) { // Netty worker 线程回调，每帧 GameMessage 一次
        DispatchSession session = new NettyDispatchSession(ctx, playerPushRegistry); // Channel 包装为 DispatchSession
        pipeline.handle(session, msg.msgId(), msg.payload()); // IO 线程 preHandle，重逻辑异步到 dispatch stripe
    } // 编译单元结束

    @Override // 实现接口/父类方法
    public void channelInactive(ChannelHandlerContext ctx) throws Exception { // TCP 连接关闭时 Netty 回调
        Long playerId = ctx.channel().attr(ChannelAttrs.PLAYER_ID).get(); // 读取 Channel 上 AuthFacade 写入的玩家 ID
        if (playerId != null && playerId > 0) { // 已选角才注册了 PlayerPushRegistry 绑定
            playerPushRegistry.unbind(playerId); // 清除 playerId -> ChannelHandlerContext 推送映射
        } // 编译单元结束

        super.channelInactive(ctx); // 继续 pipeline 默认断连处理，释放 Channel 资源
    } // 编译单元结束

    @Override // 实现接口/父类方法
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) { // pipeline 下游未捕获异常冒泡到此
        log.warn("MessageIoDispatcher error: {}", cause.toString()); // 记录解码/半包/业务异常摘要
        ctx.close(); // 强制关闭 Channel，配合 IdleStateHandler 回收半开连接
    } // 编译单元结束

    // 具体 preHandle/dispatch/幂等/跨服转发逻辑在 MessageDispatchPipeline 中，Netty 与 WebSocket 共用
} // 编译单元结束
