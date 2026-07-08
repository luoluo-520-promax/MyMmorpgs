/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/handler/DispatchSession.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/handler
 * 3) 主要职责：类 DispatchSession，承载模块内相关实现。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.handler; // player-service 协议 Facade 与消息 dispatch 管道
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage; // 统一协议回包载体（msgId + protobuf payload），Netty/WebSocket 出站编码共用
/**
 * 统一会话抽象：Netty TCP 与 WebSocket 二进制帧经 MessageDispatchPipeline 分发时共用同一套 handler 签名。
 */
public interface DispatchSession { // Facade 第一参数类型，NettyDispatchSession/WsDispatchSession 双实现
    /** 当前连接绑定的账号 ID，AuthFacade 登录成功后写入，选角/登出时更新 */
    Long accountId(); // Netty ChannelAttrs.ACCOUNT_ID 或 WsState.accountId
    /** 将账号 ID 写入会话状态（Netty ChannelAttr 或 WsState） */
    void accountId(Long v); // AuthFacade.accountLogin OK 时写入，logout 时置 null
    /** 当前连接选中的玩家 ID，SelectPlayer 成功后写入，用于 dispatchKey 与推送绑定 */
    Long playerId(); // Netty ChannelAttrs.PLAYER_ID 或 WsState.playerId
    /** 将玩家 ID 写入会话状态，供串行分发与 PlayerPushRegistry 查找 */
    void playerId(Long v); // AuthFacade.selectPlayer OK 时写入，DispatchThreadModel dispatchKey
    /**
     * 会话唯一标识：格式 "netty:{channelId}" 或 "ws:{sessionId}"，
     * 用于 IdempotencyService 幂等键与选角时的多连接互斥 marker。
     */
    String marker(); // IdempotencyService 键前缀；选角时 AccountPlayerService 多连接互斥
    /** 经 Netty GameMessageEncoder 或 WebSocket BinaryFrameSender 向客户端写出 protobuf 帧 */
    void send(ProtocolMessage msg); // ClientRequestTask/幂等 replay 出站编码 [length][msgId][payload]
    /**
     * 业务回包后的会话级钩子：EnterScene/SwitchLine 成功时在业务线程绑定推送通道。
     * <p>在 DispatchThreadModel 业务线程中执行，不在 IO 线程调用。</p>
     */
    default void afterResponse(int requestMsgId, ProtocolMessage response) { // 默认空实现，Netty/Ws 子类 override 绑定推送
        // EnterScene/SwitchLine 成功时 NettyDispatchSession/WsDispatchSession 绑定 PlayerPushRegistry
    } // 编译单元结束
} // 编译单元结束
