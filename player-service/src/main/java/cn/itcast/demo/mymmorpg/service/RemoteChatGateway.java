package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.client.ChatCommandClient;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.SendChatMsgCsReq;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "game.chat.remote.enabled", havingValue = "true")
public class RemoteChatGateway implements RemoteChatClient {

    private final ChatCommandClient chatCommandClient;

    public RemoteChatGateway(ChatCommandClient chatCommandClient) {
        this.chatCommandClient = chatCommandClient;
    }

    @Override
    public ProtocolMessage handleSendChat(long playerId, SendChatMsgCsReq req) {
        return new ProtocolMessage(MessageId.SEND_CHAT_MSG_SC_RSP, chatCommandClient.send(playerId, req.toByteArray()));
    }
}
