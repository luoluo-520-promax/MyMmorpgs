package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.DoGachaCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ExchangeGachaCeilingCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetGachaHistoryCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetGachaInfoCsReq;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GachaCommandGateway {

    private final GachaService localService;
    private final ObjectProvider<RemoteGachaClient> remoteClient;
    private final boolean remoteEnabled;

    public GachaCommandGateway(
            GachaService localService,
            ObjectProvider<RemoteGachaClient> remoteClient,
            @Value("${game.gacha.remote.enabled:false}") boolean remoteEnabled) {
        this.localService = localService;
        this.remoteClient = remoteClient;
        this.remoteEnabled = remoteEnabled;
    }

    public ProtocolMessage handleGetGachaInfo(long playerId, GetGachaInfoCsReq req) {
        if (useRemote()) {
            return remoteClient.getObject().handleGetGachaInfo(playerId, req);
        }
        return localService.handleGetGachaInfo(playerId, req);
    }

    public ProtocolMessage handleDoGacha(long playerId, DoGachaCsReq req) {
        if (useRemote()) {
            return remoteClient.getObject().handleDoGacha(playerId, req);
        }
        return localService.handleDoGacha(playerId, req);
    }

    public ProtocolMessage handleExchangeCeiling(long playerId, ExchangeGachaCeilingCsReq req) {
        if (useRemote()) {
            return remoteClient.getObject().handleExchangeCeiling(playerId, req);
        }
        return localService.handleExchangeCeiling(playerId, req);
    }

    public ProtocolMessage handleGetGachaHistory(long playerId, GetGachaHistoryCsReq req) {
        if (useRemote()) {
            return remoteClient.getObject().handleGetGachaHistory(playerId, req);
        }
        return localService.handleGetGachaHistory(playerId, req);
    }

    private boolean useRemote() {
        return remoteEnabled && remoteClient.getIfAvailable() != null;
    }
}
