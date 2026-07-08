/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/handler/BattleRpcForwarder.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/handler
 * 3) 主要职责：类 BattleRpcForwarder，承载模块内相关实现。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.handler; // player-service 协议 Facade 与消息 dispatch 管道
import cn.itcast.demo.mymmorpg.rpc.IdSession; // 中心服下发的 FIGHT 节点会话标识（host/port）
import cn.itcast.demo.mymmorpg.rpc.RpcClientRouter; // 从已注册战斗节点中 pickFightNode 负载均衡
import cn.itcast.demo.mymmorpg.rpc.RpcForwardClientMessage; // GAME->FIGHT 转发客户端原始 msgId+payload
import cn.itcast.demo.mymmorpg.rpc.RpcForwardClientResponse; // FIGHT 处理后的回包 msgId+payload
import cn.itcast.demo.mymmorpg.rpc.CallBackService; // RpcClient 异步响应回调注册表
import cn.itcast.demo.mymmorpg.rpc.RpcClient; // 向 FIGHT 节点发 RPC 并 await 响应
import cn.itcast.demo.mymmorpg.rpc.RequestResponseFuture; // 阻塞等待 RPC 响应或超时
import org.springframework.beans.factory.annotation.Value; // game.battle.route-to-fight 跨服转发开关
import org.springframework.stereotype.Component; // MessageDispatchPipeline 注入，仅 GAME 服且 BattleMessage 时触发
/**
 * 战斗消息跨服转发：GAME 服将标记 BattleMessage 的请求经 RPC 转发 FIGHT 服，等待响应后回写 Netty/WebSocket 客户端。
 */
@Component // Spring 单例，MessageDispatchPipeline 在 BattleMessage 路径注入使用
public class BattleRpcForwarder { // GAME 服 BattleMessage 跨服 RPC 桥，替代本地 ClientRequestTask 执行
    /** 战斗节点路由，GameServerLayer.onCenterServerConnected 后拉取 FIGHT 列表 */
    private final RpcClientRouter rpcClientRouter; // pickFightNode 选取可用 FIGHT IdSession
    /** RPC 请求-响应 Future 回调中心 */
    private final CallBackService callBackService; // RpcClient.send 注册 rid 对应 RequestResponseFuture
    /** game.battle.route-to-fight，false 时 BattleMessage 走本地 Facade */
    private final boolean enabled; // 配置开关，false 时 MessageDispatchPipeline 跳过 RPC 直接 ClientRequestTask
    public BattleRpcForwarder(RpcClientRouter rpcClientRouter, // 构造注入 FIGHT RPC 路由与回调
                              CallBackService callBackService, // rid 对应 RequestResponseFuture 注册中心
                              @Value("${game.battle.route-to-fight:false}") boolean enabled) { // game.battle.route-to-fight 控制是否跨服转发
        this.rpcClientRouter = rpcClientRouter; // 持有 FIGHT 节点路由表
        this.callBackService = callBackService; // 持有 RPC 回调注册中心
        this.enabled = enabled; // 记录跨服转发开关
    } // 编译单元结束

    /** MessageDispatchPipeline 判断是否走跨服路径的前置条件之一 */
    public boolean enabled() { // MessageDispatchPipeline 检查 game.battle.route-to-fight
        return enabled; // true 且 GAME 服且 BattleMessage 时才调用 forward
    } // 编译单元结束

    /**
     * 将客户端战斗包转发到 FIGHT 服并同步等待回包。
     *
     * @param playerId   当前玩家，写入 RpcForwardClientMessage 供 FIGHT 侧上下文
     * @param msgId      客户端原始 msgId
     * @param payload    protobuf 原始字节
     * @param timeoutMs  game.dispatch.rpc-timeout-ms，超时后 pipeline 回退本地处理
     */
    public RpcForwardClientResponse forward(long playerId, int msgId, byte[] payload, long timeoutMs) throws InterruptedException { // BattleRpcForwarder.forward：long playerId, int msgId, byte[] payload, long timeoutMs
        IdSession fight = rpcClientRouter.pickFightNode(); // 轮询/随机选取可用战斗节点
        if (fight == null) { // 无 FIGHT 节点注册，MessageDispatchPipeline 回退本地 ClientRequestTask
            return null; // 告知 pipeline RPC 不可用，走本地 Facade 兜底
        } // forward 方法体结束

        RpcClient client = new RpcClient(fight, callBackService); // 绑定目标 FIGHT 会话 TCP/RPC 通道
        long rid = client.nextRequestId(); // 单调递增 RPC 请求序号，匹配响应 Future
        RpcForwardClientMessage req = new RpcForwardClientMessage(rid, playerId, msgId, payload); // 封装 rid+playerId+客户端 msgId+protobuf payload
        RequestResponseFuture<RpcForwardClientResponse> f = client.send(rid, req); // 异步发送 RPC，注册 rid 回调
        return f.await(timeoutMs); // dispatch stripe 业务线程阻塞等待 FIGHT 回包或超时
    } // 编译单元结束
} // 编译单元结束
