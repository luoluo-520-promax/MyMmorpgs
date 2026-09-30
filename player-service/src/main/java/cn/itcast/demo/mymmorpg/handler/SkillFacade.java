package cn.itcast.demo.mymmorpg.handler;

import cn.itcast.demo.mymmorpg.port.PlayerDataLoadPort;
import cn.itcast.demo.mymmorpg.port.PlayerDataPreloadPort;
import cn.itcast.demo.mymmorpg.protocol.GamePackets;
import cn.itcast.demo.mymmorpg.protocol.Modules;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.*;
import cn.itcast.demo.mymmorpg.service.SkillCommandGateway;
import org.springframework.stereotype.Component;

@Component
@MessageRoute(module = Modules.SKILL)
public class SkillFacade {

    private final SkillCommandGateway skillCommandGateway;
    private final PlayerDataPreloadPort preloadService;

    public SkillFacade(SkillCommandGateway skillCommandGateway, PlayerDataPreloadPort preloadService) {
        this.skillCommandGateway = skillCommandGateway;
        this.preloadService = preloadService;
    }

    @RequestHandler(cmd = 1)
    public ProtocolMessage list(DispatchSession session, GamePackets.GetPlayerSkillsCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        ProtocolMessage out = skillCommandGateway.handleGetPlayerSkills(pid, GetPlayerSkillsCsReq.parseFrom(pkt.payload()));
        GetPlayerSkillsScRsp rsp = GetPlayerSkillsScRsp.parseFrom(out.payload());
        if (rsp.getLoading()) {
            preloadService.trigger(pid, PlayerDataLoadPort.DataType.SKILL);
        }
        return out;
    }

    @RequestHandler(cmd = 3)
    public ProtocolMessage learn(DispatchSession session, GamePackets.LearnSkillCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return skillCommandGateway.handleLearnSkill(pid, LearnSkillCsReq.parseFrom(pkt.payload()));
    }

    @RequestHandler(cmd = 5)
    public ProtocolMessage cast(DispatchSession session, GamePackets.CastSkillCsReq pkt) throws Exception {
        long pid = session.playerId() != null ? session.playerId() : 0L;
        return skillCommandGateway.handleCastSkill(pid, CastSkillCsReq.parseFrom(pkt.payload()));
    }
}
