/**
 * 连接中心服后的客户端入站 Handler：自动握手、登记 centerSession、同步战斗目录、分发 RPC 回调。
 */
package cn.itcast.demo.mymmorpg.rpc;

import cn.itcast.demo.mymmorpg.net.GameContext; // 反射获取 serverLayer Bean，通知中心已连通
import cn.itcast.demo.mymmorpg.net.ServerConfig; // 本机 serverId 与 RPC 配置
import cn.itcast.demo.mymmorpg.net.ServerType; // 进程类型（本文件未直接使用，与整体 RPC 架构一致）
import io.netty.channel.ChannelHandlerContext; // 写登录包、关闭连接
import io.netty.channel.SimpleChannelInboundHandler; // 入站 RpcWireEnvelope 自动释放

import org.slf4j.Logger; // 日志

import org.slf4j.LoggerFactory; // Logger 工厂


/**
 * 非中心服侧 RPC 入站处理器：channelActive 发登录，channelRead0 处理握手/目录/异步响应。
 */
public class CenterRpcClientHandler extends SimpleChannelInboundHandler<RpcWireEnvelope> {

    private static final Logger log = LoggerFactory.getLogger(CenterRpcClientHandler.class); // 本 Handler 日志

    /** 本机服务器配置 */
    private final ServerConfig serverConfig;
    /** RPC JSON 编解码 */
    private final RpcJsonCodec jsonCodec;
    /** 跨服会话与战斗节点路由 */
    private final RpcClientRouter router;
    /** 异步 RPC 响应回调分发 */
    private final CallBackService callBackService;
    /** 游戏服连接各战斗服的集群连接器 */
    private final FightClusterConnector fightClusterConnector;
    /** 是否已完成与中心服的 RpcRespServerLogin 握手 */
    private volatile boolean handshakeOk;

    /**
     * 构造中心 RPC 客户端入站处理器。
     */
    public CenterRpcClientHandler(ServerConfig serverConfig,
                                  RpcJsonCodec jsonCodec,
                                  RpcClientRouter router,
                                  CallBackService callBackService,
                                  FightClusterConnector fightClusterConnector) {
        this.serverConfig = serverConfig; // 保存配置，握手成功后包装 NettyIdSession
        this.jsonCodec = jsonCodec; // 保存编解码器
        this.router = router; // 保存路由，登记 centerSession 与更新战斗节点
        this.callBackService = callBackService; // 保存回调服务，非握手/目录消息交给它
        this.fightClusterConnector = fightClusterConnector; // 保存战斗连接器，目录更新后 connectAll
    }

    @Override
    /**
     * TCP 连接建立后立即向中心服发送 RpcReqServerLogin 完成身份握手。
     */
    public void channelActive(ChannelHandlerContext ctx) {
        RpcReqServerLogin login = router.buildLoginRequest(serverConfig); // 构造带 sign 的登录请求（含 serverId、type、host、rpcPort）
        ctx.writeAndFlush(jsonCodec.wrap(login)); // 编码并发送首包，触发中心服 CenterRpcServerHandler 校验
    }

    @Override
    /**
     * 处理中心服下发的 RPC 帧：握手响应、战斗目录推送、或其它 RPC 异步回包。
     */
    protected void channelRead0(ChannelHandlerContext ctx, RpcWireEnvelope env) {
        if (env.getKind() == null) { // 无效消息类型
            return; // 丢弃
        }
        String kind = env.getKind(); // RPC 消息类名，用于分支分发

        if (!handshakeOk && RpcRespServerLogin.class.getSimpleName().equals(kind)) { // 尚未握手且收到登录响应
            RpcRespServerLogin resp = jsonCodec.parseBody(env.getBody(), RpcRespServerLogin.class); // 解析握手结果
            if (resp != null && resp.isSuccess()) { // 中心服验签通过
                handshakeOk = true; // 标记握手完成，后续帧走业务/目录/回调逻辑
                NettyIdSession session = new NettyIdSession(ctx.channel(), jsonCodec, serverConfig.getServerId()); // 封装可发送 RPC 的会话对象
                router.setCenterSession(session); // 写入路由表 key=center，并发布 RpcConnectedEvent
                notifyServerLayerConnected(); // 通知业务层 ServerLayer 中心已连通（若存在该 Bean）
                log.info("Center RPC connected serverId={}", serverConfig.getServerId()); // 记录本机与中心 RPC 链路就绪
            } else { // 握手被拒绝（签名错误等）
                log.warn("Center RPC handshake rejected: {}", resp != null ? resp.getMessage() : "null"); // 记录拒绝原因
                ctx.close(); // 关闭连接，由 CenterRpcClient 重连逻辑稍后重试
            }
            return; // 登录响应帧处理结束
        }

        if (RpcC2G_FightServerNodes.class.getSimpleName().equals(kind)) { // 中心服推送战斗服节点目录（Center→Game）
            RpcC2G_FightServerNodes nodes = jsonCodec.parseBody(env.getBody(), RpcC2G_FightServerNodes.class); // 解析节点列表
            if (nodes != null) { // 解析成功
                router.updateFightNodes(nodes.getNodes()); // 更新本地战斗节点快照，供负载均衡选路
                fightClusterConnector.connectAll(); // 游戏服按新目录逐个 connect 战斗服 RPC
            }
            return; // 目录帧处理结束
        }

        callBackService.dispatchEnvelope(env, jsonCodec); // 其余 RPC 帧视为异步请求的回包，按 kind/correlationId 分发给等待方
    }

    /**
     * 通过反射调用 ServerLayer.onCenterServerConnected()，让业务层在中心连通后拉目录或初始化逻辑。
     */
    private void notifyServerLayerConnected() {
        try { // 反射调用可能因模块未引入 ServerLayer 而失败，需捕获
            Object layer = GameContext.getBean("serverLayer", Object.class); // 从 Spring 容器取 serverLayer Bean（若存在）
            if (layer != null) { // Bean 存在才回调
                layer.getClass().getMethod("onCenterServerConnected").invoke(layer); // 无参方法反射调用，通知业务层
            }
        } catch (Exception e) { // 无 ServerLayer 或方法不存在时忽略
            log.debug("ServerLayer onCenterServerConnected skipped: {}", e.toString()); // 调试级别记录跳过原因
        }
    }

    @Override
    /**
     * 与中心服连接断开时清除路由表中的 centerSession 并重置握手状态。
     */
    public void channelInactive(ChannelHandlerContext ctx) {
        router.unregisterSession(0); // serverId=0 表示清除 center 键及对应会话
        handshakeOk = false; // 重连后需重新走 channelActive 登录流程
    }

    @Override
    /**
     * 处理 Pipeline 异常：打日志并关闭 Channel，触发重连。
     */
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("Center RPC client handler error: {}", cause.toString()); // 记录异常
        ctx.close(); // 关闭出错连接
    }
}
