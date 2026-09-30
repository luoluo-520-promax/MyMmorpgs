package cn.itcast.demo.mymmorpg.port;

/**
 * 技能命令端口：player 侧路由到 skill-service。
 */
public interface SkillCommandPort {

    byte[] learnSkill(long playerId, byte[] requestPayload);

    byte[] listSkills(long playerId, byte[] requestPayload);
}
