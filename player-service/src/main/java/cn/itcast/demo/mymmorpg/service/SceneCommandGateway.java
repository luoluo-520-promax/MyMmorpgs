package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SceneCommandGateway {

    private final SceneActorService localService;
    private final ObjectProvider<RemoteSceneClient> remoteClient;
    private final boolean remoteEnabled;

    public SceneCommandGateway(
            SceneActorService localService,
            ObjectProvider<RemoteSceneClient> remoteClient,
            @Value("${game.scene.remote.enabled:false}") boolean remoteEnabled) {
        this.localService = localService;
        this.remoteClient = remoteClient;
        this.remoteEnabled = remoteEnabled;
    }

    public ProtocolMessage handleEnterScene(long playerId, EnterSceneCsReq req) {
        if (useRemote()) {
            return remoteClient.getObject().handleEnterScene(playerId, req);
        }
        return localService.handleEnterScene(playerId, req);
    }

    public ProtocolMessage handleGetCurSceneInfo(long playerId, GetCurSceneInfoCsReq req) {
        if (useRemote()) {
            return remoteClient.getObject().handleGetCurSceneInfo(playerId, req);
        }
        return localService.handleGetCurSceneInfo(playerId, req);
    }

    public ProtocolMessage handleMove(long playerId, MoveCsReq req) {
        if (useRemote()) {
            return remoteClient.getObject().handleMove(playerId, req);
        }
        return localService.handleMove(playerId, req);
    }

    public ProtocolMessage handleSwitchLine(long playerId, SwitchLineCsReq req) {
        if (useRemote()) {
            return remoteClient.getObject().handleSwitchLine(playerId, req);
        }
        return localService.handleSwitchLine(playerId, req);
    }

    public ProtocolMessage handleGetNearby(long playerId, GetNearbyEntitiesCsReq req) {
        if (useRemote()) {
            return remoteClient.getObject().handleGetNearby(playerId, req);
        }
        return localService.handleGetNearby(playerId, req);
    }

    public ProtocolMessage handleTransferScene(long playerId, TransferSceneCsReq req) {
        if (useRemote()) {
            return remoteClient.getObject().handleTransferScene(playerId, req);
        }
        return localService.handleTransferScene(playerId, req);
    }

    public ProtocolMessage handleResumeScene(long playerId, ResumeSceneCsReq req) {
        if (useRemote()) {
            return remoteClient.getObject().handleResumeScene(playerId, req);
        }
        return localService.handleResumeScene(playerId, req);
    }

    public void onPlayerLeave(long playerId) {
        if (useRemote()) {
            remoteClient.getObject().onPlayerLeave(playerId);
            return;
        }
        localService.onPlayerLeave(playerId);
    }

    public void onPlayerDisconnect(long playerId) {
        if (useRemote()) {
            remoteClient.getObject().onPlayerDisconnect(playerId);
            return;
        }
        localService.onPlayerDisconnect(playerId);
    }

    private boolean useRemote() {
        return remoteEnabled && remoteClient.getIfAvailable() != null;
    }
}
