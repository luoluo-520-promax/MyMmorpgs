package cn.itcast.demo.mymmorpg.handler;

import cn.itcast.demo.mymmorpg.protocol.GamePackets;
import cn.itcast.demo.mymmorpg.protocol.Modules;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.AcceptQuestCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ClaimQuestRewardCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetQuestListCsReq;
import cn.itcast.demo.mymmorpg.service.QuestCommandGateway;
import org.springframework.stereotype.Component;

@Component
@MessageRoute(module = Modules.QUEST)
public class QuestFacade {

    private final QuestCommandGateway questCommandGateway;

    public QuestFacade(QuestCommandGateway questCommandGateway) {
        this.questCommandGateway = questCommandGateway;
    }

    @RequestHandler(cmd = 1)
    public ProtocolMessage list(DispatchSession session, GamePackets.GetQuestListCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return questCommandGateway.handleGetQuestList(pid, GetQuestListCsReq.parseFrom(pkt.payload()));
    }

    @RequestHandler(cmd = 3)
    public ProtocolMessage accept(DispatchSession session, GamePackets.AcceptQuestCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return questCommandGateway.handleAcceptQuest(pid, AcceptQuestCsReq.parseFrom(pkt.payload()));
    }

    @RequestHandler(cmd = 5)
    public ProtocolMessage claim(DispatchSession session, GamePackets.ClaimQuestRewardCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return questCommandGateway.handleClaimQuestReward(pid, ClaimQuestRewardCsReq.parseFrom(pkt.payload()));
    }
}
