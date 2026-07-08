/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/handler/WsDispatchSession.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/handler
 * 3) 主要职责：类 WsDispatchSession，承载模块内相关实现。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.handler; // player-service 协议 Facade 与消息 dispatch 管道
import cn.itcast.demo.mymmorpg.net.BinaryFrameSender; // WebSocket 出站：封装 [length][msgId][protobuf] 二进制帧
import cn.itcast.demo.mymmorpg.protocol.MessageId; // 协议 msgId 常量，afterResponse 识别进场景/切线请求
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage; // 统一回包结构，send 时取出 msgId 与 payload
import cn.itcast.demo.mymmorpg.protocol.RetCode; // protobuf retcode，OK 时才绑定推送通道
import cn.itcast.demo.mymmorpg.protocol.protobuf.EnterSceneScRsp; // 进场景响应体，解析 retcode 决定是否 bindWebSocket
import cn.itcast.demo.mymmorpg.protocol.protobuf.SwitchLineScRsp; // 切线响应体，成功时刷新 WebSocket 推送绑定
import cn.itcast.demo.mymmorpg.service.PlayerPushRegistry; // playerId -> WebSocketSession/Channel 双向映射，服务端推送入口
import org.springframework.web.socket.WebSocketSession; // Spring WebSocket 会话，BinaryFrameSender 写出帧的目标
/**
 * WebSocket 侧 DispatchSession 实现：accountId/playerId 存 WsState，出站经 BinaryFrameSender 写二进制帧。
 */
public final class WsDispatchSession implements DispatchSession { // PlayerBinaryWebSocketHandler 每帧构造
    /** 底层 WebSocket 连接，send 时经 BinaryFrameSender 写出与 Netty 同格式的帧 */
    private final WebSocketSession ws; // Spring WebSocket 会话，BinaryFrame 读写目标
    /** 推送注册表，进场景/切线成功后 bindWebSocket，断线时由 Handler 或 IdleReaper unbind */
    private final PlayerPushRegistry playerPushRegistry; // bindWebSocket(pid, ws) 注册推送通道
    /** 会话级可变状态，与 sessionId 一一对应，供 IdleReaper 扫描 lastSeenMs */
    private final WsState state; // accountId/playerId/lastSeenMs，跨帧共享
    public WsDispatchSession(WebSocketSession ws, PlayerPushRegistry playerPushRegistry, WsState state) { // handleBinaryMessage 每帧 new
        this.ws = ws; // 绑定 WebSocket 连接
        this.playerPushRegistry = playerPushRegistry; // afterResponse 绑定推送
        this.state = state; // AuthFacade 读写 accountId/playerId
    } // 编译单元结束

    @Override // 实现接口/父类方法
    public Long accountId() { // AuthFacade 登录态
        return state.accountId; // volatile 读，IO 线程与 dispatch stripe 可见
    } // 编译单元结束

    @Override // 实现接口/父类方法
    public void accountId(Long v) { // AuthFacade.accountLogin/logout
        state.accountId = v; // 登出时置 null，断开账号与会话关联
    } // 编译单元结束

    @Override // 实现接口/父类方法
    public Long playerId() { // dispatchKey 与推送键
        return state.playerId; // SelectPlayer 成功后写入
    } // 编译单元结束

    @Override // 实现接口/父类方法
    public void playerId(Long v) { // AuthFacade.selectPlayer/logout
        state.playerId = v; // 切换角色或登出时更新
    } // 编译单元结束

    @Override // 实现接口/父类方法
    public String marker() { // IdempotencyService 键前缀
        return "ws:" + ws.getId(); // 区分同账号不同 WebSocket 连接
    } // 编译单元结束

    @Override // 实现接口/父类方法
    public void send(ProtocolMessage msg) { // ClientRequestTask/幂等 replay 出站
        if (msg == null) { // 防御空包
            return; // pipeline 异常路径可能传入 null
        } // 编译单元结束

        try { // 代码块开始
            BinaryFrameSender.sendWebSocket(ws, msg.msgId(), msg.payload()); // 与 Netty GameMessageEncoder 帧格式 [length][msgId][payload] 对齐
        } catch (Exception ignore) { // 客户端已断开时写帧失败
            // 连接已关闭时写 WebSocket 帧失败，忽略避免 dispatch 线程抛错
        } // 块 代码块结束
    } // 编译单元结束

    @Override // 实现接口/父类方法
    public void afterResponse(int requestMsgId, ProtocolMessage response) { // dispatch stripe 线程，send 前调用
        if (playerPushRegistry == null || response == null) { // 测试或未注入推送表
            return; // 跳过 bindWebSocket
        } // 编译单元结束

        Long pid = playerId(); // 当前 WsState 绑定的玩家
        if (pid == null || pid <= 0) { // 未选角前不绑定推送
            return; // 避免向未进场景玩家推送
        } // 编译单元结束

        try { // 代码块开始
            if (requestMsgId == MessageId.ENTER_SCENE_CS_REQ) { // 进场景 CsReq 回包
                EnterSceneScRsp rsp = EnterSceneScRsp.parseFrom(response.payload()); // protobuf 解码 EnterSceneScRsp
                if (rsp.getRetcode() == RetCode.OK) { // 进场景成功
                    playerPushRegistry.bindWebSocket(pid, ws); // 后续服务端广播经此 WebSocket 下发
                } // 块 代码块结束
            } else if (requestMsgId == MessageId.SWITCH_LINE_CS_REQ) { // 切分线 CsReq 回包
                SwitchLineScRsp rsp = SwitchLineScRsp.parseFrom(response.payload()); // protobuf 解码 SwitchLineScRsp
                if (rsp.getRetcode() == RetCode.OK) { // 切线成功
                    playerPushRegistry.bindWebSocket(pid, ws); // 刷新 WebSocket 推送通道
                } // 编译单元结束
            } // 编译单元结束
        } catch (Exception ignore) { // protobuf 损坏
            // payload 损坏或类型不匹配时不影响主流程 ScRsp 出站
        } // 编译单元结束
    } // 编译单元结束

    /** WebSocket 会话共享状态：volatile 保证 IO 线程写 lastSeenMs、业务线程读 playerId 可见性 */
    public static final class WsState { // PlayerBinaryWebSocketHandler.sessions 按 sessionId 存储
        /** 账号 ID，登录成功后由 AuthFacade 写入 */
        public volatile Long accountId; // AuthFacade.accountLogin OK 时写入
        /** 玩家 ID，选角成功后写入，IdleReaper 超时关闭时用于 unbind */
        public volatile Long playerId; // AuthFacade.selectPlayer OK 时写入
        /** 最后活跃毫秒时间戳，收到业务帧或 Pong 时刷新，WsSessionIdleReaper 据此判定空闲 */
        public volatile long lastSeenMs = System.currentTimeMillis(); // handleBinaryMessage/handlePongMessage 刷新
    } // 编译单元结束
} // 编译单元结束
