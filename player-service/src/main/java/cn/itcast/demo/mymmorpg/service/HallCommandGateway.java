package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.AddFriendCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ClaimMailCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetFriendListCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetMailListCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetRankingCsReq;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HallCommandGateway {

    private final HallService localService;
    private final ObjectProvider<RemoteHallClient> remoteClient;
    private final boolean remoteEnabled;

    public HallCommandGateway(
            HallService localService,
            ObjectProvider<RemoteHallClient> remoteClient,
            @Value("${game.hall.remote.enabled:false}") boolean remoteEnabled) {
        this.localService = localService;
        this.remoteClient = remoteClient;
        this.remoteEnabled = remoteEnabled;
    }

    public ProtocolMessage handleGetFriendList(long playerId, GetFriendListCsReq req) {
        if (useRemote()) {
            return remoteClient.getObject().handleGetFriendList(playerId, req);
        }
        return localService.handleGetFriendList(playerId, req);
    }

    public ProtocolMessage handleAddFriend(long playerId, AddFriendCsReq req) {
        if (useRemote()) {
            return remoteClient.getObject().handleAddFriend(playerId, req);
        }
        return localService.handleAddFriend(playerId, req);
    }

    public ProtocolMessage handleGetMailList(long playerId, GetMailListCsReq req) {
        if (useRemote()) {
            return remoteClient.getObject().handleGetMailList(playerId, req);
        }
        return localService.handleGetMailList(playerId, req);
    }

    public ProtocolMessage handleClaimMail(long playerId, ClaimMailCsReq req) {
        if (useRemote()) {
            return remoteClient.getObject().handleClaimMail(playerId, req);
        }
        return localService.handleClaimMail(playerId, req);
    }

    public ProtocolMessage handleGetRanking(long playerId, GetRankingCsReq req) {
        if (useRemote()) {
            return remoteClient.getObject().handleGetRanking(playerId, req);
        }
        return localService.handleGetRanking(playerId, req);
    }

    private boolean useRemote() {
        return remoteEnabled && remoteClient.getIfAvailable() != null;
    }
}
