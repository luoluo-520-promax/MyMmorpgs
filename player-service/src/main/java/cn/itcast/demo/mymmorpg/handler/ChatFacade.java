package cn.itcast.demo.mymmorpg.handler;

import cn.itcast.demo.mymmorpg.protocol.GamePackets;
import cn.itcast.demo.mymmorpg.protocol.Modules;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.SendChatMsgCsReq;
import cn.itcast.demo.mymmorpg.service.ChatCommandGateway;
import org.springframework.stereotype.Component;

@Component
@MessageRoute(module = Modules.CHAT)
public class ChatFacade {

    private final ChatCommandGateway chatCommandGateway;

    public ChatFacade(ChatCommandGateway chatCommandGateway) {
        this.chatCommandGateway = chatCommandGateway;
    }

    @RequestHandler(cmd = 1)
    public ProtocolMessage send(DispatchSession session, GamePackets.SendChatMsgCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return chatCommandGateway.handleSendChat(pid, SendChatMsgCsReq.parseFrom(pkt.payload()));
    }
}
