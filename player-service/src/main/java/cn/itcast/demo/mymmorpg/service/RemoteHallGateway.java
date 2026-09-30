package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.client.HallCommandClient;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.AddFriendCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ClaimMailCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetFriendListCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetMailListCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetRankingCsReq;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "game.hall.remote.enabled", havingValue = "true")
public class RemoteHallGateway implements RemoteHallClient {

    private final HallCommandClient hallCommandClient;

    public RemoteHallGateway(HallCommandClient hallCommandClient) {
        this.hallCommandClient = hallCommandClient;
    }

    @Override
    public ProtocolMessage handleGetFriendList(long playerId, GetFriendListCsReq req) {
        return new ProtocolMessage(MessageId.GET_FRIEND_LIST_SC_RSP, hallCommandClient.friends(playerId, req.toByteArray()));
    }

    @Override
    public ProtocolMessage handleAddFriend(long playerId, AddFriendCsReq req) {
        return new ProtocolMessage(MessageId.ADD_FRIEND_SC_RSP, hallCommandClient.addFriend(playerId, req.toByteArray()));
    }

    @Override
    public ProtocolMessage handleGetMailList(long playerId, GetMailListCsReq req) {
        return new ProtocolMessage(MessageId.GET_MAIL_LIST_SC_RSP, hallCommandClient.mails(playerId, req.toByteArray()));
    }

    @Override
    public ProtocolMessage handleClaimMail(long playerId, ClaimMailCsReq req) {
        return new ProtocolMessage(MessageId.CLAIM_MAIL_SC_RSP, hallCommandClient.claimMail(playerId, req.toByteArray()));
    }

    @Override
    public ProtocolMessage handleGetRanking(long playerId, GetRankingCsReq req) {
        return new ProtocolMessage(MessageId.GET_RANKING_SC_RSP, hallCommandClient.ranking(playerId, req.toByteArray()));
    }
}
