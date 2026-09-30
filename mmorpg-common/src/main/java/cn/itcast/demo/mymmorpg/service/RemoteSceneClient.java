package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.*;

public interface RemoteSceneClient {
    ProtocolMessage handleEnterScene(long playerId, EnterSceneCsReq req);
    ProtocolMessage handleGetCurSceneInfo(long playerId, GetCurSceneInfoCsReq req);
    ProtocolMessage handleMove(long playerId, MoveCsReq req);
    ProtocolMessage handleSwitchLine(long playerId, SwitchLineCsReq req);
    ProtocolMessage handleGetNearby(long playerId, GetNearbyEntitiesCsReq req);
    ProtocolMessage handleTransferScene(long playerId, TransferSceneCsReq req);
    ProtocolMessage handleResumeScene(long playerId, ResumeSceneCsReq req);
    void onPlayerLeave(long playerId);
    void onPlayerDisconnect(long playerId);
}
