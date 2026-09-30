package cn.itcast.demo.mymmorpg.handler;

import cn.itcast.demo.mymmorpg.protocol.GamePackets;
import cn.itcast.demo.mymmorpg.protocol.Modules;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.*;
import cn.itcast.demo.mymmorpg.service.SceneCommandGateway;
import org.springframework.stereotype.Component;

@Component
@MessageRoute(module = Modules.SCENE)
public class SceneFacade {

    private final SceneCommandGateway sceneCommandGateway;

    public SceneFacade(SceneCommandGateway sceneCommandGateway) {
        this.sceneCommandGateway = sceneCommandGateway;
    }

    @RequestHandler(cmd = 1)
    public ProtocolMessage enter(DispatchSession session, GamePackets.EnterSceneCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return sceneCommandGateway.handleEnterScene(pid, EnterSceneCsReq.parseFrom(pkt.payload()));
    }

    @RequestHandler(cmd = 3)
    public ProtocolMessage cur(DispatchSession session, GamePackets.GetCurSceneInfoCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return sceneCommandGateway.handleGetCurSceneInfo(pid, GetCurSceneInfoCsReq.parseFrom(pkt.payload()));
    }

    @RequestHandler(cmd = 5)
    public ProtocolMessage move(DispatchSession session, GamePackets.MoveCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return sceneCommandGateway.handleMove(pid, MoveCsReq.parseFrom(pkt.payload()));
    }

    @RequestHandler(cmd = 8)
    public ProtocolMessage switchLine(DispatchSession session, GamePackets.SwitchLineCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return sceneCommandGateway.handleSwitchLine(pid, SwitchLineCsReq.parseFrom(pkt.payload()));
    }

    @RequestHandler(cmd = 10)
    public ProtocolMessage nearby(DispatchSession session, GamePackets.GetNearbyEntitiesCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return sceneCommandGateway.handleGetNearby(pid, GetNearbyEntitiesCsReq.parseFrom(pkt.payload()));
    }

    @RequestHandler(cmd = 12)
    public ProtocolMessage transfer(DispatchSession session, GamePackets.TransferSceneCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return sceneCommandGateway.handleTransferScene(pid, TransferSceneCsReq.parseFrom(pkt.payload()));
    }

    @RequestHandler(cmd = 20)
    public ProtocolMessage resume(DispatchSession session, GamePackets.ResumeSceneCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return sceneCommandGateway.handleResumeScene(pid, ResumeSceneCsReq.parseFrom(pkt.payload()));
    }
}
