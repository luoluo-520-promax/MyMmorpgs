package cn.itcast.demo.mymmorpg.handler;

import cn.itcast.demo.mymmorpg.protocol.GamePackets;
import cn.itcast.demo.mymmorpg.protocol.Modules;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.EquipSkinCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetWardrobeCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.UnequipSkinCsReq;
import cn.itcast.demo.mymmorpg.service.SkinService;
import org.springframework.stereotype.Component;

@Component
@MessageRoute(module = Modules.SKIN)
public class SkinFacade {

    private final SkinService skinService;

    public SkinFacade(SkinService skinService) {
        this.skinService = skinService;
    }

    @RequestHandler(cmd = 1)
    public ProtocolMessage wardrobe(DispatchSession session, GamePackets.GetWardrobeCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return skinService.handleGetWardrobe(pid, GetWardrobeCsReq.parseFrom(pkt.payload()));
    }

    @RequestHandler(cmd = 3)
    public ProtocolMessage equip(DispatchSession session, GamePackets.EquipSkinCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return skinService.handleEquipSkin(pid, EquipSkinCsReq.parseFrom(pkt.payload()));
    }

    @RequestHandler(cmd = 5)
    public ProtocolMessage unequip(DispatchSession session, GamePackets.UnequipSkinCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return skinService.handleUnequipSkin(pid, UnequipSkinCsReq.parseFrom(pkt.payload()));
    }
}
