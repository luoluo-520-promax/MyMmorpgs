/**
 * 非中心服 RPC 客户端：游戏服/战斗服主动连接中心服 rpc 端口，维持目录同步与跨服调用链路。
 */
package cn.itcast.demo.mymmorpg.rpc;

import cn.itcast.demo.mymmorpg.net.GameContext; // 判断当前进程类型，CENTRE 不启动客户端
import cn.itcast.demo.mymmorpg.net.ServerConfig; // 读取中心服 IP/端口及本机 RPC 配置
import cn.itcast.demo.mymmorpg.net.ServerType; // 区分 CENTRE / GAME / FIGHT
import com.fasterxml.jackson.databind.ObjectMapper; // 供 RPC 长度前缀 JSON 编解码
import io.netty.bootstrap.Bootstrap; // Netty 客户端连接引导
import io.netty.channel.Channel; // 与中心服建立的 TCP 连接通道
import io.netty.channel.ChannelFuture; // 异步 connect 结果
import io.netty.channel.ChannelInitializer; // 连接成功后初始化 Pipeline
import io.netty.channel.EventLoopGroup; // 客户端 IO 线程组
import io.netty.channel.nio.NioEventLoopGroup; // NIO 实现
import io.netty.channel.socket.SocketChannel; // 客户端 Socket 通道
import io.netty.channel.socket.nio.NioSocketChannel; // NIO 客户端 Channel 类型

import jakarta.annotation.PostConstruct; // 容器就绪后初始化 worker 并尝试连接中心

import jakarta.annotation.PreDestroy; // 销毁时关闭 Channel 与线程组

import org.slf4j.Logger; // 日志

import org.slf4j.LoggerFactory; // Logger 工厂

import org.springframework.context.annotation.DependsOn; // 确保 GameContext 先于本 Bean 初始化

import org.springframework.stereotype.Component; // Spring 管理的 RPC 客户端 Bean


import java.util.concurrent.TimeUnit; // shutdownGracefully 超时单位

/**
 * 中心服 RPC  outbound 连接器：GAME/FIGHT 进程通过它连上 CENTRE，接收战斗目录推送。
 */
@Component // 注册为 Spring Bean
@DependsOn("gameContext") // 依赖 GameContext，serverType 赋值后再连接
public class CenterRpcClient {

    private static final Logger log = LoggerFactory.getLogger(CenterRpcClient.class); // 本类日志

    /** 本机与中心服的网络配置 */
    private final ServerConfig serverConfig;
    /** RPC JSON 包装工具 */
    private final RpcJsonCodec jsonCodec;
    /** Jackson 序列化器，传给 Pipeline 编解码器 */
    private final ObjectMapper objectMapper;
    /** 跨服会话路由表，连接成功后登记 centerSession */
    private final RpcClientRouter router;
    /** 异步 RPC 回包分发器 */
    private final CallBackService callBackService;
    /** 游戏服侧战斗集群连接器，收到目录后触发 connectAll */
    private final FightClusterConnector fightClusterConnector;
    /** 客户端 Netty IO 线程组 */
    private EventLoopGroup workerGroup;
    /** 与中心服建立的 Channel 引用（volatile 供其他线程可见） */
    private volatile Channel channel;
    /** 是否已有 connect 在进行中，防止并发重复建连 */
    private volatile boolean connectInFlight;

    /**
     * 构造中心 RPC 客户端，注入路由、回调与战斗集群连接器。
     */
    public CenterRpcClient(ServerConfig serverConfig,
                           RpcJsonCodec jsonCodec,
                           ObjectMapper objectMapper,
                           RpcClientRouter router,
                           CallBackService callBackService,
                           FightClusterConnector fightClusterConnector) {
        this.serverConfig = serverConfig; // 保存配置，读取 center.ip/port
        this.jsonCodec = jsonCodec; // 保存编解码器，供 Handler 使用
        this.objectMapper = objectMapper; // 保存 ObjectMapper，装配 Pipeline
        this.router = router; // 保存路由，连接成功后注册 centerSession
        this.callBackService = callBackService; // 保存回调服务，Handler 分发异步响应
        this.fightClusterConnector = fightClusterConnector; // 保存战斗连接器，目录更新后连战斗服
    }

    @PostConstruct // Spring 初始化完成后执行
    /**
     * 创建客户端 EventLoopGroup 并首次尝试连接中心服。
     */
    public void init() {
        if (GameContext.serverType == ServerType.CENTRE) { // 中心服自身不需要连中心
            return; // 跳过客户端初始化
        }
        workerGroup = new NioEventLoopGroup(); // 创建客户端 IO 线程池
        checkAndRegisterConnections(); // 立即检查并发起到中心服的连接
    }

    /**
     * 检查中心会话是否存活，若未连接则异步 connect 中心 RPC 端口（可被定时任务重复调用）。
     */
    public void checkAndRegisterConnections() {
        if (GameContext.serverType == ServerType.CENTRE) { // 中心服不执行客户端重连
            return; // 直接返回
        }
        IdSession center = router.getCenterSession(); // 从路由表取已登记的中心服会话
        if (center != null && center.isActive()) { // 已有有效连接
            return; // 无需重复建连
        }
        if (connectInFlight) { // 已有 connect 在进行
            return; // 避免并发重复发起连接
        }
        int centerPort = serverConfig.getRpc().getCenter().getPort(); // 读取配置中的中心 RPC 端口
        if (centerPort <= 0) { // 未配置中心端口
            return; // 无法连接，静默跳过
        }
        connectInFlight = true; // 标记连接进行中，防止重入
        String host = serverConfig.getRpc().getCenter().getIp(); // 读取中心服 RPC IP
        Bootstrap bootstrap = new Bootstrap(); // 创建 Netty 客户端 Bootstrap
        bootstrap.group(workerGroup) // 绑定客户端 EventLoopGroup
                .channel(NioSocketChannel.class) // 使用 NIO 客户端 Socket
                .handler(new ChannelInitializer<SocketChannel>() { // 连接建立后配置 Pipeline
                    @Override
                    /**
                     * 装配 RPC 编解码器与中心客户端 Handler（握手、目录、异步回包）。
                     */
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new RpcLengthJsonDecoder(objectMapper)); // 入站解码长度前缀 JSON
                        ch.pipeline().addLast(new RpcLengthJsonEncoder(objectMapper)); // 出站编码为长度前缀 JSON
                        ch.pipeline().addLast(new CenterRpcClientHandler( // 入站业务：登录响应、战斗目录、RPC 回调
                                serverConfig, jsonCodec, router, callBackService, fightClusterConnector));
                    }
                });
        ChannelFuture future = bootstrap.connect(host, centerPort); // 异步连接中心 RPC 地址
        future.addListener(f -> { // 连接完成回调（成功或失败）
            connectInFlight = false; // 无论成败都清除进行中标志，允许下次重试
            if (f.isSuccess()) { // TCP 三次握手成功
                channel = future.channel(); // 保存 Channel 引用供 stop 时关闭
                log.info("Connecting to center RPC {}:{}", host, centerPort); // 记录已连上中心（后续 Handler 会发登录包）
            } else { // 连接失败（网络不通、中心未启动等）
                log.warn("Center RPC connect failed {}:{} err={}", host, centerPort, f.cause().toString()); // 记录失败原因，等待下次重试
            }
        });
    }

    @PreDestroy // 应用关闭时释放资源
    /**
     * 关闭与中心服的 Channel 并 shutdown 客户端 EventLoopGroup。
     */
    public void stop() {
        if (channel != null) { // Channel 曾建立过
            channel.close(); // 关闭 TCP 连接
        }
        if (workerGroup != null) { // 线程组已创建
            workerGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS); // 优雅关闭 IO 线程
        }
    }
}
