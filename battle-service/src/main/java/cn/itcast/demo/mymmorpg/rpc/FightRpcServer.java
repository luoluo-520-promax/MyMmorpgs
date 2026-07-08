/**
 * 文件说明：战斗服 RPC 服务端。
 * 职责：在 FIGHT 类型进程上监听 rpc.port，供游戏服等节点建立跨服 RPC 连接（握手 + 战斗消息转发）。
 */
package cn.itcast.demo.mymmorpg.rpc;

import cn.itcast.demo.mymmorpg.service.BattleService; // 战斗逻辑（RPC 转发最终调用它）
import cn.itcast.demo.mymmorpg.rpc.RpcLengthJsonDecoder; // Netty 入站：长度前缀 + JSON 解码
import cn.itcast.demo.mymmorpg.rpc.RpcLengthJsonEncoder; // Netty 出站：长度前缀 + JSON 编码
import cn.itcast.demo.mymmorpg.rpc.RpcJsonCodec; // RPC 消息与 JSON 编解码工具
import cn.itcast.demo.mymmorpg.net.GameContext; // 全局游戏上下文（含当前进程服务器类型）
import cn.itcast.demo.mymmorpg.net.ServerConfig; // 服务器配置（IP、RPC 端口等）
import cn.itcast.demo.mymmorpg.net.ServerType; // 服务器类型枚举（GAME、FIGHT 等）
import com.fasterxml.jackson.databind.ObjectMapper; // JSON 映射
import io.netty.bootstrap.ServerBootstrap; // Netty 服务端启动器
import io.netty.channel.ChannelFuture; // 绑定端口的异步结果
import io.netty.channel.ChannelInitializer; // 新连接 Pipeline 初始化
import io.netty.channel.EventLoopGroup; // Netty 事件循环组
import io.netty.channel.nio.NioEventLoopGroup; // NIO 实现的事件循环组
import io.netty.channel.socket.SocketChannel; // TCP 客户端连接通道
import io.netty.channel.socket.nio.NioServerSocketChannel; // NIO 服务端 ServerSocket
import jakarta.annotation.PostConstruct; // Bean 初始化完成后回调
import jakarta.annotation.PreDestroy; // Bean 销毁前回调
import org.slf4j.Logger; // 日志接口
import org.slf4j.LoggerFactory; // 日志工厂
import org.springframework.context.annotation.DependsOn; // 声明依赖其他 Bean 先创建
import org.springframework.stereotype.Component; // Spring 组件
import org.springframework.util.StringUtils; // 字符串工具（判断 IP 是否为空）

import java.util.concurrent.TimeUnit; // 时间单位（优雅关闭线程池）

/**
 * 战斗服监听 {@code rpc.port}，供游戏服等节点建立跨服 RPC（握手）。
 */
@Component // Spring 管理生命周期
@DependsOn("gameContext") // 确保 GameContext 已初始化再启动 RPC（需要 serverType）
public class FightRpcServer { // 仅在 FIGHT 类型进程上监听 RPC 端口

    /** 类级别日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(FightRpcServer.class); // 日志

    /** 服务器配置 */
    private final ServerConfig serverConfig; // 服务器配置
    /** RPC JSON 编解码器 */
    private final RpcJsonCodec jsonCodec; // RPC JSON 包装
    /** JSON 对象映射器 */
    private final ObjectMapper objectMapper; // Netty 编解码用
    /** 战斗核心业务服务 */
    private final BattleService battleService; // 转发战斗请求时使用

    /** 接受连接的 boss 线程组 */
    private EventLoopGroup bossGroup; // 接受连接的线程组（通常 1 线程）
    /** 处理 IO 的 worker 线程组 */
    private EventLoopGroup workerGroup; // 处理 IO 的线程组
    /** bind 结果，用于关闭时 release */
    private ChannelFuture bindFuture; // 绑定结果

    /**
     * 构造器注入依赖。
     *
     * @param serverConfig  服务器配置
     * @param jsonCodec     RPC JSON 编解码器
     * @param objectMapper  JSON 映射器
     * @param battleService 战斗服务
     */
    public FightRpcServer(ServerConfig serverConfig, RpcJsonCodec jsonCodec, ObjectMapper objectMapper, BattleService battleService) {
        this.serverConfig = serverConfig; // 保存配置
        this.jsonCodec = jsonCodec; // 保存编解码器
        this.objectMapper = objectMapper; // 保存 JSON 映射器
        this.battleService = battleService; // 保存战斗服务
    }

    /**
     * Spring 容器就绪后自动启动 RPC 监听。
     */
    @PostConstruct // Spring 容器就绪后自动调用
    public void start() { // 启动 RPC 服务
        if (GameContext.serverType != ServerType.FIGHT) { // 非战斗服进程不启动 RPC 监听
            return; // 直接返回
        }
        int port = serverConfig.getRpcPort(); // 从配置读取 RPC 端口
        if (port <= 0) { // 端口无效则跳过
            log.debug("rpc.port<=0, skip FightRpcServer"); // 记录调试日志
            return; // 不启动
        }
        String host = StringUtils.hasText(serverConfig.getServerIp()) ? serverConfig.getServerIp() : "0.0.0.0"; // 绑定地址，默认全网卡
        bossGroup = new NioEventLoopGroup(1); // 1 个 boss 线程接受连接
        workerGroup = new NioEventLoopGroup(); // 默认 worker 线程数（CPU*2）
        try { // 启动 Netty 服务
            ServerBootstrap b = new ServerBootstrap(); // 创建 Netty 服务端引导类
            b.group(bossGroup, workerGroup) // 设置线程组
                    .channel(NioServerSocketChannel.class) // 使用 NIO 服务端 Channel 实现
                    .childHandler(new ChannelInitializer<SocketChannel>() { // 每个新客户端连接初始化 Pipeline
                        @Override
                        protected void initChannel(SocketChannel ch) { // 为 Channel（每条连接）配置处理器链
                            ch.pipeline().addLast(new RpcLengthJsonDecoder(objectMapper)); // 入站：按长度拆包并 JSON 解码
                            ch.pipeline().addLast(new RpcLengthJsonEncoder(objectMapper)); // 出站：JSON 编码并加长度前缀
                            ch.pipeline().addLast(new FightRpcServerHandler(serverConfig, jsonCodec, battleService)); // 业务：握手与战斗转发
                        }
                    });
            bindFuture = b.bind(host, port).sync(); // 同步等待绑定完成
            log.info("FightRpcServer listening {}:{}", host, port); // 打印监听地址
        } catch (Exception e) { // 启动失败
            shutdownQuietly(); // 启动失败则释放已创建的线程组
            throw new IllegalStateException("FightRpcServer start failed", e); // 向上抛错，Spring 启动失败
        }
    }

    /**
     * 应用关闭时停止 RPC 服务。
     */
    @PreDestroy // 应用关闭时调用
    public void stop() { // 停止 RPC 服务
        try { // 关闭监听 Channel
            if (bindFuture != null && bindFuture.channel() != null) { // 若已成功 bind
                bindFuture.channel().close().syncUninterruptibly(); // 关闭监听 Channel（不响应中断）
            }
        } finally { // 无论如何
            shutdownQuietly(); // 关闭线程组
        }
        log.info("FightRpcServer stopped"); // 记录停止日志
    }

    /**
     * 优雅关闭 Netty 线程组。
     */
    private void shutdownQuietly() { // 优雅关闭
        if (bossGroup != null) { // boss 组存在
            bossGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS); // 静默期 0s，最多等 5s
        }
        if (workerGroup != null) { // worker 组存在
            workerGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS); // 同上
        }
    }
}
