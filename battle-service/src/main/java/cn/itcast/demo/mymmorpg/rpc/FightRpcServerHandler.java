/**
 * 文件说明：战斗服 RPC 入站消息处理器。
 * 职责：处理跨服握手（ServerLogin 签名校验）及战斗消息转发（将游戏服转发的客户端战斗请求交给 BattleService）。
 */
package cn.itcast.demo.mymmorpg.rpc;

import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleActionCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleEndCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleStartCsReq;
import cn.itcast.demo.mymmorpg.service.BattleService; // 战斗业务
import cn.itcast.demo.mymmorpg.rpc.RpcForwardClientMessage; // RPC：游戏服转发客户端消息的请求体
import cn.itcast.demo.mymmorpg.rpc.RpcForwardClientResponse; // RPC：转发后的响应体
import cn.itcast.demo.mymmorpg.rpc.RpcReqServerLogin; // RPC：跨服握手登录请求
import cn.itcast.demo.mymmorpg.rpc.RpcRespServerLogin; // RPC：握手响应
import cn.itcast.demo.mymmorpg.rpc.RpcJsonCodec; // 将 Java 对象包装为 RpcWireEnvelope
import cn.itcast.demo.mymmorpg.rpc.RpcSignUtil; // 签名校验工具
import cn.itcast.demo.mymmorpg.rpc.RpcWireEnvelope; // 线上传输的统一 RPC 信封（kind + body）
import cn.itcast.demo.mymmorpg.net.ServerConfig; // 读取 RPC 签名密钥
import cn.itcast.demo.mymmorpg.protocol.MessageId; // 游戏协议消息 ID
import io.netty.channel.ChannelHandlerContext; // Netty 通道上下文
import io.netty.channel.SimpleChannelInboundHandler; // 入站消息已解码为指定类型后的简单处理器
import org.slf4j.Logger; // 日志接口
import org.slf4j.LoggerFactory; // 日志工厂

/**
 * 战斗服侧入站连接：仅校验跨服握手（游戏服等节点连入本进程 RPC 端口）。
 */
public class FightRpcServerHandler extends SimpleChannelInboundHandler<RpcWireEnvelope> { // 每条 RPC 连接一个 Handler 实例

    /** 类级别日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(FightRpcServerHandler.class); // 日志

    /** 服务器配置（含 RPC 签名密钥） */
    private final ServerConfig serverConfig; // 读取 signKey 等
    /** RPC JSON 编解码器 */
    private final RpcJsonCodec jsonCodec; // 序列化响应
    /** 战斗核心业务服务 */
    private final BattleService battleService; // 执行战斗逻辑

    /** 是否已完成跨服握手（未完成则拒绝业务消息） */
    private volatile boolean handshakeOk; // 握手标志

    /**
     * 构造器注入依赖。
     *
     * @param serverConfig  服务器配置
     * @param jsonCodec     JSON 编解码器
     * @param battleService 战斗服务
     */
    public FightRpcServerHandler(ServerConfig serverConfig, RpcJsonCodec jsonCodec, BattleService battleService) {
        this.serverConfig = serverConfig; // 保存配置
        this.jsonCodec = jsonCodec; // 保存编解码器
        this.battleService = battleService; // 保存战斗服务
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcWireEnvelope env) throws Exception { // 每收到一条解码后的 RPC 消息
        if (env.getKind() == null) { // 无消息类型则忽略
            return; // 直接返回
        }
        String kind = env.getKind(); // 消息类型名（与 Java 类 simpleName 对应）
        if (!handshakeOk) { // 尚未握手
            if (!RpcReqServerLogin.class.getSimpleName().equals(kind)) { // 第一条必须是 ServerLogin
                ctx.writeAndFlush(jsonCodec.wrap(new RpcRespServerLogin(false, "handshake required"))); // 拒绝并提示
                return; // 结束处理
            }
            RpcReqServerLogin req = jsonCodec.parseBody(env.getBody(), RpcReqServerLogin.class); // 解析握手请求体
            String key = serverConfig.getRpc().getSignKey(); // 配置的共享密钥
            if (!RpcSignUtil.verify(key, req.getServerId(), req.getSign())) { // 校验 HMAC/签名防伪造节点
                log.warn("Fight RPC handshake failed serverId={}", req.getServerId()); // 记录握手失败
                ctx.writeAndFlush(jsonCodec.wrap(new RpcRespServerLogin(false, "invalid sign"))); // 返回签名无效
                return; // 结束处理
            }
            handshakeOk = true; // 标记握手成功
            ctx.writeAndFlush(jsonCodec.wrap(new RpcRespServerLogin(true, "ok"))); // 返回成功
            return; // 结束处理
        }

        if (RpcForwardClientMessage.class.getSimpleName().equals(kind)) { // 握手后的业务：转发客户端战斗消息
            RpcForwardClientMessage req = jsonCodec.parseBody(env.getBody(), RpcForwardClientMessage.class); // 解析转发请求
            RpcForwardClientResponse resp = handleForward(req); // 调用战斗服务
            ctx.writeAndFlush(jsonCodec.wrap(resp)); // 写回 RPC 响应
        }
    }

    /**
     * 根据 msgId 分发到 BattleService 处理战斗请求。
     *
     * @param req 转发请求
     * @return RPC 转发响应
     */
    private RpcForwardClientResponse handleForward(RpcForwardClientMessage req) { // 战斗消息转发
        long pid = req.getPlayerId(); // 玩家 ID（由游戏服带上）
        int msgId = req.getMsgId(); // 原始客户端协议消息 ID
        byte[] payload = req.getPayload() != null ? req.getPayload() : new byte[0]; // Protobuf 载荷，空则字节数组长度为 0
        try { // 捕获解析与业务异常
            if (msgId == MessageId.BATTLE_START_CS_REQ) { // 开始战斗
                var out = battleService.handleBattleStart(pid, BattleStartCsReq.parseFrom(payload)); // 解析并处理
                return new RpcForwardClientResponse(req.getRequestId(), out.msgId(), out.payload()); // 带回请求 ID 与响应 msgId/payload
            }
            if (msgId == MessageId.BATTLE_ACTION_CS_REQ) { // 战斗行动
                var out = battleService.handleBattleAction(pid, BattleActionCsReq.parseFrom(payload)); // 解析并处理
                return new RpcForwardClientResponse(req.getRequestId(), out.msgId(), out.payload()); // 返回响应
            }
            if (msgId == MessageId.BATTLE_END_CS_REQ) { // 战斗结束
                var out = battleService.handleBattleEnd(pid, BattleEndCsReq.parseFrom(payload)); // 解析并处理
                return new RpcForwardClientResponse(req.getRequestId(), out.msgId(), out.payload()); // 返回响应
            }
            return new RpcForwardClientResponse(req.getRequestId(), 0, new byte[0]); // 未知 msgId：空响应
        } catch (Exception e) { // 处理异常
            log.warn("Fight RPC forward failed msgId={} playerId={} err={}", msgId, pid, e.toString()); // 记录警告
            return new RpcForwardClientResponse(req.getRequestId(), 0, new byte[0]); // 异常也不抛到 Netty，避免断连风险
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) { // Pipeline 未捕获的异常
        log.warn("Fight RPC handler error: {}", cause.toString()); // 记录异常
        ctx.close(); // 关闭异常连接
    }
}
