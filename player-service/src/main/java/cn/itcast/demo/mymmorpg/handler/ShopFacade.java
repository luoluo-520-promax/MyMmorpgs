package cn.itcast.demo.mymmorpg.handler;

import cn.itcast.demo.mymmorpg.protocol.GamePackets;
import cn.itcast.demo.mymmorpg.protocol.Modules;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.CreateShopOrderCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetShopOrderCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetShopPurchaseHistoryCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetShopShelfCsReq;
import cn.itcast.demo.mymmorpg.service.ShopCommandGateway;
import org.springframework.stereotype.Component;

@Component
@MessageRoute(module = Modules.SHOP)
public class ShopFacade {

    private final ShopCommandGateway shopCommandGateway;

    public ShopFacade(ShopCommandGateway shopCommandGateway) {
        this.shopCommandGateway = shopCommandGateway;
    }

    @RequestHandler(cmd = 1)
    public ProtocolMessage shelf(DispatchSession session, GamePackets.GetShopShelfCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return shopCommandGateway.handleGetShelf(pid, GetShopShelfCsReq.parseFrom(pkt.payload()));
    }

    @RequestHandler(cmd = 3)
    public ProtocolMessage createOrder(DispatchSession session, GamePackets.CreateShopOrderCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return shopCommandGateway.handleCreateOrder(pid, CreateShopOrderCsReq.parseFrom(pkt.payload()));
    }

    @RequestHandler(cmd = 5)
    public ProtocolMessage getOrder(DispatchSession session, GamePackets.GetShopOrderCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return shopCommandGateway.handleGetOrder(pid, GetShopOrderCsReq.parseFrom(pkt.payload()));
    }

    @RequestHandler(cmd = 7)
    public ProtocolMessage history(DispatchSession session, GamePackets.GetShopPurchaseHistoryCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return shopCommandGateway.handlePurchaseHistory(pid, GetShopPurchaseHistoryCsReq.parseFrom(pkt.payload()));
    }
}
