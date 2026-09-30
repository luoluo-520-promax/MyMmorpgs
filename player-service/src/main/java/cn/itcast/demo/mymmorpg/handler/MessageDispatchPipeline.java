/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/handler/MessageDispatchPipeline.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/handler
 * 3) 主要职责：类 MessageDispatchPipeline，承载模块内相关实现。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.handler; // player-service 协议 Facade 与消息 dispatch 管道
import cn.itcast.demo.mymmorpg.handler.GameMessageFactory; // msgId 路由表与 BattleMessage 标记
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage; // RPC 回包与幂等 replay 的统一结构
import cn.itcast.demo.mymmorpg.rpc.RpcForwardClientResponse; // FIGHT 服 RPC 转发响应 msgId+payload
import cn.itcast.demo.mymmorpg.support.DispatchFailureReporter;
import cn.itcast.demo.mymmorpg.net.GameContext; // 静态 serverType，区分 GAME/CENTRE/FIGHT/GATE
import cn.itcast.demo.mymmorpg.net.ServerType; // GAME 服才启用 BattleRpcForwarder 跨服路径
import org.slf4j.Logger; // debug 级别记录未知 msgId 丢弃
import org.slf4j.LoggerFactory; // 按类名创建 SLF4J Logger
import org.springframework.beans.factory.annotation.Value; // game.dispatch.rpc-timeout-ms RPC 等待上限
import org.springframework.stereotype.Component; // Netty MessageIoDispatcher 与 WebSocket Handler 共用
/**
 * 可复用消息分发管道：Netty TCP 与 WebSocket 二进制帧经同一 preHandle/dispatch/幂等/跨服逻辑。
 */
@Component // Spring 单例，Netty MessageIoDispatcher 与 PlayerBinaryWebSocketHandler 注入
public class MessageDispatchPipeline extends ChainedMessageDispatcher { // Netty/WebSocket 共用 preHandle+dispatch 实现
    private static final Logger log = LoggerFactory.getLogger(MessageDispatchPipeline.class); // 记录未知 msgId 丢弃
    /** 按 playerId 分片提交 ClientRequestTask，保证同玩家消息串行 */
    private final DispatchThreadModel dispatchThreadModel; // dispatchKey 映射 stripe 单线程池
    /** 启动扫描 @MessageRoute/@RequestHandler 注册的 msgId 路由表 */
    private final GameMessageFactory factory; // preHandle 查路由、ClientRequestTask 调 Facade
    /** GAME 服 BattleMessage 跨服转发 FIGHT 的可选组件 */
    private final BattleRpcForwarder battleRpcForwarder; // enabled() 且 GAME 服时 RPC 转发
    /** 窗口内重复包 replay，跳过 Facade 重复执行 */
    private final IdempotencyService idempotencyService; // tryReplay/record TCP/WebSocket 重传去重
    /** RPC 转发 FIGHT 的最大等待毫秒，超时回退本地 ClientRequestTask */
    private final long rpcTimeoutMs; // game.dispatch.rpc-timeout-ms
    /** 故障指标上报 */
    private final DispatchFailureReporter failureReporter;

    public MessageDispatchPipeline(DispatchThreadModel dispatchThreadModel, // 构造注入 dispatch 分片与路由依赖
                                   GameMessageFactory factory, // msgId -> Invoker 路由表
                                   BattleRpcForwarder battleRpcForwarder, // BattleMessage 跨服 RPC 转发 FIGHT
                                   IdempotencyService idempotencyService, // TCP/WebSocket 重传 replay/record
                                   DispatchFailureReporter failureReporter,
                                   @Value("${game.dispatch.rpc-timeout-ms:1500}") long rpcTimeoutMs) { // RPC 同步等待 FIGHT 上限毫秒
        this.dispatchThreadModel = dispatchThreadModel; // 业务线程分片提交
        this.factory = factory; // msgId 路由表
        this.battleRpcForwarder = battleRpcForwarder; // 跨服战斗转发
        this.idempotencyService = idempotencyService; // 幂等 replay/record
        this.failureReporter = failureReporter;
        this.rpcTimeoutMs = rpcTimeoutMs; // FIGHT RPC await 超时
    } // 编译单元结束

    @Override // 实现接口/父类方法
    protected boolean preHandle(DispatchSession session, int msgId, byte[] payload) {
        if (factory.get(msgId) == null) {
            log.debug("Drop unknown msgId={}", msgId);
            return false;
        }
        if (!SessionAuthGuard.isPublicMessage(msgId) && !SessionAuthGuard.isAuthenticated(session)) {
            log.debug("Reject unauthenticated msgId={}", msgId);
            return false;
        }
        return true;
    }

    @Override // 实现接口/父类方法
    protected void dispatch(DispatchSession session, int msgId, byte[] payload) { // preHandle 通过后异步提交业务
        GameMessageFactory.Invoker inv = factory.get(msgId); // 查 Invoker 含 battleMessage 标记
        if (inv == null) { // preHandle 与 dispatch 之间理论上不会缺失，双保险
            return; // 无路由静默结束
        } // 编译单元结束

        // 已选角用 playerId 作 dispatchKey 保证串行；登录前用 marker.hashCode 分片
        long dispatchKey = session.playerId() != null && session.playerId() > 0 // AuthFacade 选角后 session.playerId>0
                ? session.playerId() // 同玩家消息 FIFO 串行到同一 stripe
                : (long) session.marker().hashCode(); // 未选角用 netty:xxx 或 ws:xxx 的 hash 分片
        // BattleMessage + GAME 服 + 转发开关 + 已选角：优先 RPC 转发 FIGHT，失败再本地 ClientRequestTask
        if (inv.battleMessage() // PayloadPacket 实现 BattleMessage 标记
                && battleRpcForwarder != null // MessageDispatchPipeline 逻辑
                && battleRpcForwarder.enabled() // MessageDispatchPipeline 逻辑
                && GameContext.serverType == ServerType.GAME // MessageDispatchPipeline 逻辑
                && session.playerId() != null // 须已选角
                && session.playerId() > 0) { // RPC 需 playerId 写入 RpcForwardClientMessage
            dispatchThreadModel.submit(dispatchKey, () -> { // 战斗消息也走 stripe 保证同玩家串行
                ProtocolMessage replay = idempotencyService.tryReplay(session, msgId, payload); // 重传包直接 replay ScRsp
                if (replay != null) { // 窗口内命中幂等缓存
                    session.afterResponse(msgId, replay); // 进场景/切线 ScRsp 仍触发推送绑定
                    session.send(replay); // Netty GameMessageEncoder 或 WebSocket BinaryFrame 出站
                    return; // 跳过 RPC 与 Facade
                } // 编译单元结束

                try { // 代码块开始
                    RpcForwardClientResponse resp = battleRpcForwarder.forward( // 同步 RPC 转发 FIGHT 等待回包
                            session.playerId(), msgId, payload, rpcTimeoutMs); // playerId+msgId+protobuf 发往 FIGHT
                    if (resp != null && resp.getMsgId() > 0) { // FIGHT 成功回包含有效 msgId
                        ProtocolMessage pm = new ProtocolMessage(resp.getMsgId(), resp.getPayload()); // 封装 FIGHT ScRsp 为出站 ProtocolMessage
                        idempotencyService.record(session, msgId, payload, pm); // 缓存供重传 replay
                        session.afterResponse(msgId, pm); // 战斗回包后钩子（若需绑定推送）
                        session.send(pm); // 经 Netty/WebSocket 编码回客户端
                        return; // RPC 成功，不走本地 Facade
                    } // 块 代码块结束
                } catch (InterruptedException e) { // RPC await 被 shutdownNow 中断
                    Thread.currentThread().interrupt(); // 恢复中断标志
                } catch (Exception e) { // RPC 网络失败或 FIGHT 异常
                    log.warn("Battle RPC 转发失败，降级本地处理 playerId={} msgId={}", session.playerId(), msgId, e);
                    failureReporter.recordRpcForwardFailure();
                } // 编译单元结束

                new ClientRequestTask(session, msgId, payload, factory, idempotencyService).run(); // FIGHT 不可用时的本地 Facade 兜底
            }); // MessageDispatchPipeline 逻辑
            return; // BattleMessage 路径已 submit，不再走普通分支
        } // 编译单元结束

        // 普通消息：提交业务线程，幂等检查后执行 ClientRequestTask
        dispatchThreadModel.submit(dispatchKey, () -> { // 普通 CsReq 提交 stripe
            ProtocolMessage replay = idempotencyService.tryReplay(session, msgId, payload); // dispatch 前查幂等缓存
            if (replay != null) { // TCP/WebSocket 重传命中窗口
                session.afterResponse(msgId, replay); // EnterScene/SwitchLine replay 仍 bind 推送
                session.send(replay); // 直接 replay ScRsp 出站
                return; // 跳过 ClientRequestTask
            } // 编译单元结束

            new ClientRequestTask(session, msgId, payload, factory, idempotencyService).run(); // stripe 内执行 Facade+send
        }); // MessageDispatchPipeline 逻辑
    } // 编译单元结束
} // 编译单元结束
