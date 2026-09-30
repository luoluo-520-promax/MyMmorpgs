package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.CancelMatchCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.EnqueueMatchCsReq;

public interface RemoteMatchClient {
    ProtocolMessage handleEnqueueMatch(long playerId, EnqueueMatchCsReq req);

    ProtocolMessage handleCancelMatch(long playerId, CancelMatchCsReq req);
}
