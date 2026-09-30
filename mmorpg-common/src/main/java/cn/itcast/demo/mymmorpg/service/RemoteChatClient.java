package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.SendChatMsgCsReq;

public interface RemoteChatClient {
    ProtocolMessage handleSendChat(long playerId, SendChatMsgCsReq req);
}
