/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/net/PlayerBinaryWebSocketHandler.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/net
 * 3) 主要职责：类 PlayerBinaryWebSocketHandler，承载模块内相关实现。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.net; // player-service Netty/WebSocket 网络层与 YAML 配置加载
import cn.itcast.demo.mymmorpg.handler.MessageDispatchPipeline; // 与 Netty MessageIoDispatcher 共用分发管道
import cn.itcast.demo.mymmorpg.handler.WsDispatchSession; // WebSocket 侧 DispatchSession 实现
import cn.itcast.demo.mymmorpg.service.PlayerPushRegistry; // 断线/空闲时 unbind 推送
import org.slf4j.Logger; // 帧长度异常与分发失败日志
import org.slf4j.LoggerFactory; // 按类名创建 SLF4J Logger
import org.springframework.lang.NonNull; // WebSocket 回调参数非空契约
import org.springframework.stereotype.Component; // Spring WebSocket 配置注入本 Handler
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus; // 连接关闭状态码
import org.springframework.web.socket.PongMessage; // 心跳 Pong，刷新 lastSeenMs
import org.springframework.web.socket.WebSocketSession; // 二进制帧读写的会话对象
import org.springframework.web.socket.handler.BinaryWebSocketHandler; // 仅处理 BinaryMessage，忽略文本帧
import java.nio.ByteBuffer; // WebSocket payload 底层字节视图
import java.nio.ByteOrder; // 大端序，与 Netty GameMessageEncoder 一致
import java.util.Map; // sessionStates 暴露给 WsSessionIdleReaper
import java.util.concurrent.ConcurrentHashMap; // 多连接并发维护 sessionId -> WsState
/**
 * WebSocket 二进制协议入口：帧格式与 Netty TCP 相同 [length:4][msgId:4][protobuf]，经 MessageDispatchPipeline 路由。
 */
@Component // Spring WebSocket 配置注册为 BinaryWebSocketHandler Bean
public class PlayerBinaryWebSocketHandler extends BinaryWebSocketHandler { // WebSocket 二进制帧入口，等价 Netty MessageIoDispatcher
    private static final Logger log = LoggerFactory.getLogger(PlayerBinaryWebSocketHandler.class); // 记录帧非法与分发异常
    /** 推送注册表，afterConnectionClosed 与 IdleReaper 解绑 playerId */
    private final PlayerPushRegistry playerPushRegistry; // unbind(playerId) 清除推送映射
    /** Netty/WebSocket 共享的消息分发管道（preHandle + 业务线程 + 幂等 + 跨服） */
    private final MessageDispatchPipeline pipeline; // handle(WsDispatchSession, msgId, payload) 与 Netty 相同
    /** sessionId -> WsState（accountId/playerId/lastSeenMs），IdleReaper 扫描 lastSeenMs */
    private final ConcurrentHashMap<String, WsDispatchSession.WsState> sessions = new ConcurrentHashMap<>(); // 并发 map，AuthFacade 写 playerId
    /** sessionId -> WebSocketSession，IdleReaper 超时关闭时调用 ws.close() */
    private final ConcurrentHashMap<String, WebSocketSession> sessionRefs = new ConcurrentHashMap<>(); // live Session 引用
    public PlayerBinaryWebSocketHandler( // 构造注入推送表与 MessageDispatchPipeline
            PlayerPushRegistry playerPushRegistry, // 断线/空闲 unbind playerId 推送映射
            MessageDispatchPipeline pipeline) { // Spring 构造注入
        this.playerPushRegistry = playerPushRegistry; // 断线/空闲 unbind
        this.pipeline = pipeline; // 与 Netty 共用 MessageDispatchPipeline
    } // 编译单元结束

    @Override // 实现接口/父类方法
    protected void handleBinaryMessage(@NonNull WebSocketSession session, @NonNull BinaryMessage message) { // 每帧 BinaryMessage 回调
        sessionRefs.put(session.getId(), session); // 维护 sessionId -> live WebSocketSession，IdleReaper close 用
        ByteBuffer buf = message.getPayload(); // Spring BinaryMessage 底层 ByteBuffer
        buf.order(ByteOrder.BIG_ENDIAN); // length/msgId 大端，与 Netty TCP 对齐
        if (buf.remaining() < 8) { // 至少 length(4)+msgId(4)
            return; // 帧过短丢弃（WebSocket 无粘包缓冲，依赖客户端整帧发送）
        } // 编译单元结束

        int length = buf.getInt(); // 内容区长度 = msgId(4) + protobuf
        if (length < 4 || buf.remaining() < length) { // length 非法或帧不完整
            log.warn("WebSocket 帧长度非法 length={}", length); // 记录异常帧便于排查客户端
            return; // 丢弃非法帧，不断开连接
        } // 编译单元结束

        int msgId = buf.getInt(); // GameMessageFactory 路由键
        byte[] payload = new byte[length - 4]; // protobuf CsReq 体
        buf.get(payload); // 读 protobuf 字节到 heap 数组
        WsDispatchSession.WsState state = sessions.computeIfAbsent(session.getId(), k -> new WsDispatchSession.WsState()); // 首帧懒创建 WsState
        state.lastSeenMs = System.currentTimeMillis(); // 刷新活跃时间，WsSessionIdleReaper 判定空闲
        WsDispatchSession s = new WsDispatchSession(session, playerPushRegistry, state); // 包装为 DispatchSession
        try { // 代码块开始
            pipeline.handle(s, msgId, payload); // 与 Netty MessageIoDispatcher.channelRead0 相同入口
        } catch (Exception e) { // preHandle/dispatch 未捕获异常
            log.error("WebSocket 分发失败 msgId={}", msgId, e); // 记录异常，连接保持（与 Netty 策略一致）
        } // 块 代码块结束
    } // 编译单元结束

    @Override // 实现接口/父类方法
    protected void handlePongMessage(@NonNull WebSocketSession session, @NonNull PongMessage message) { // WebSocket 心跳 Pong 帧
        WsDispatchSession.WsState state = sessions.computeIfAbsent(session.getId(), k -> new WsDispatchSession.WsState()); // 无业务帧时也需 WsState
        state.lastSeenMs = System.currentTimeMillis(); // Pong 视为活跃，延长 idle 超时
        sessionRefs.put(session.getId(), session); // 维护 live Session 引用
    } // 编译单元结束

    @Override // 实现接口/父类方法
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) { // 客户端断线或 IdleReaper close
        WsDispatchSession.WsState st = sessions.remove(session.getId()); // 清理 WsState
        sessionRefs.remove(session.getId()); // 清理 Session 引用
        if (st != null && st.playerId != null && st.playerId > 0) { // 已选角曾 bindWebSocket
            playerPushRegistry.unbind(st.playerId); // 解除 playerId -> WebSocketSession 推送映射
        } // 编译单元结束
    } // 编译单元结束

    /** WsSessionIdleReaper 定时扫描 sessionId -> WsState.lastSeenMs */
    Map<String, WsDispatchSession.WsState> sessionStates() { // package 可见，供 IdleReaper 遍历
        return sessions; // 暴露 WsState 并发 map
    } // 编译单元结束

    /** IdleReaper 取 live WebSocketSession 执行 close() */
    WebSocketSession getSessionRef(String sessionId) { // package 可见
        return sessionRefs.get(sessionId); // 取 live WebSocketSession
    } // 编译单元结束

    /** IdleReaper 超时兜底：移除 map 条目，避免僵尸 sessionId */
    void removeSessionRef(String sessionId) { // package 可见，close 后清理
        sessionRefs.remove(sessionId); // 移除 Session 引用
        sessions.remove(sessionId); // 移除 WsState
    } // 编译单元结束
} // 编译单元结束
