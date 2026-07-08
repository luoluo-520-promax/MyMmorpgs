/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/handler/ClientRequestTask.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/handler
 * 3) 主要职责：类 ClientRequestTask，承载模块内相关实现。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.handler; // player-service 协议 Facade 与消息 dispatch 管道
import cn.itcast.demo.mymmorpg.handler.GameMessageFactory; // msgId -> Invoker 路由表，反射调用 Facade 方法
import cn.itcast.demo.mymmorpg.protocol.PayloadPacket; // protobuf 请求包装，(byte[]) 构造器反序列化
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage; // Facade 返回的统一回包，经 session.send 编码出站
import org.slf4j.Logger; // 记录 Facade 业务异常，不向上抛避免线程池任务失败
import org.slf4j.LoggerFactory; // 按类名创建 SLF4J Logger，输出 dispatch 业务异常
/**
 * 业务线程 Runnable：在 DispatchThreadModel stripe 中执行 Facade handler，完成后回包并触发 afterResponse。
 */
public class ClientRequestTask implements Runnable { // DispatchThreadModel stripe 单线程池执行，保证同 playerId 串行
    private static final Logger log = LoggerFactory.getLogger(ClientRequestTask.class); // 记录 msgId 与 Facade 异常摘要
    /** 当前客户端会话，出站 send 与 afterResponse 推送绑定均经此对象 */
    private final DispatchSession session; // NettyDispatchSession 或 WsDispatchSession，含 accountId/playerId
    /** 客户端请求 msgId，用于路由 Invoker 与幂等 record 键 */
    private final int msgId; // GameMessageFactory.signedMsgId(module,cmd)，路由 Facade 方法
    /** protobuf 原始字节，构造 PayloadPacket 并参与幂等 payloadHash */
    private final byte[] payload; // Netty GameMessageDecoder 或 WebSocket 帧解析出的 protobuf 体
    /** 启动时扫描注册的 msgId -> Facade MethodHandle 映射 */
    private final GameMessageFactory factory; // 查 Invoker、构造 PayloadPacket、调用 Facade
    /** 可选幂等服务，重复包直接 replay 时不进入本 Task */
    private final IdempotencyService idempotencyService; // 成功回包后 record，供 tryReplay 重传去重
    public ClientRequestTask(DispatchSession session, int msgId, byte[] payload, GameMessageFactory factory, // 封装单次客户端请求上下文
                             IdempotencyService idempotencyService) { // MessageDispatchPipeline 构造并 submit 到 stripe
        this.session = session; // 绑定 Netty/WebSocket 会话，send/afterResponse 出站
        this.msgId = msgId; // 客户端 msgId，查 GameMessageFactory.routes
        this.payload = payload != null ? payload : new byte[0]; // 空 payload 合法，如部分心跳类 CsReq
        this.factory = factory; // 路由表与 PayloadPacket 构造器
        this.idempotencyService = idempotencyService; // 幂等 record，可为 null 时跳过
    } // 编译单元结束

    @Override // 实现接口/父类方法
    public void run() { // DispatchThreadModel stripe 单线程池回调
        GameMessageFactory.Invoker inv = factory.get(msgId); // 按 msgId 查 Facade MethodHandle 路由
        if (inv == null) { // preHandle 已过滤未知 msgId，此处双保险
            return; // 无路由则静默结束，不断开连接
        } // 编译单元结束

        try { // 代码块开始
            PayloadPacket pkt = factory.newPacket(msgId, payload); // byte[] -> GamePackets.*CsReq 包装类
            if (pkt == null) { // 构造失败时 factory 可能返回 null
                return; // 无法反序列化则跳过，避免 NPE
            } // 块 代码块结束

            Object out = inv.invoke(session, pkt); // MethodHandle 调用 AuthFacade/SceneFacade 等 handler
            if (out instanceof ProtocolMessage pm) { // Facade 约定返回 ProtocolMessage 才回写客户端
                if (idempotencyService != null) { // 幂等服务已启用
                    idempotencyService.record(session, msgId, payload, pm); // 缓存 ScRsp 供窗口内 TCP/WebSocket 重传 replay
                } // 编译单元结束

                session.afterResponse(msgId, pm); // EnterScene/SwitchLine OK 时 bindNetty/bindWebSocket 推送通道
                session.send(pm); // Netty writeAndFlush(GameMessage) 或 BinaryFrameSender 写 WebSocket 帧
            } // 编译单元结束
        } catch (Exception e) { // Facade 业务异常或 protobuf 解析失败
            log.warn("ClientRequestTask failed msgId={} err={}", msgId, e.toString()); // 吞掉异常，避免 stripe 线程终止
        } // 编译单元结束
    } // 编译单元结束
} // 编译单元结束
