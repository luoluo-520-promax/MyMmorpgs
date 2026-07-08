package cn.itcast.demo.mymmorpg.port;

import java.util.Optional;

/**
 * 战斗服独立部署且无 player-service 场景模块时的兜底实现。
 * 由 battle-service 的 {@link cn.itcast.demo.mymmorpg.config.BattlePortConfiguration} 显式注册。
 */
public class NoOpBattleScenePort implements BattleScenePort {

    @Override
    public Optional<MonsterBattleRef> findMonsterForBattle(long playerId, long enemyEntityId) {
        return Optional.empty();
    }

    @Override
    public void removeMonsterFromScene(long playerId, long enemyEntityId) {
        // no-op
    }

    @Override
    public boolean isPlayerInScene(long playerId) {
        return false;
    }

    @Override
    public Optional<Integer> getEntityTypeInLine(long playerId, long entityId) {
        return Optional.empty();
    }
}
