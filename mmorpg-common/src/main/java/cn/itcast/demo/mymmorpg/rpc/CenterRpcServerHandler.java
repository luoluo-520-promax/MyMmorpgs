/**
 * 中心服 RPC 入站处理器：处理远端服务器的登录握手、战斗服注册与战斗节点目录查询。
 */
package cn.itcast.demo.mymmorpg.rpc;

import cn.itcast.demo.mymmorpg.net.ServerConfig; // 读取 RPC 签名密钥 signKey，校验登录请求
import cn.itcast.demo.mymmorpg.net.ServerType;
import io.netty.channel.ChannelHandlerContext; // Netty 通道上下文，用于写回响应与获取远端地址
import io.netty.channel.SimpleChannelInboundHandler; // 自动释放消息的入站 Handler 基类

import org.slf4j.Logger; // 日志接口

import org.slf4j.LoggerFactory; // 创建 Logger

import org.springframework.util.StringUtils; // 判断战斗服上报的 host 是否有效


/**
 * 中心服侧 RPC 业务 Handler：首包必须是 RpcReqServerLogin，通过后方可处理目录类请求。
 */
public class CenterRpcServerHandler extends SimpleChannelInboundHandler<RpcWireEnvelope> {

    private static final Logger log = LoggerFactory.getLogger(CenterRpcServerHandler.class); // 本 Handler 日志

    /** 服务器配置，含 RPC 签名密钥 */
    private final ServerConfig serverConfig;
    /** JSON 编解码，将 RpcWireEnvelope 与具体 RPC DTO 互转 */
    private final RpcJsonCodec jsonCodec;
    /** 中心服维护的战斗服节点注册表 */
    private final CenterFightRegistry fightRegistry;
    /** 当前连接是否已完成握手（volatile 保证多线程可见性） */
    private volatile boolean handshakeOk;
    /** 对端服务器 ID，断连时用于从注册表注销（volatile 保证可见性） */
    private volatile int peerServerId;

    /**
     * 构造中心服 RPC 入站处理器。
     */
    public CenterRpcServerHandler(ServerConfig serverConfig,
                                  RpcJsonCodec jsonCodec,
                                  CenterFightRegistry fightRegistry) {
        this.serverConfig = serverConfig; // 保存配置，握手时校验 sign
        this.jsonCodec = jsonCodec; // 保存编解码器，解析/封装 RPC 消息体
        this.fightRegistry = fightRegistry; // 保存注册表，战斗服登录成功后登记节点
    }

    @Override
    /**
     * 处理每一条入站 RPC 帧：未握手时只接受登录；握手后处理目录查询等请求。
     */
    protected void channelRead0(ChannelHandlerContext ctx, RpcWireEnvelope env) {
        if (env.getKind() == null) { // 消息类型为空则无法路由，直接丢弃
            return; // 忽略无效帧
        }
        String kind = env.getKind(); // 取出 RPC 消息类名，用于分发逻辑

        if (!handshakeOk) { // 连接尚未完成 RpcReqServerLogin 握手
            if (!RpcReqServerLogin.class.getSimpleName().equals(kind)) { // 首包不是登录请求
                ctx.writeAndFlush(jsonCodec.wrap(new RpcRespServerLogin(false, "handshake required"))); // 拒绝并提示必须先握手
                return; // 不继续处理后续逻辑
            }
            RpcReqServerLogin req = jsonCodec.parseBody(env.getBody(), RpcReqServerLogin.class); // 解析登录请求体
            String key = serverConfig.getRpc().getSignKey(); // 从配置读取 RPC 共享签名密钥
            if (!RpcSignUtil.verify(key, req.getServerId(), req.getSign())) { // 校验 serverId 与 sign 是否匹配
                log.warn("Center RPC handshake failed serverId={}", req.getServerId()); // 记录握手失败的对端 ID
                ctx.writeAndFlush(jsonCodec.wrap(new RpcRespServerLogin(false, "invalid sign"))); // 返回签名无效
                return; // 握手失败，不标记 handshakeOk
            }
            handshakeOk = true; // 标记本连接已通过身份校验
            peerServerId = req.getServerId(); // 记录对端 serverId，断连时用于注销
            if (req.getServerType() == ServerType.FIGHT.getCode()) { // 对端是战斗服
                String ip = resolveHost(req, ctx); // 解析战斗服对外 RPC IP（优先请求体，其次 TCP 远端地址）
                int port = req.getRpcPort() > 0 ? req.getRpcPort() : serverConfig.getRpcPort(); // 优先用战斗服自报端口，否则回退配置
                FightServerNode node = new FightServerNode(req.getServerId(), // 构造战斗服节点描述
                        ServerType.FIGHT.getCode(), ip, port);
                fightRegistry.register(node); // 写入中心服内存注册表，供目录查询与游戏服选路
                log.info("Fight server registered sid={} {}:{}", node.getSid(), ip, port); // 记录战斗服注册成功
            }
            ctx.writeAndFlush(jsonCodec.wrap(new RpcRespServerLogin(true, "ok"))); // 返回握手成功响应
            return; // 本帧已处理完毕
        }

        if (Rpc_G2C_FetchFightServerNodes.class.getSimpleName().equals(kind)) { // 游戏服向中心服拉取战斗节点列表
            RpcC2G_FightServerNodes resp = new RpcC2G_FightServerNodes(fightRegistry.listAll()); // 从注册表快照全部战斗节点
            ctx.writeAndFlush(jsonCodec.wrap(resp)); // 将目录同步响应发回请求方
        }
    }

    /**
     * 解析战斗服对外可达 IP：优先使用登录请求中的 host，否则从 TCP 远端地址截取。
     */
    private String resolveHost(RpcReqServerLogin req, ChannelHandlerContext ctx) {
        if (StringUtils.hasText(req.getHost())) { // 战斗服在登录包里显式上报了 host
            return req.getHost(); // 直接使用上报地址（可能经过 NAT 或内网映射）
        }
        if (ctx.channel().remoteAddress() != null) { // 可从 Netty Channel 获取 TCP 对端地址
            String remote = ctx.channel().remoteAddress().toString(); // 形如 "/192.168.1.10:54321"
            if (remote.startsWith("/")) { // Netty InetSocketAddress 字符串通常以 / 开头
                int colon = remote.indexOf(':'); // 查找 IP 与端口分隔符
                return colon > 1 ? remote.substring(1, colon) : remote.substring(1); // 截取 IP 部分（去掉前导 /）
            }
        }
        return "127.0.0.1"; // 无法解析时回退本地地址，避免注册空 IP
    }

    @Override
    /**
     * 对端 TCP 连接断开时，从战斗服注册表移除对应节点。
     */
    public void channelInactive(ChannelHandlerContext ctx) {
        if (peerServerId > 0) { // 已成功握手并记录了对端 ID
            fightRegistry.unregister(peerServerId); // 从中心目录删除该战斗服，避免游戏服连到已下线节点
            log.info("RPC peer disconnected sid={}", peerServerId); // 记录 RPC 对端断连
        }
    }

    @Override
    /**
     * Pipeline 异常时记录日志并关闭连接，防止半开连接占用资源。
     */
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("Center RPC handler error: {}", cause.toString()); // 记录异常摘要
        ctx.close(); // 关闭出错的 Channel，对端将触发重连逻辑
    }
}
