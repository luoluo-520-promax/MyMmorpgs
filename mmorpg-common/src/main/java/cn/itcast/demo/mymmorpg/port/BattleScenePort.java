/**
 * 战斗服访问场景怪物实例的能力：开战校验、遭遇锁定、击杀移除与回世界坐标。
 */
package cn.itcast.demo.mymmorpg.port;

import java.util.Optional;

public interface BattleScenePort {

    Optional<MonsterBattleRef> findMonsterForBattle(long playerId, long enemyEntityId);

    /**
     * 遭遇开战锁定怪物，防止多人同时对同一实体开战。
     * @return 锁定成功时的回世界坐标；失败为空
     */
    default Optional<EncounterLock> markMonsterInCombat(long playerId, long enemyEntityId) {
        return findMonsterForBattle(playerId, enemyEntityId)
                .map(r -> new EncounterLock(r.enemyEntityId(), r.sceneId(), r.monsterTemplateId(), 0f, 0f, 0f));
    }

    /** 战斗异常结束时释放锁定（未击杀）。 */
    default void releaseMonsterFromCombat(long playerId, long enemyEntityId) {
        // 默认无操作；场景实现可清除 inCombat 标记
    }

    void removeMonsterFromScene(long playerId, long enemyEntityId);

    boolean isPlayerInScene(long playerId);

    Optional<Integer> getEntityTypeInLine(long playerId, long entityId);

    record MonsterBattleRef(long enemyEntityId, int sceneId, int monsterTemplateId) {
    }

    record EncounterLock(
            long enemyEntityId,
            int sceneId,
            int monsterTemplateId,
            float returnPosX,
            float returnPosY,
            float returnPosZ) {
    }
}
