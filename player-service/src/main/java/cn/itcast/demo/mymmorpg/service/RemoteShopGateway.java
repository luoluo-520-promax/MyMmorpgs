package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.client.ShopCommandClient;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.CreateShopOrderCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetShopOrderCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetShopPurchaseHistoryCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetShopShelfCsReq;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "game.shop.remote.enabled", havingValue = "true")
public class RemoteShopGateway implements RemoteShopClient {

    private final ShopCommandClient shopCommandClient;

    public RemoteShopGateway(ShopCommandClient shopCommandClient) {
        this.shopCommandClient = shopCommandClient;
    }

    @Override
    public ProtocolMessage handleGetShelf(long playerId, GetShopShelfCsReq req) {
        return new ProtocolMessage(MessageId.GET_SHOP_SHELF_SC_RSP,
                shopCommandClient.shelf(playerId, req.toByteArray()));
    }

    @Override
    public ProtocolMessage handleCreateOrder(long playerId, CreateShopOrderCsReq req) {
        return new ProtocolMessage(MessageId.CREATE_SHOP_ORDER_SC_RSP,
                shopCommandClient.createOrder(playerId, req.toByteArray()));
    }

    @Override
    public ProtocolMessage handleGetOrder(long playerId, GetShopOrderCsReq req) {
        return new ProtocolMessage(MessageId.GET_SHOP_ORDER_SC_RSP,
                shopCommandClient.getOrder(playerId, req.toByteArray()));
    }

    @Override
    public ProtocolMessage handlePurchaseHistory(long playerId, GetShopPurchaseHistoryCsReq req) {
        return new ProtocolMessage(MessageId.GET_SHOP_PURCHASE_HISTORY_SC_RSP,
                shopCommandClient.history(playerId, req.toByteArray()));
    }
}
