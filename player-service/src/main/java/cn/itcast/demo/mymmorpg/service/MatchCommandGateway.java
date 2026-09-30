package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.CancelMatchCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.EnqueueMatchCsReq;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MatchCommandGateway {

    private final MatchmakingService localService;
    private final ObjectProvider<RemoteMatchClient> remoteClient;
    private final boolean remoteEnabled;

    public MatchCommandGateway(
            MatchmakingService localService,
            ObjectProvider<RemoteMatchClient> remoteClient,
            @Value("${game.match.remote.enabled:false}") boolean remoteEnabled) {
        this.localService = localService;
        this.remoteClient = remoteClient;
        this.remoteEnabled = remoteEnabled;
    }

    public ProtocolMessage handleEnqueueMatch(long playerId, EnqueueMatchCsReq req) {
        if (useRemote()) {
            return remoteClient.getObject().handleEnqueueMatch(playerId, req);
        }
        return localService.handleEnqueueMatch(playerId, req);
    }

    public ProtocolMessage handleCancelMatch(long playerId, CancelMatchCsReq req) {
        if (useRemote()) {
            return remoteClient.getObject().handleCancelMatch(playerId, req);
        }
        return localService.handleCancelMatch(playerId, req);
    }

    private boolean useRemote() {
        return remoteEnabled && remoteClient.getIfAvailable() != null;
    }
}
