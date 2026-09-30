package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.*;

public interface RemoteSkillClient {
    ProtocolMessage handleGetPlayerSkills(long playerId, GetPlayerSkillsCsReq req);
    ProtocolMessage handleLearnSkill(long playerId, LearnSkillCsReq req);
    ProtocolMessage handleCastSkill(long playerId, CastSkillCsReq req);
}
