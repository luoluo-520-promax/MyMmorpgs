package cn.itcast.demo.mymmorpg.handler;

import cn.itcast.demo.mymmorpg.port.PlayerDataLoadPort;
import cn.itcast.demo.mymmorpg.port.PlayerDataPreloadPort;
import cn.itcast.demo.mymmorpg.protocol.GamePackets;
import cn.itcast.demo.mymmorpg.protocol.Modules;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.*;
import cn.itcast.demo.mymmorpg.service.BagCommandGateway;
import org.springframework.stereotype.Component;

@Component
@MessageRoute(module = Modules.BAG)
public class BagFacade {

    private final BagCommandGateway bagCommandGateway;
    private final PlayerDataPreloadPort preloadService;

    public BagFacade(BagCommandGateway bagCommandGateway, PlayerDataPreloadPort preloadService) {
        this.bagCommandGateway = bagCommandGateway;
        this.preloadService = preloadService;
    }

    @RequestHandler(cmd = 1)
    public ProtocolMessage info(DispatchSession session, GamePackets.GetBagInfoCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        ProtocolMessage out = bagCommandGateway.handleGetBagInfo(pid, GetBagInfoCsReq.parseFrom(pkt.payload()));
        GetBagInfoScRsp rsp = GetBagInfoScRsp.parseFrom(out.payload());
        if (rsp.getLoading()) {
            preloadService.trigger(pid, PlayerDataLoadPort.DataType.BAG);
        }
        return out;
    }

    @RequestHandler(cmd = 3)
    public ProtocolMessage use(DispatchSession session, GamePackets.UseItemCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return bagCommandGateway.handleUseItem(pid, UseItemCsReq.parseFrom(pkt.payload()));
    }

    @RequestHandler(cmd = 5)
    public ProtocolMessage discard(DispatchSession session, GamePackets.DiscardItemCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return bagCommandGateway.handleDiscardItem(pid, DiscardItemCsReq.parseFrom(pkt.payload()));
    }

    @RequestHandler(cmd = 7)
    public ProtocolMessage sort(DispatchSession session, GamePackets.SortBagCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return bagCommandGateway.handleSortBag(pid, SortBagCsReq.parseFrom(pkt.payload()));
    }

    @RequestHandler(cmd = 9)
    public ProtocolMessage sell(DispatchSession session, GamePackets.SellItemCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return bagCommandGateway.handleSellItem(pid, SellItemCsReq.parseFrom(pkt.payload()));
    }

    @RequestHandler(cmd = 13)
    public ProtocolMessage equip(DispatchSession session, GamePackets.EquipItemCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return bagCommandGateway.handleEquipItem(pid, EquipItemCsReq.parseFrom(pkt.payload()));
    }

    @RequestHandler(cmd = 15)
    public ProtocolMessage unequip(DispatchSession session, GamePackets.UnequipItemCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return bagCommandGateway.handleUnequipItem(pid, UnequipItemCsReq.parseFrom(pkt.payload()));
    }
}
