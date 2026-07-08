/**
 * RPC 客户端路由中心：维护到中心服/各战斗服的 IdSession、战斗节点目录与负载均衡选路。
 */
package cn.itcast.demo.mymmorpg.rpc;

import cn.itcast.demo.mymmorpg.net.GameContext;
import cn.itcast.demo.mymmorpg.net.ServerConfig; // 构造 RpcReqServerLogin 时读取 serverId、signKey、IP
import jforgame.commons.eventbus.EventBus; // 发布 RpcConnectedEvent 等跨模块事件

import org.springframework.stereotype.Component; // Spring 单例，全局共享路由状态


import java.util.ArrayList; // 可变列表，收集在线战斗会话

import java.util.List; // 战斗节点列表、RPC 节点查询返回类型

import java.util.Map; // serverId → IdSession 映射

import java.util.concurrent.ConcurrentHashMap; // 线程安全的会话表，多线程 connect/读写并发访问

import java.util.stream.Collectors; // 按 serverType 过滤节点列表

/**
 * 跨服 RPC 会话路由器：center 会话 + 各战斗服会话 + 中心同步来的 FightServerNode 目录。
 */
@Component // 注册为 Spring Bean，供 CenterRpcClient、FightClusterConnector 等注入
public class RpcClientRouter {

    /** 路由表中中心服会话的固定键，与具体 serverId 字符串键区分 */
    private static final String CENTER_KEY = "center";

    /** serverId（字符串）→ NettyIdSession：含 center 与各已连接战斗服 */
    private final Map<String, IdSession> servers = new ConcurrentHashMap<>();
    /** 中心服推送的战斗服节点目录快照（sid、type、ip、port） */
    private final List<FightServerNode> nodes = new ArrayList<>();
    /** 事件总线，中心会话建立时发布 RpcConnectedEvent */
    private final EventBus eventBus;
    /** 战斗服选路策略，默认轮询 RoundBalanceStrategy */
    private BalanceStrategy balanceStrategy = new RoundBalanceStrategy();

    /**
     * 注入事件总线，用于连接状态变更通知。
     */
    public RpcClientRouter(EventBus eventBus) {
        this.eventBus = eventBus; // 保存 EventBus，setCenterSession 时发布连接事件
    }

    /**
     * 替换负载均衡策略（如改为随机、最少连接等），传入 null 则忽略。
     */
    public void setBalanceStrategy(BalanceStrategy balanceStrategy) {
        if (balanceStrategy != null) { // 防止空策略覆盖默认实现
            this.balanceStrategy = balanceStrategy; // 更新选路算法，影响 pickFightNode
        }
    }

    /**
     * 登记与中心服的 RPC 会话，并发布 RpcConnectedEvent。
     */
    public void setCenterSession(IdSession session) {
        if (session != null) { // 忽略空会话
            servers.put(CENTER_KEY, session); // 以固定键 "center" 存储，getCenterSession 专用
            eventBus.publish(new RpcConnectedEvent(session)); // 通知其它模块：中心 RPC 链路已就绪
        }
    }

    /**
     * 获取当前与中心服建立的 IdSession，未连接时返回 null。
     */
    public IdSession getCenterSession() {
        return servers.get(CENTER_KEY); // 按 center 键读取会话
    }

    /**
     * 战斗服 RPC 连接握手成功后，按 serverId 登记会话供后续跨服调用。
     */
    public void registerFightSession(int serverId, IdSession session) {
        if (session != null) { // 会话有效才写入
            servers.put(String.valueOf(serverId), session); // 键为战斗服 sid 字符串
        }
    }

    /**
     * 移除指定 serverId 的会话；serverId=0 时额外清除 center 会话（中心断连场景）。
     */
    public void unregisterSession(int serverId) {
        servers.remove(String.valueOf(serverId)); // 移除指定战斗服或占位 sid 的会话
        if (serverId == 0) { // 约定 0 表示中心服断连
            servers.remove(CENTER_KEY); // 同时删除 center 键，避免 getCenterSession 返回已关闭 Channel
        }
    }

    /**
     * 按目标 serverId 获取 RPC 会话，不存在或未连接返回 null。
     */
    public IdSession getSession(int targetSid) {
        return servers.get(String.valueOf(targetSid)); // 精确路由到指定战斗服
    }

    /**
     * 选取下一个可用战斗服会话（委托 pickFightNode）。
     */
    public IdSession nextFightSession() {
        return pickFightNode(); // 对外别名，语义为“取下一个战斗 RPC 会话”
    }

    /**
     * 从目录中的战斗节点里筛选已建立且 Active 的会话，再经负载均衡策略选一个。
     */
    public IdSession pickFightNode() {
        List<IdSession> fightSessions = new ArrayList<>(); // 收集当前在线的战斗 RPC 会话
        for (FightServerNode node : nodes) { // 遍历中心同步来的战斗节点目录
            IdSession session = servers.get(String.valueOf(node.getSid())); // 查该 sid 是否已有 TCP 会话
            if (session != null && session.isActive()) { // 会话存在且 Channel 仍活跃
                fightSessions.add(session); // 加入候选列表
            }
        }
        return balanceStrategy.pick(fightSessions); // 轮询或其它策略返回一个战斗会话
    }

    /**
     * 按 serverType 过滤本地缓存的战斗节点列表（如只查 FIGHT 类型）。
     */
    public List<FightServerNode> listRpcNodes(int serverType) {
        return nodes.stream()
                .filter(n -> n.getType() == serverType) // 保留类型匹配的节点
                .collect(Collectors.toList()); // 转为不可变结果列表返回
    }

    /**
     * 用中心服推送的最新战斗节点列表全量替换本地目录快照。
     */
    public void updateFightNodes(List<FightServerNode> fightNodes) {
        nodes.clear(); // 清空旧目录，避免已下线节点残留
        if (fightNodes != null) { // 中心可能推送空列表
            nodes.addAll(fightNodes); // 写入新目录，供 connectAll 与 pickFightNode 使用
        }
    }

    /**
     * 返回战斗节点目录的副本，避免外部直接修改内部 List。
     */
    public List<FightServerNode> getNodes() {
        return new ArrayList<>(nodes); // 防御性拷贝
    }

    /**
     * 占位方法：实际重连逻辑由 CenterRpcClient.checkAndRegisterConnections 实现，此处供统一调度接口调用。
     */
    public void checkAndRegisterConnections() {
        // 由 CenterRpcClient 执行实际到中心服的重连与注册逻辑
    }

    /**
     * 构造带签名的 RpcReqServerLogin，供连接中心或战斗服时作为首包发送。
     */
    public RpcReqServerLogin buildLoginRequest(ServerConfig serverConfig) {
        int sid = serverConfig.getServerId(); // 本进程 serverId
        String signKey = serverConfig.getRpc().getSignKey(); // RPC 共享密钥
        RpcReqServerLogin req = new RpcReqServerLogin(sid, RpcSignUtil.sign(sid, signKey)); // 构造登录请求并计算 sign
        req.setServerType(GameContext.serverType.getCode()); // 告知对端本进程是 GAME 还是 FIGHT
        req.setHost(serverConfig.getServerIp()); // 上报本机对外 IP，供中心注册战斗节点时使用
        req.setRpcPort(serverConfig.getRpcPort()); // 上报本机 RPC 监听端口
        return req; // 返回完整登录包，由 Handler channelActive 或 connect 回调发送
    }
}
