package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.client.SkillCommandClient;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "game.skill.remote.enabled", havingValue = "true")
public class RemoteSkillGateway implements RemoteSkillClient {

    private final SkillCommandClient skillCommandClient;

    public RemoteSkillGateway(SkillCommandClient skillCommandClient) {
        this.skillCommandClient = skillCommandClient;
    }

    @Override
    public ProtocolMessage handleGetPlayerSkills(long playerId, GetPlayerSkillsCsReq req) {
        return new ProtocolMessage(MessageId.GET_PLAYER_SKILLS_SC_RSP, skillCommandClient.list(playerId, req.toByteArray()));
    }

    @Override
    public ProtocolMessage handleLearnSkill(long playerId, LearnSkillCsReq req) {
        return new ProtocolMessage(MessageId.LEARN_SKILL_SC_RSP, skillCommandClient.learn(playerId, req.toByteArray()));
    }

    @Override
    public ProtocolMessage handleCastSkill(long playerId, CastSkillCsReq req) {
        return new ProtocolMessage(MessageId.CAST_SKILL_SC_RSP, skillCommandClient.cast(playerId, req.toByteArray()));
    }
}
