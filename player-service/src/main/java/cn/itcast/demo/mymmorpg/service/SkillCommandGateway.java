package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SkillCommandGateway {

    private final SkillService localService;
    private final ObjectProvider<RemoteSkillClient> remoteClient;
    private final boolean remoteEnabled;

    public SkillCommandGateway(
            SkillService localService,
            ObjectProvider<RemoteSkillClient> remoteClient,
            @Value("${game.skill.remote.enabled:false}") boolean remoteEnabled) {
        this.localService = localService;
        this.remoteClient = remoteClient;
        this.remoteEnabled = remoteEnabled;
    }

    public ProtocolMessage handleGetPlayerSkills(long playerId, GetPlayerSkillsCsReq req) {
        if (useRemote()) {
            return remoteClient.getObject().handleGetPlayerSkills(playerId, req);
        }
        return localService.handleGetPlayerSkills(playerId, req);
    }

    public ProtocolMessage handleLearnSkill(long playerId, LearnSkillCsReq req) {
        if (useRemote()) {
            return remoteClient.getObject().handleLearnSkill(playerId, req);
        }
        return localService.handleLearnSkill(playerId, req);
    }

    public ProtocolMessage handleCastSkill(long playerId, CastSkillCsReq req) {
        if (useRemote()) {
            return remoteClient.getObject().handleCastSkill(playerId, req);
        }
        return localService.handleCastSkill(playerId, req);
    }

    private boolean useRemote() {
        return remoteEnabled && remoteClient.getIfAvailable() != null;
    }
}
