/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/net/NettyIdleCloseHandler.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/net
 * 3) 主要职责：类 NettyIdleCloseHandler，承载模块内相关实现。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.net; // player-service Netty/WebSocket 网络层与 YAML 配置加载
import io.netty.channel.ChannelHandlerContext; // 读空闲时 ctx.close() 关闭半开连接
import io.netty.channel.ChannelInboundHandlerAdapter; // 监听 userEventTriggered 接收 IdleStateEvent
import io.netty.handler.timeout.IdleState; // READER_IDLE：readIdleSeconds 内无入站数据
import io.netty.handler.timeout.IdleStateEvent; // IdleStateHandler 触发的用户事件
import org.slf4j.Logger; // debug 记录被关闭的对端地址
import org.slf4j.LoggerFactory; // 按类名创建 SLF4J Logger
/**
 * Netty 读空闲关闭：BaseServer pipeline 中 IdleStateHandler(readIdleSeconds,0,0) 之后挂载，超时无数据则断开。
 * <p>与 WsSessionIdleReaper 对应，分别处理 TCP 与 WebSocket 半开连接回收。</p>
 */
public class NettyIdleCloseHandler extends ChannelInboundHandlerAdapter { // BaseServer pipeline IdleStateHandler 之后
    private static final Logger log = LoggerFactory.getLogger(NettyIdleCloseHandler.class); // debug 记录 idle 关闭
    @Override // 实现接口/父类方法
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception { // IdleStateHandler 触发用户事件
        if (evt instanceof IdleStateEvent e && e.state() == IdleState.READER_IDLE) { // readIdleSeconds 内无入站字节
            log.debug("Netty connection idle timeout, close remote={}", ctx.channel().remoteAddress()); // 记录对端地址便于排查
            ctx.close(); // 关闭 Channel，触发 MessageIoDispatcher.channelInactive -> unbind
            return; // 已处理 READER_IDLE，不再向下传递
        } // 编译单元结束

        super.userEventTriggered(ctx, evt); // WRITER_IDLE/ALL_IDLE 等事件继续 pipeline 传递
    } // 编译单元结束
} // 编译单元结束
