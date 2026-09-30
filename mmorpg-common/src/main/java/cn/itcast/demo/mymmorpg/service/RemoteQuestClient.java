package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.AcceptQuestCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ClaimQuestRewardCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetQuestListCsReq;

public interface RemoteQuestClient {
    ProtocolMessage handleGetQuestList(long playerId, GetQuestListCsReq req);

    ProtocolMessage handleAcceptQuest(long playerId, AcceptQuestCsReq req);

    ProtocolMessage handleClaimQuestReward(long playerId, ClaimQuestRewardCsReq req);
}
