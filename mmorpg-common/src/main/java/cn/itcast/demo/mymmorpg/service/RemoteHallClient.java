package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.AddFriendCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ClaimMailCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetFriendListCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetMailListCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetRankingCsReq;

public interface RemoteHallClient {
    ProtocolMessage handleGetFriendList(long playerId, GetFriendListCsReq req);

    ProtocolMessage handleAddFriend(long playerId, AddFriendCsReq req);

    ProtocolMessage handleGetMailList(long playerId, GetMailListCsReq req);

    ProtocolMessage handleClaimMail(long playerId, ClaimMailCsReq req);

    ProtocolMessage handleGetRanking(long playerId, GetRankingCsReq req);
}
