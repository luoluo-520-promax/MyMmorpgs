/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/handler/ChainedMessageDispatcher.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/handler
 * 3) 主要职责：类 ChainedMessageDispatcher，承载模块内相关实现。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.handler; // player-service 协议 Facade 与消息 dispatch 管道
/**
 * 两阶段链式分发器模板：IO 线程只做 preHandle 轻量校验，重逻辑在 dispatch 阶段切到业务线程。
 * <p>Netty MessageIoDispatcher 与 WebSocket PlayerBinaryWebSocketHandler 均委托 MessageDispatchPipeline 继承本类。</p>
 */
public abstract class ChainedMessageDispatcher { // Netty/WebSocket 共用分发模板，子类实现 preHandle+dispatch
    /** 预处理：校验 msgId 是否已注册、是否丢弃未知包，在 IO 线程同步执行 */
    protected abstract boolean preHandle(DispatchSession session, int msgId, byte[] payload); // GameMessageFactory.get(msgId) 查路由表
    /** 正式分发：提交 DispatchThreadModel 或转发 FIGHT 服，不在 IO 线程执行业务 */
    protected abstract void dispatch(DispatchSession session, int msgId, byte[] payload); // 提交 ClientRequestTask 到 dispatch stripe
    /** 统一入口：preHandle 拒绝则短路，避免未知 msgId 进入业务线程池 */
    public final void handle(DispatchSession session, int msgId, byte[] payload) { // MessageIoDispatcher/WebSocket Handler 调用入口
        if (!preHandle(session, msgId, payload)) { // 未知 msgId 或校验失败，IO 线程静默丢弃
            return; // 不进入 dispatch，避免恶意/版本不匹配包占用业务线程
        } // 编译单元结束

        dispatch(session, msgId, payload); // 通过校验后异步提交业务线程或 RPC 转发
    } // 编译单元结束
} // 编译单元结束
