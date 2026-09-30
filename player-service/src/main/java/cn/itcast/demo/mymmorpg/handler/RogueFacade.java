package cn.itcast.demo.mymmorpg.handler;

import cn.itcast.demo.mymmorpg.protocol.GamePackets;
import cn.itcast.demo.mymmorpg.protocol.Modules;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetRogueInfoCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.RogueAllocateTalentCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.RogueMoveCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.RogueQuitCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.RogueSelectBlessingCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.StartRogueCsReq;
import cn.itcast.demo.mymmorpg.service.RogueService;
import org.springframework.stereotype.Component;

@Component
@MessageRoute(module = Modules.ROGUE)
public class RogueFacade {

    private final RogueService rogueService;

    public RogueFacade(RogueService rogueService) {
        this.rogueService = rogueService;
    }

    @RequestHandler(cmd = 1)
    public ProtocolMessage start(DispatchSession session, GamePackets.StartRogueCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return rogueService.handleStart(pid, StartRogueCsReq.parseFrom(pkt.payload()));
    }

    @RequestHandler(cmd = 3)
    public ProtocolMessage info(DispatchSession session, GamePackets.GetRogueInfoCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return rogueService.handleGetInfo(pid, GetRogueInfoCsReq.parseFrom(pkt.payload()));
    }

    @RequestHandler(cmd = 5)
    public ProtocolMessage move(DispatchSession session, GamePackets.RogueMoveCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return rogueService.handleMove(pid, RogueMoveCsReq.parseFrom(pkt.payload()));
    }

    @RequestHandler(cmd = 7)
    public ProtocolMessage selectBlessing(DispatchSession session, GamePackets.RogueSelectBlessingCsReq pkt)
            throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return rogueService.handleSelectBlessing(pid, RogueSelectBlessingCsReq.parseFrom(pkt.payload()));
    }

    @RequestHandler(cmd = 9)
    public ProtocolMessage quit(DispatchSession session, GamePackets.RogueQuitCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return rogueService.handleQuit(pid, RogueQuitCsReq.parseFrom(pkt.payload()));
    }

    @RequestHandler(cmd = 12)
    public ProtocolMessage allocateTalent(DispatchSession session, GamePackets.RogueAllocateTalentCsReq pkt)
            throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return rogueService.handleAllocateTalent(pid, RogueAllocateTalentCsReq.parseFrom(pkt.payload()));
    }
}
