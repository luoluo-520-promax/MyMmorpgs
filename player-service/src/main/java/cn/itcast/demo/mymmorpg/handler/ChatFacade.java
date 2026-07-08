/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/handler/ChatFacade.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/handler
 * 3) 主要职责：门面类 ChatFacade，协调协议层与业务服务。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.handler; // player-service 协议 Facade 与消息 dispatch 管道
import cn.itcast.demo.mymmorpg.handler.MessageRoute; // module=Modules.CHAT
import cn.itcast.demo.mymmorpg.handler.RequestHandler; // cmd=1 发送聊天
import cn.itcast.demo.mymmorpg.handler.DispatchSession; // playerId 标识发言者，广播经 PlayerPushRegistry
import cn.itcast.demo.mymmorpg.protocol.GamePackets; // SendChatMsgCsReq 包装
import cn.itcast.demo.mymmorpg.protocol.Modules; // CHAT 模块编号
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage; // 发送结果回包；广播推送由 ChatService 经 PushRegistry 下发
import cn.itcast.demo.mymmorpg.protocol.protobuf.SendChatMsgCsReq;
import cn.itcast.demo.mymmorpg.service.ChatService; // 频道校验、敏感词、广播逻辑
import org.springframework.stereotype.Component; // GameMessageFactory 扫描注册
@Component // Spring 单例，CHAT 模块唯一 Facade
@MessageRoute(module = Modules.CHAT) // msgId=abs(CHAT)*100+1 注册 SendChatMsgCsReq 路由
public class ChatFacade { // 聊天 protobuf 入口，发言者 playerId 来自 DispatchSession
    /** 聊天业务：校验频道、组装广播，经 PlayerPushRegistry 向在线玩家 WebSocket/Netty 推送 */
    private final ChatService chatService; // 频道校验、敏感词过滤、经 PlayerPushRegistry 广播 ChatScNtf
    public ChatFacade(ChatService chatService) { // GameMessageFactory MethodHandle 绑定目标
        this.chatService = chatService; // 持有 ChatService 供 send handler 委托
    } // 编译单元结束

    @RequestHandler(cmd = 1) // msgId：SendChatMsgCsReq
    public ProtocolMessage send(DispatchSession session, GamePackets.SendChatMsgCsReq pkt) throws Exception { // ChatFacade.send：DispatchSession session, GamePackets.SendChatMsgCsReq pkt
        long pid = session.playerId() != null ? session.playerId() : 0L; // 未选角 pid=0，ChatService 回 SendChatMsgScRsp 拒绝发言
        return chatService.handleSendChat(pid, // 校验频道/敏感词，广播 ChatScNtf 经 PlayerPushRegistry 推送
                SendChatMsgCsReq.parseFrom(pkt.payload())); // 解码 channel/content 等 SendChatMsgCsReq 字段
    } // send 方法体结束
} // 编译单元结束
