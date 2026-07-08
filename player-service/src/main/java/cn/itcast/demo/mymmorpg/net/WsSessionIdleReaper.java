/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/net/WsSessionIdleReaper.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/net
 * 3) 主要职责：类 WsSessionIdleReaper，承载模块内相关实现。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.net; // player-service Netty/WebSocket 网络层与 YAML 配置加载
import cn.itcast.demo.mymmorpg.handler.WsDispatchSession; // WsState.lastSeenMs 空闲判定字段
import cn.itcast.demo.mymmorpg.service.PlayerPushRegistry; // 超时关闭时兜底 unbind
import org.slf4j.Logger; // debug 记录被回收的 sessionId/playerId
import org.slf4j.LoggerFactory; // 按类名创建 SLF4J Logger
import org.springframework.beans.factory.annotation.Value; // game.ws.idle-timeout-ms / sweep-interval-ms
import org.springframework.scheduling.annotation.Scheduled; // 定时 sweep，与 Netty IdleStateHandler 互补
import org.springframework.stereotype.Component; // @Scheduled sweep 定时任务 Bean
import org.springframework.web.socket.WebSocketSession; // 主动 close 触发客户端断线
import java.util.Map; // 遍历 handler.sessionStates()
/**
 * WebSocket 空闲回收：超过 idleTimeoutMs 未收到业务二进制帧或 Pong 的连接主动关闭并 unbind 推送。
 * <p>Netty TCP 侧由 IdleStateHandler + NettyIdleCloseHandler 处理，WebSocket 无 Netty pipeline 故用定时扫描。</p>
 */
@Component // Spring 定时任务 Bean，@Scheduled sweep
public class WsSessionIdleReaper { // WebSocket 半开连接回收，对应 Netty NettyIdleCloseHandler
    private static final Logger log = LoggerFactory.getLogger(WsSessionIdleReaper.class); // debug 记录 idle 关闭
    /** 持有 sessions/sessionRefs map，遍历 WsState.lastSeenMs */
    private final PlayerBinaryWebSocketHandler handler; // sessionStates/getSessionRef/removeSessionRef
    /** close 未触发 afterConnectionClosed 时的兜底解绑 */
    private final PlayerPushRegistry playerPushRegistry; // unbind(playerId) 清除僵尸推送
    /** game.ws.idle-timeout-ms，默认 90s 无消息/Pong 则关闭 */
    private final long idleTimeoutMs; // 超过此毫秒无业务帧/Pong 视为 idle
    public WsSessionIdleReaper(PlayerBinaryWebSocketHandler handler, // 构造注入 WebSocket Handler 与推送表
                               PlayerPushRegistry playerPushRegistry, // 超时兜底 unbind 僵尸推送
                               @Value("${game.ws.idle-timeout-ms:90000}") long idleTimeoutMs) { // 默认 90s WebSocket 空闲超时
        this.handler = handler; // 访问 WsState map 与 WebSocketSession 引用
        this.playerPushRegistry = playerPushRegistry; // 超时兜底 unbind
        this.idleTimeoutMs = idleTimeoutMs; // 空闲判定阈值毫秒
    } // 编译单元结束

    @Scheduled(fixedDelayString = "${game.ws.sweep-interval-ms:10000}") // 默认每 10s 扫一轮，fixedDelay=上次结束后再计
    public void sweep() { // Spring @Scheduled 定时回调
        long now = System.currentTimeMillis(); // 当前时间戳，与 WsState.lastSeenMs 比较
        for (Map.Entry<String, WsDispatchSession.WsState> e : handler.sessionStates().entrySet()) { // 遍历所有 WebSocket 会话
            String sessionId = e.getKey(); // ws sessionId
            WsDispatchSession.WsState st = e.getValue(); // accountId/playerId/lastSeenMs
            if (st == null) { // map 中值为 null（不应出现）
                continue; // 跳过无效条目
            } // 编译单元结束

            if (now - st.lastSeenMs <= idleTimeoutMs) { // 仍在 idle 窗口内活跃
                continue; // 跳过活跃连接
            } // 编译单元结束

            WebSocketSession ws = handler.getSessionRef(sessionId); // 取 live WebSocketSession
            try { // 代码块开始
                if (ws != null && ws.isOpen()) { // Session 仍打开
                    ws.close(); // 主动关闭，客户端收到 Close 帧；可能触发 afterConnectionClosed
                } // 块 代码块结束
            } catch (Exception ignore) { // close IO 异常
                // close 失败仍走 finally 清理 map 与 unbind
            } finally { // 代码块开始
                if (st.playerId != null && st.playerId > 0) { // 曾选角 bindWebSocket
                    playerPushRegistry.unbind(st.playerId); // 兜底 unbind，防止推送指向已死 WebSocket
                } // 块 代码块结束

                handler.removeSessionRef(sessionId); // 从 handler 内部 map 移除 WsState 与 SessionRef
                log.debug("WebSocket session idle timeout, closed sessionId={} playerId={}", sessionId, st.playerId); // 记录回收
            } // 编译单元结束
        } // 编译单元结束
    } // 编译单元结束
} // 编译单元结束
