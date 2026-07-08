/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/net/BaseServer.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/net
 * 3) 主要职责：类 BaseServer，承载模块内相关实现。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.net; // player-service Netty/WebSocket 网络层与 YAML 配置加载
import cn.itcast.demo.mymmorpg.net.ServerConfig; // YAML 加载的 server.port/server.ip，优先于 fallback
import cn.itcast.demo.mymmorpg.handler.MessageIoDispatcher; // pipeline 终端：GameMessage -> MessageDispatchPipeline
import io.netty.bootstrap.ServerBootstrap; // Netty 服务端启动模板
import io.netty.channel.ChannelFuture; // bind 异步结果，stop 时 close Channel
import io.netty.channel.ChannelInitializer; // 每连接初始化 pipeline Handler 链
import io.netty.channel.EventLoopGroup; // boss/worker 事件循环线程组
import io.netty.channel.nio.NioEventLoopGroup; // NIO 多路复用实现
import io.netty.channel.socket.SocketChannel; // 客户端 TCP 连接类型
import io.netty.channel.socket.nio.NioServerSocketChannel; // 服务端 ServerSocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder; // 按 [length:4] 拆粘包，与 GameMessageEncoder 对称
import io.netty.handler.timeout.IdleStateHandler; // 读空闲检测，触发 NettyIdleCloseHandler
import jakarta.annotation.PostConstruct; // Spring 就绪后启动 Netty 监听
import jakarta.annotation.PreDestroy; // 容器关闭时优雅 stop
import org.slf4j.Logger; // SLF4J 日志接口
import org.slf4j.LoggerFactory; // 按类名创建 SLF4J Logger
import org.springframework.beans.factory.annotation.Value; // game.netty.host/port/read-idle-seconds
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // game.netty.enabled 开关
import org.springframework.context.annotation.DependsOn; // 等待 serverLayer/gameContext 初始化
import org.springframework.stereotype.Component; // Spring Bean，@PostConstruct 启动 Netty
import org.springframework.util.StringUtils; // 判断 ServerConfig IP 是否非空
import java.util.concurrent.TimeUnit; // IdleStateHandler 与 shutdownGracefully 时间单位
/**
 * Netty TCP 游戏端口：pipeline 顺序 IdleState -> IdleClose -> LengthFieldFrame -> Decoder -> Dispatcher -> Encoder。
 * <p>端口优先 ServerConfig.getServerPort()，为 0 时回退 game.netty.port。</p>
 */
@Component // Spring 管理生命周期，@PostConstruct bind / @PreDestroy stop
@ConditionalOnProperty(name = "game.netty.enabled", havingValue = "true", matchIfMissing = true) // 默认开启 Netty TCP 游戏端口
@DependsOn({"serverLayer", "gameContext"}) // GameContext.serverType 与 serverLayer 就绪后再 bind
public class BaseServer { // Netty NIO TCP 服务端，客户端 protobuf 帧入口
    private static final Logger log = LoggerFactory.getLogger(BaseServer.class); // 记录 bind/stop 端口与异常
    @Value("${game.netty.host:0.0.0.0}") // bind 地址，0.0.0.0 监听所有网卡
    private String fallbackHost; // ServerConfig IP 无效时使用
    @Value("${game.netty.port:8089}") // ServerConfig 端口为 0 时的备用 TCP 端口
    private int fallbackPort; // 默认 8089 游戏 TCP 端口
    /** 读空闲秒数，超时无入站字节触发 READER_IDLE -> NettyIdleCloseHandler.close */
    @Value("${game.netty.read-idle-seconds:60}") // 读空闲秒数，触发 NettyIdleCloseHandler
    private int readIdleSeconds; // IdleStateHandler 第一个参数
    /** config/server.yml 等加载的进程级端口/IP */
    private final ServerConfig serverConfig; // YAML http.port/server.ip 优先于 fallback
    /** @Sharable 单例，挂到每个 SocketChannel pipeline 末端前 */
    private final MessageIoDispatcher messageIoDispatcher; // 入站 GameMessage -> MessageDispatchPipeline
    /** accept 线程组，通常 1 线程 */
    private EventLoopGroup bossGroup; // 接受新 TCP 连接
    /** IO 读写线程组，默认 2*CPU */
    private EventLoopGroup workerGroup; // 已连接 Channel 的 pipeline 读写
    /** bind 成功后的 ChannelFuture，stop 时关闭 ServerChannel */
    private ChannelFuture bindFuture; // 监听端口 Channel，stop 时 close
    public BaseServer(ServerConfig serverConfig, MessageIoDispatcher messageIoDispatcher) { // Spring 构造注入
        this.serverConfig = serverConfig; // 游戏 YAML 端口/IP
        this.messageIoDispatcher = messageIoDispatcher; // @Sharable 共享 dispatcher
    } // 编译单元结束

    @PostConstruct // 容器就绪后执行初始化
    public void start() { // Spring 容器就绪后 bind TCP 端口
        int port = serverConfig.getServerPort() > 0 ? serverConfig.getServerPort() : fallbackPort; // YAML http.port 或 game.netty.port
        String host = StringUtils.hasText(serverConfig.getServerIp()) ? serverConfig.getServerIp() : fallbackHost; // ServerConfig IP 优先
        bossGroup = new NioEventLoopGroup(1); // 单 boss 线程 accept
        workerGroup = new NioEventLoopGroup(); // 默认 CPU*2 worker 处理 I/O
        try { // 代码块开始
            ServerBootstrap b = new ServerBootstrap(); // Netty 服务端启动器
            b.group(bossGroup, workerGroup) // 绑定 boss/worker EventLoopGroup
                    .channel(NioServerSocketChannel.class) // NIO 服务端 Channel 类型
                    .childHandler(new ChannelInitializer<SocketChannel>() { // 每新 TCP 连接初始化 pipeline
                        @Override // 实现接口/父类方法
                        protected void initChannel(SocketChannel ch) { // 装配 Netty 入站/出站 Handler 链
                            // pipeline 入站顺序：空闲检测 -> 空闲关闭 -> 拆包 -> protobuf 解码 -> 业务分发
                            ch.pipeline().addLast(new IdleStateHandler(readIdleSeconds, 0, 0)); // 仅读空闲，超时 READER_IDLE
                            ch.pipeline().addLast(new NettyIdleCloseHandler()); // READER_IDLE 时 ctx.close()
                            // maxFrame=1MB；offset=0 len=4 表示前4字节为长度；不调整读索引；跳过长度字段本身4字节
                            ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(1 << 20, 0, 4, 0, 4)); // [length:4][msgId:4][payload] 拆粘包
                            ch.pipeline().addLast(new GameMessageDecoder()); // ByteBuf 单帧 -> GameMessage(msgId,payload)
                            ch.pipeline().addLast(messageIoDispatcher); // GameMessage -> MessageDispatchPipeline
                            // 出站：业务 writeAndFlush(GameMessage) 经 Encoder 加长度前缀
                            ch.pipeline().addLast(new GameMessageEncoder()); // GameMessage -> [length][msgId][payload] ByteBuf
                        } // 块 代码块结束
                    }); // BaseServer 逻辑
            bindFuture = b.bind(host, port).sync(); // 阻塞至 bind 完成
            log.info("BaseServer (Netty) listening {}:{}", host, port); // 启动成功日志
        } catch (InterruptedException e) { // bind 被中断
            Thread.currentThread().interrupt(); // 恢复中断标志
            throw new IllegalStateException("BaseServer start interrupted", e); // 启动失败抛异常
        } catch (Exception e) { // bind 端口占用等
            shutdownQuietly(); // 释放已创建的 EventLoopGroup
            throw e; // 向上抛出让 Spring 启动失败
        } // 编译单元结束
    } // 编译单元结束

    @PreDestroy // 容器关闭前释放资源
    public void stop() { // Spring 容器关闭时优雅停止 Netty
        try { // 代码块开始
            if (bindFuture != null && bindFuture.channel() != null) { // 已 bind 成功
                bindFuture.channel().close().syncUninterruptibly(); // 关闭监听 ServerChannel
            } // 块 代码块结束
        } finally { // 代码块开始
            shutdownQuietly(); // 无论 close 是否成功都释放线程组
        } // 块 代码块结束

        log.info("BaseServer (Netty) stopped"); // 停止完成日志
    } // 编译单元结束

    /** 优雅关闭 boss/worker EventLoopGroup */
    private void shutdownQuietly() { // start 失败或 @PreDestroy 调用
        if (bossGroup != null) { // boss 已创建
            bossGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS); // 0s 静默期后最多 5s 优雅关闭
        } // 编译单元结束

        if (workerGroup != null) { // worker 已创建
            workerGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS); // 等待在途 Channel I/O 完成
        } // 编译单元结束
    } // 编译单元结束
} // 编译单元结束
