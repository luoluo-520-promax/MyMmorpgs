package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.SendChatMsgCsReq;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ChatCommandGateway {

    private final ChatService localService;
    private final ObjectProvider<RemoteChatClient> remoteClient;
    private final boolean remoteEnabled;

    public ChatCommandGateway(
            ChatService localService,
            ObjectProvider<RemoteChatClient> remoteClient,
            @Value("${game.chat.remote.enabled:false}") boolean remoteEnabled) {
        this.localService = localService;
        this.remoteClient = remoteClient;
        this.remoteEnabled = remoteEnabled;
    }

    public ProtocolMessage handleSendChat(long playerId, SendChatMsgCsReq req) {
        if (useRemote()) {
            return remoteClient.getObject().handleSendChat(playerId, req);
        }
        return localService.handleSendChat(playerId, req);
    }

    private boolean useRemote() {
        return remoteEnabled && remoteClient.getIfAvailable() != null;
    }
}
