package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.client.SceneCommandClient;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "game.scene.remote.enabled", havingValue = "true")
public class RemoteSceneGateway implements RemoteSceneClient {

    private final SceneCommandClient sceneCommandClient;

    public RemoteSceneGateway(SceneCommandClient sceneCommandClient) {
        this.sceneCommandClient = sceneCommandClient;
    }

    @Override
    public ProtocolMessage handleEnterScene(long playerId, EnterSceneCsReq req) {
        return new ProtocolMessage(MessageId.ENTER_SCENE_SC_RSP, sceneCommandClient.enter(playerId, req.toByteArray()));
    }

    @Override
    public ProtocolMessage handleGetCurSceneInfo(long playerId, GetCurSceneInfoCsReq req) {
        return new ProtocolMessage(MessageId.GET_CUR_SCENE_INFO_SC_RSP, sceneCommandClient.cur(playerId, req.toByteArray()));
    }

    @Override
    public ProtocolMessage handleMove(long playerId, MoveCsReq req) {
        return new ProtocolMessage(MessageId.MOVE_SC_RSP, sceneCommandClient.move(playerId, req.toByteArray()));
    }

    @Override
    public ProtocolMessage handleSwitchLine(long playerId, SwitchLineCsReq req) {
        return new ProtocolMessage(MessageId.SWITCH_LINE_SC_RSP, sceneCommandClient.switchLine(playerId, req.toByteArray()));
    }

    @Override
    public ProtocolMessage handleGetNearby(long playerId, GetNearbyEntitiesCsReq req) {
        return new ProtocolMessage(MessageId.GET_NEARBY_ENTITIES_SC_RSP, sceneCommandClient.nearby(playerId, req.toByteArray()));
    }

    @Override
    public ProtocolMessage handleTransferScene(long playerId, TransferSceneCsReq req) {
        return new ProtocolMessage(MessageId.TRANSFER_SCENE_SC_RSP, sceneCommandClient.transfer(playerId, req.toByteArray()));
    }

    @Override
    public ProtocolMessage handleResumeScene(long playerId, ResumeSceneCsReq req) {
        return new ProtocolMessage(MessageId.RESUME_SCENE_SC_RSP, sceneCommandClient.resume(playerId, req.toByteArray()));
    }

    @Override
    public void onPlayerLeave(long playerId) {
        sceneCommandClient.leave(playerId);
    }

    @Override
    public void onPlayerDisconnect(long playerId) {
        sceneCommandClient.disconnect(playerId);
    }
}
