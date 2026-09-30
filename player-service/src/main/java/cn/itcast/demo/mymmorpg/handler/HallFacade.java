package cn.itcast.demo.mymmorpg.handler;

import cn.itcast.demo.mymmorpg.protocol.GamePackets;
import cn.itcast.demo.mymmorpg.protocol.Modules;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.AddFriendCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ClaimMailCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetFriendListCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetMailListCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetRankingCsReq;
import cn.itcast.demo.mymmorpg.service.HallCommandGateway;
import org.springframework.stereotype.Component;

@Component
@MessageRoute(module = Modules.HALL)
public class HallFacade {

    private final HallCommandGateway hallCommandGateway;

    public HallFacade(HallCommandGateway hallCommandGateway) {
        this.hallCommandGateway = hallCommandGateway;
    }

    @RequestHandler(cmd = 1)
    public ProtocolMessage friends(DispatchSession session, GamePackets.GetFriendListCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return hallCommandGateway.handleGetFriendList(pid, GetFriendListCsReq.parseFrom(pkt.payload()));
    }

    @RequestHandler(cmd = 3)
    public ProtocolMessage addFriend(DispatchSession session, GamePackets.AddFriendCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return hallCommandGateway.handleAddFriend(pid, AddFriendCsReq.parseFrom(pkt.payload()));
    }

    @RequestHandler(cmd = 5)
    public ProtocolMessage mails(DispatchSession session, GamePackets.GetMailListCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return hallCommandGateway.handleGetMailList(pid, GetMailListCsReq.parseFrom(pkt.payload()));
    }

    @RequestHandler(cmd = 7)
    public ProtocolMessage claimMail(DispatchSession session, GamePackets.ClaimMailCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return hallCommandGateway.handleClaimMail(pid, ClaimMailCsReq.parseFrom(pkt.payload()));
    }

    @RequestHandler(cmd = 9)
    public ProtocolMessage ranking(DispatchSession session, GamePackets.GetRankingCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return hallCommandGateway.handleGetRanking(pid, GetRankingCsReq.parseFrom(pkt.payload()));
    }
}
