package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.AcceptQuestCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ClaimQuestRewardCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetQuestListCsReq;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class QuestCommandGateway {

    private final QuestService localService;
    private final ObjectProvider<RemoteQuestClient> remoteClient;
    private final boolean remoteEnabled;

    public QuestCommandGateway(
            QuestService localService,
            ObjectProvider<RemoteQuestClient> remoteClient,
            @Value("${game.quest.remote.enabled:false}") boolean remoteEnabled) {
        this.localService = localService;
        this.remoteClient = remoteClient;
        this.remoteEnabled = remoteEnabled;
    }

    public ProtocolMessage handleGetQuestList(long playerId, GetQuestListCsReq req) {
        if (useRemote()) {
            return remoteClient.getObject().handleGetQuestList(playerId, req);
        }
        return localService.handleGetQuestList(playerId, req);
    }

    public ProtocolMessage handleAcceptQuest(long playerId, AcceptQuestCsReq req) {
        if (useRemote()) {
            return remoteClient.getObject().handleAcceptQuest(playerId, req);
        }
        return localService.handleAcceptQuest(playerId, req);
    }

    public ProtocolMessage handleClaimQuestReward(long playerId, ClaimQuestRewardCsReq req) {
        if (useRemote()) {
            return remoteClient.getObject().handleClaimQuestReward(playerId, req);
        }
        return localService.handleClaimQuestReward(playerId, req);
    }

    private boolean useRemote() {
        return remoteEnabled && remoteClient.getIfAvailable() != null;
    }
}
