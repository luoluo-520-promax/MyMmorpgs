/**
 * 游戏服战斗集群连接器：根据中心同步的 FightServerNode 目录，逐个建立到战斗服的 RPC TCP 连接。
 */
package cn.itcast.demo.mymmorpg.rpc;

import cn.itcast.demo.mymmorpg.net.GameContext; // 仅 GAME 进程需要连战斗服集群
import cn.itcast.demo.mymmorpg.net.ServerConfig; // 本机配置，connect 成功后发送 RpcReqServerLogin
import cn.itcast.demo.mymmorpg.net.ServerType; // 判断 serverType == GAME
import com.fasterxml.jackson.databind.ObjectMapper; // RPC Pipeline JSON 编解码
import io.netty.bootstrap.Bootstrap; // Netty 客户端 Bootstrap
import io.netty.channel.ChannelFuture; // 异步 connect 结果与写登录包
import io.netty.channel.ChannelInitializer; // 每个战斗服连接独立的 Pipeline
import io.netty.channel.EventLoopGroup; // 客户端 IO 线程组
import io.netty.channel.nio.NioEventLoopGroup; // NIO EventLoopGroup
import io.netty.channel.socket.SocketChannel; // 到战斗服的 TCP 通道
import io.netty.channel.socket.nio.NioSocketChannel; // NIO 客户端 Channel 类型

import jakarta.annotation.PostConstruct; // GAME 进程启动时创建 workerGroup

import jakarta.annotation.PreDestroy; // 关闭时释放线程组

import org.slf4j.Logger; // 日志

import org.slf4j.LoggerFactory; // Logger 工厂

import org.springframework.context.annotation.DependsOn; // 依赖 GameContext

import org.springframework.stereotype.Component; // Spring Bean

import org.springframework.util.StringUtils; // 校验节点 ip 非空


import java.util.HashSet; // 记录正在 connect 的 sid，防重复建连

import java.util.Set; // connecting 集合类型

import java.util.concurrent.TimeUnit; // shutdownGracefully 超时

/**
 * 游戏服侧战斗 RPC 连接管理：目录更新后 connectAll，每个节点独立 Bootstrap + FightOutboundHandler。
 */
@Component // Spring 管理的战斗集群连接器
@DependsOn("gameContext") // 确保 serverType 已设置
public class FightClusterConnector {

    private static final Logger log = LoggerFactory.getLogger(FightClusterConnector.class); // 本类日志

    /** 本游戏服网络与 RPC 配置 */
    private final ServerConfig serverConfig;
    /** RPC JSON 编解码 */
    private final RpcJsonCodec jsonCodec;
    /** Jackson，传给 RpcLengthJson 编解码器 */
    private final ObjectMapper objectMapper;
    /** 路由表：connect 成功后 registerFightSession，选路 pickFightNode */
    private final RpcClientRouter router;
    /** 战斗服 RPC 异步回包分发 */
    private final CallBackService callBackService;
    /** 连接各战斗服共用的 Netty 客户端 IO 线程组 */
    private EventLoopGroup workerGroup;
    /** 当前正在异步 connect 的战斗服 sid 集合，避免同一 sid 并发重复连接 */
    private final Set<Integer> connecting = new HashSet<>();

    /**
     * 构造战斗集群连接器。
     */
    public FightClusterConnector(ServerConfig serverConfig,
                                 RpcJsonCodec jsonCodec,
                                 ObjectMapper objectMapper,
                                 RpcClientRouter router,
                                 CallBackService callBackService) {
        this.serverConfig = serverConfig; // 保存配置，连接成功后 buildLoginRequest
        this.jsonCodec = jsonCodec; // 保存编解码器
        this.objectMapper = objectMapper; // 保存 ObjectMapper
        this.router = router; // 保存路由，读取 getNodes/getSession
        this.callBackService = callBackService; // 保存回调，传给 FightOutboundHandler
    }

    @PostConstruct // Spring 初始化后执行
    /**
     * 仅在 GAME 进程创建客户端 EventLoopGroup。
     */
    public void init() {
        if (GameContext.serverType != ServerType.GAME) { // 战斗服/中心服不需要连战斗集群
            return; // 跳过 workerGroup 创建
        }
        workerGroup = new NioEventLoopGroup(); // 创建共享的客户端 IO 线程池，所有战斗服连接复用
    }

    /**
     * 遍历路由表中的战斗节点目录，对每个节点尝试建立 RPC 连接（已连接或连接中会跳过）。
     */
    public void connectAll() {
        if (GameContext.serverType != ServerType.GAME) { // 非游戏服不连战斗服
            return; // 直接返回
        }
        for (FightServerNode node : router.getNodes()) { // 遍历中心推送的战斗服列表
            connectNode(node); // 对每个节点发起独立 connect（内部去重）
        }
    }

    /**
     * 对单个战斗服节点发起 Netty 客户端连接，成功后发送 RpcReqServerLogin 完成战斗侧握手。
     */
    private void connectNode(FightServerNode node) {
        if (node == null || !StringUtils.hasText(node.getIp()) || node.getPort() <= 0) { // 节点数据不完整
            return; // 无法 connect，跳过
        }
        IdSession existing = router.getSession(node.getSid()); // 查是否已有该 sid 的会话
        if (existing != null && existing.isActive()) { // 已存在活跃连接
            return; // 无需重复连接
        }
        if (connecting.contains(node.getSid())) { // 该 sid 正在 connect 中
            return; // 避免并发重复发起
        }
        connecting.add(node.getSid()); // 标记 sid 进入连接中状态
        Bootstrap bootstrap = new Bootstrap(); // 为本节点创建客户端 Bootstrap（可复用 group）
        bootstrap.group(workerGroup) // 使用共享 workerGroup
                .channel(NioSocketChannel.class) // NIO 客户端 Socket
                .handler(new ChannelInitializer<SocketChannel>() { // 连接成功后装配 Pipeline
                    @Override
                    /**
                     * 为到战斗服的连接添加编解码器与 FightOutboundHandler（登记会话、处理回包）。
                     */
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new RpcLengthJsonDecoder(objectMapper)); // 入站解码 RPC 帧
                        ch.pipeline().addLast(new RpcLengthJsonEncoder(objectMapper)); // 出站编码 RPC
                        ch.pipeline().addLast(new FightOutboundHandler( // 战斗服侧客户端业务 Handler
                                jsonCodec, router, callBackService, node.getSid())); // 传入目标战斗服 sid 用于 registerFightSession  加入 Pipeline 末尾
                    }
                });
        ChannelFuture future = bootstrap.connect(node.getIp(), node.getPort()); // 异步连接战斗服 RPC 地址
        future.addListener(f -> { // connect 完成回调
            connecting.remove(node.getSid()); // 无论成败都从 connecting 集合移除，允许后续重试
            if (f.isSuccess()) { // TCP 连接成功
                RpcReqServerLogin login = router.buildLoginRequest(serverConfig); // 构造本游戏服登录包（带 sign）
                future.channel().writeAndFlush(jsonCodec.wrap(login)); // 发送首包，战斗服 RPC 服务端验签并登记
                log.info("Fight cluster connect sid={} {}:{}", node.getSid(), node.getIp(), node.getPort()); // 记录连接成功
            } else { // 连接失败（战斗服未启、网络问题等）
                log.warn("Fight cluster connect failed sid={} {}:{} err={}", // 记录失败，等待下次 connectAll 重试
                        node.getSid(), node.getIp(), node.getPort(), f.cause().toString());
            }
        });
    }

    @PreDestroy // 应用关闭
    /**
     * 优雅关闭客户端 EventLoopGroup。
     */
    public void stop() {
        if (workerGroup != null) { // workerGroup 已创建
            workerGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS); // 关闭 IO 线程，最多等 5 秒
        }
    }
}
