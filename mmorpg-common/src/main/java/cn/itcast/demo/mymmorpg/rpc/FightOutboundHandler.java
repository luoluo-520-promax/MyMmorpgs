/**
 * 文件说明
 * 模块：mmorpg-common / RPC 通信
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/rpc/FightOutboundHandler.java
 * 类型：类
 * 职责：游戏服连接战斗服后的 Netty 入站处理——握手与战斗转发响应分发。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.rpc;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 游戏服 → 战斗服 RPC 连接的 Netty 入站 Handler。
 * <p>
 * 管道顺序：RpcLengthJsonDecoder → 本 Handler。
 * 首包处理 {@link RpcRespServerLogin} 握手；后续包经 {@link CallBackService} 完成
 * {@link RpcForwardClientResponse} 等异步 RPC 回调。
 * </p>
 */
public class FightOutboundHandler extends SimpleChannelInboundHandler<RpcWireEnvelope> {

    private static final Logger log = LoggerFactory.getLogger(FightOutboundHandler.class);

    /** 将 envelope.body 转为具体 RPC 类型的 JSON 编解码器 */
    private final RpcJsonCodec jsonCodec;

    /** 战斗服 Session 注册表，握手成功后 registerFightSession 供 Router 选服 */
    private final RpcClientRouter router;

    /** 按 requestId 完成 RequestResponseFuture，衔接战斗回包与游戏服业务线程 */
    private final CallBackService callBackService;

    /** 本 TCP 连接对应的战斗服 serverId（配置或目录同步获得） */
    private final int fightServerId;

    /** 是否已完成 RpcRespServerLogin 握手；未完成前非登录包不应进入业务分发 */
    private volatile boolean handshakeOk;

    /**
     * @param jsonCodec        RPC JSON 编解码
     * @param router           战斗服 Session 路由
     * @param callBackService  RPC 异步回调注册表
     * @param fightServerId    对端战斗服 ID
     */
    public FightOutboundHandler(RpcJsonCodec jsonCodec,
                                RpcClientRouter router,
                                CallBackService callBackService,
                                int fightServerId) {
        this.jsonCodec = jsonCodec; // 解析 RpcRespServerLogin、RpcForwardClientResponse 等 body
        this.router = router; // 握手成功后将 Netty Channel 注册为可转发战斗 RPC 的 Session
        this.callBackService = callBackService; // 非握手包走 dispatchEnvelope → complete Future
        this.fightServerId = fightServerId; // 日志与 Session 表 key
    }

    /**
     * 每收到一帧 {@link RpcWireEnvelope} 时调用（已由 RpcLengthJsonDecoder 解码）。
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcWireEnvelope env) {
        if (env.getKind() == null) { // 无 kind 无法路由，丢弃
            return;
        }
        String kind = env.getKind(); // 消息类型简单类名，如 RpcRespServerLogin

        if (!handshakeOk && RpcRespServerLogin.class.getSimpleName().equals(kind)) { // 连接建立后首包应为战斗服登录响应
            RpcRespServerLogin resp = jsonCodec.parseBody(env.getBody(), RpcRespServerLogin.class); // 解析握手结果
            if (resp != null && resp.isSuccess()) { // 战斗服验签通过，RPC 长连接可用
                handshakeOk = true; // 标记握手完成，后续包进入 RPC 业务分发
                NettyIdSession session = new NettyIdSession(ctx.channel(), jsonCodec, fightServerId); // 封装为 IdSession，支持 send(wrap)
                router.registerFightSession(fightServerId, session); // 注册到 Router，游戏服可向该战斗服转发客户端战斗包
                log.info("Fight RPC session ready sid={}", fightServerId); // 记录战斗服 RPC 通道就绪
            } else { // 验签失败或战斗服拒绝注册
                log.warn("Fight RPC handshake rejected sid={}", fightServerId); // 记录拒绝原因便于排查 signKey/serverId
                ctx.close(); // 关闭非法连接，避免未授权节点接入
            }
            return; // 握手包已处理，不进入 CallBackService
        }

        callBackService.dispatchEnvelope(env, jsonCodec); // 战斗转发回包等：按 kind 完成对应 RequestResponseFuture
    }

    /**
     * TCP 连接断开时，从 Router 移除该战斗服 Session，避免向已下线节点发 RPC。
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        router.unregisterSession(fightServerId); // 清理 fightServerId 对应的 NettyIdSession
    }

    /**
     * Pipeline 异常时关闭连接并记录，防止半开连接占用战斗转发资源。
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("Fight outbound handler error sid={}: {}", fightServerId, cause.toString()); // 记录解码/IO 异常
        ctx.close(); // 关闭 Channel，触发 channelInactive 清理 Router
    }
}
