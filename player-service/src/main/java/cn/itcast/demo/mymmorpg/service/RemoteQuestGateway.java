package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.client.QuestCommandClient;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.AcceptQuestCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ClaimQuestRewardCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetQuestListCsReq;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "game.quest.remote.enabled", havingValue = "true")
public class RemoteQuestGateway implements RemoteQuestClient {

    private final QuestCommandClient questCommandClient;

    public RemoteQuestGateway(QuestCommandClient questCommandClient) {
        this.questCommandClient = questCommandClient;
    }

    @Override
    public ProtocolMessage handleGetQuestList(long playerId, GetQuestListCsReq req) {
        return new ProtocolMessage(MessageId.GET_QUEST_LIST_SC_RSP, questCommandClient.list(playerId, req.toByteArray()));
    }

    @Override
    public ProtocolMessage handleAcceptQuest(long playerId, AcceptQuestCsReq req) {
        return new ProtocolMessage(MessageId.ACCEPT_QUEST_SC_RSP, questCommandClient.accept(playerId, req.toByteArray()));
    }

    @Override
    public ProtocolMessage handleClaimQuestReward(long playerId, ClaimQuestRewardCsReq req) {
        return new ProtocolMessage(MessageId.CLAIM_QUEST_REWARD_SC_RSP, questCommandClient.claim(playerId, req.toByteArray()));
    }
}
