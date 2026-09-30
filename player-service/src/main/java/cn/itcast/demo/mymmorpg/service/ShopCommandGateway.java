package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.CreateShopOrderCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetShopOrderCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetShopPurchaseHistoryCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetShopShelfCsReq;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ShopCommandGateway {

    private final ShopService localService;
    private final ObjectProvider<RemoteShopClient> remoteClient;
    private final boolean remoteEnabled;

    public ShopCommandGateway(
            ShopService localService,
            ObjectProvider<RemoteShopClient> remoteClient,
            @Value("${game.shop.remote.enabled:false}") boolean remoteEnabled) {
        this.localService = localService;
        this.remoteClient = remoteClient;
        this.remoteEnabled = remoteEnabled;
    }

    public ProtocolMessage handleGetShelf(long playerId, GetShopShelfCsReq req) {
        if (useRemote()) {
            return remoteClient.getObject().handleGetShelf(playerId, req);
        }
        return localService.handleGetShelf(playerId, req);
    }

    public ProtocolMessage handleCreateOrder(long playerId, CreateShopOrderCsReq req) {
        if (useRemote()) {
            return remoteClient.getObject().handleCreateOrder(playerId, req);
        }
        return localService.handleCreateOrder(playerId, req);
    }

    public ProtocolMessage handleGetOrder(long playerId, GetShopOrderCsReq req) {
        if (useRemote()) {
            return remoteClient.getObject().handleGetOrder(playerId, req);
        }
        return localService.handleGetOrder(playerId, req);
    }

    public ProtocolMessage handlePurchaseHistory(long playerId, GetShopPurchaseHistoryCsReq req) {
        if (useRemote()) {
            return remoteClient.getObject().handlePurchaseHistory(playerId, req);
        }
        return localService.handlePurchaseHistory(playerId, req);
    }

    private boolean useRemote() {
        return remoteEnabled && remoteClient.getIfAvailable() != null;
    }
}
