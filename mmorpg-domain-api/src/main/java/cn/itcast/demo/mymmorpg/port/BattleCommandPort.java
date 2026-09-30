package cn.itcast.demo.mymmorpg.port;

/**
 * 战斗命令端口：player/gateway 侧通过本接口转发战斗指令，避免编译期依赖 battle-service。
 */
public interface BattleCommandPort {

    byte[] startBattle(long playerId, byte[] requestPayload);

    byte[] castSkill(long playerId, byte[] requestPayload);

    byte[] endBattle(long playerId, byte[] requestPayload);
}
