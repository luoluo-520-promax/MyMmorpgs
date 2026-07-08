/**
 * 中心服 RPC 服务端：在中心进程上监听 rpc.port，接受战斗服/游戏服的注册与目录查询。
 */
package cn.itcast.demo.mymmorpg.rpc;

import cn.itcast.demo.mymmorpg.net.GameContext; // 全局进程上下文，用于判断当前是否为 CENTRE 中心服
import cn.itcast.demo.mymmorpg.net.ServerConfig; // 读取 rpc.port、serverIp 等网络配置
import cn.itcast.demo.mymmorpg.net.ServerType; // 服务器类型枚举，区分 CENTRE / GAME / FIGHT
import com.fasterxml.jackson.databind.ObjectMapper; // Jackson 序列化器，供 RPC 编解码器解析 JSON 帧
import io.netty.bootstrap.ServerBootstrap; // Netty 服务端启动器，绑定端口并接收连接
import io.netty.channel.ChannelFuture; // 异步绑定/关闭操作的 Future，便于优雅停机
import io.netty.channel.ChannelInitializer; // 每个新连接建立时初始化 Pipeline 的模板
import io.netty.channel.EventLoopGroup; // Netty 事件循环线程组（boss 接连接、worker 处理 IO）
import io.netty.channel.nio.NioEventLoopGroup; // 基于 NIO 的 EventLoopGroup 实现
import io.netty.channel.socket.SocketChannel; // 已 accept 的 TCP 客户端连接通道
import io.netty.channel.socket.nio.NioServerSocketChannel; // NIO 服务端监听通道类型

import jakarta.annotation.PostConstruct; // Spring 容器就绪后自动调用 start() 启动 RPC 监听

import jakarta.annotation.PreDestroy; // 容器销毁前自动调用 stop() 释放 Netty 资源

import org.slf4j.Logger; // 日志接口

import org.slf4j.LoggerFactory; // 按类名创建 Logger 实例

import org.springframework.context.annotation.DependsOn; // 确保 gameContext 先初始化，serverType 已就绪

import org.springframework.stereotype.Component; // 注册为 Spring Bean，由容器管理生命周期

import org.springframework.util.StringUtils; // 判断 serverIp 是否配置了有效文本


import java.util.concurrent.TimeUnit; // shutdownGracefully 的超时时间单位

/**
 * 中心服 RPC 监听组件：仅在 CENTRE 进程启动 Netty 服务端，维护战斗服注册表入口。
 */
@Component // 作为 Spring 单例 Bean 注入到中心服进程
@DependsOn("gameContext") // 依赖 GameContext Bean，保证 serverType 在 start 前已赋值
public class CenterRpcServer {

    private static final Logger log = LoggerFactory.getLogger(CenterRpcServer.class); // 本类专用日志记录器

    /** 服务器网络与 RPC 相关配置（端口、IP、签名密钥等） */
    private final ServerConfig serverConfig;
    /** RPC 消息 JSON 包装/解析工具，将业务对象转为 RpcWireEnvelope */
    private final RpcJsonCodec jsonCodec;
    /** Jackson ObjectMapper，传递给长度前缀 JSON 编解码器 */
    private final ObjectMapper objectMapper;
    /** 中心服内存中的战斗服节点注册表，握手成功时写入、断连时移除 */
    private final CenterFightRegistry fightRegistry;
    /** Boss 线程组：仅负责 accept 新 TCP 连接，通常 1 个线程即可 */
    private EventLoopGroup bossGroup;
    /** Worker 线程组：负责已建立连接的读写与 Handler 回调 */
    private EventLoopGroup workerGroup;
    /** bind 操作的 Future，stop 时用于关闭监听 Channel */
    private ChannelFuture bindFuture;

    /**
     * 构造中心 RPC 服务端，注入配置、编解码器与战斗服注册表。
     */
    public CenterRpcServer(ServerConfig serverConfig,
                           RpcJsonCodec jsonCodec,
                           ObjectMapper objectMapper,
                           CenterFightRegistry fightRegistry) {
        this.serverConfig = serverConfig; // 保存配置，start 时读取 rpc.port 与监听地址
        this.jsonCodec = jsonCodec; // 保存编解码器，供 Handler 构造时使用
        this.objectMapper = objectMapper; // 保存 ObjectMapper，供 Pipeline 编解码器使用
        this.fightRegistry = fightRegistry; // 保存注册表，战斗服握手成功后登记节点
    }

    @PostConstruct // Spring 初始化完成后自动启动 RPC 监听（若当前为 CENTRE）
    /**
     * 在中心服进程上绑定 rpc.port，配置 Netty Pipeline 并阻塞等待 bind 完成。
     */
    public void start() {
        if (GameContext.serverType != ServerType.CENTRE) { // 非中心服进程无需启动 RPC 服务端
            return; // 直接返回，避免 GAME/FIGHT 进程占用 rpc.port
        }
        int port = serverConfig.getRpcPort(); // 从配置读取中心服对外 RPC 监听端口
        if (port <= 0) { // 端口未配置或非法时跳过启动
            log.debug("rpc.port<=0, skip CenterRpcServer"); // 调试日志：说明未启用中心 RPC 监听
            return; // 不创建 Netty 资源
        }
        String host = StringUtils.hasText(serverConfig.getServerIp()) ? serverConfig.getServerIp() : "0.0.0.0"; // 有配置则用指定 IP，否则监听所有网卡
        bossGroup = new NioEventLoopGroup(1); // 创建 1 线程的 boss 组，专门 accept 连接
        workerGroup = new NioEventLoopGroup(); // 创建默认大小的 worker 组，处理 IO 与业务 Handler
        try { // 捕获 bind 过程中的任意异常，失败时释放已创建的资源
            ServerBootstrap bootstrap = new ServerBootstrap(); // 创建 Netty 服务端启动引导对象
            bootstrap.group(bossGroup, workerGroup) // 绑定 boss/worker 线程组到 Bootstrap
                    .channel(NioServerSocketChannel.class) // 使用 NIO 模式的服务端 Socket 通道
                    .childHandler(new ChannelInitializer<SocketChannel>() { // 每个新客户端连接初始化 Pipeline
                        @Override
                        /**
                         * 为新接入的 RPC 客户端（战斗服/游戏服）装配编解码器与业务 Handler。
                         */
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new RpcLengthJsonDecoder(objectMapper)); // 入站：先按长度前缀解码 JSON 为 RpcWireEnvelope
                            ch.pipeline().addLast(new RpcLengthJsonEncoder(objectMapper)); // 出站：将 RpcWireEnvelope 编码为长度前缀 JSON 帧
                            ch.pipeline().addLast(new CenterRpcServerHandler(serverConfig, jsonCodec, fightRegistry)); // 入站业务：握手、注册、目录查询
                        }
                    });
            bindFuture = bootstrap.bind(host, port).sync(); // 同步等待 bind 完成，记录 Future 供停机使用
            log.info("CenterRpcServer listening {}:{}", host, port); // 记录中心 RPC 已成功监听的地址与端口
        } catch (Exception e) { // bind 或 sync 失败
            shutdownQuietly(); // 释放已创建的 EventLoopGroup，避免线程泄漏
            throw new IllegalStateException("CenterRpcServer start failed", e); // 向上抛出，使 Spring 启动失败以便排查
        }
    }

    @PreDestroy // 应用关闭时优雅停止 RPC 服务端
    /**
     * 关闭监听 Channel 并释放 boss/worker 线程组。
     */
    public void stop() {
        try { // 尝试关闭已 bind 的服务端 Channel
            if (bindFuture != null && bindFuture.channel() != null) { // bind 成功过才需要关闭监听套接字
                bindFuture.channel().close().syncUninterruptibly(); // 同步关闭，中断时不抛异常
            }
        } finally { // 无论 close 是否成功都执行资源回收
            shutdownQuietly(); // 关闭 EventLoopGroup
        }
        log.info("CenterRpcServer stopped"); // 记录中心 RPC 服务端已停止
    }

    /**
     * 静默关闭 boss/worker 线程组，忽略关闭过程中的中断。
     */
    private void shutdownQuietly() {
        if (bossGroup != null) { // boss 组已创建才关闭
            bossGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS); // 优雅关闭，最多等待 5 秒
        }
        if (workerGroup != null) { // worker 组已创建才关闭
            workerGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS); // 优雅关闭 worker 线程
        }
    }
}
