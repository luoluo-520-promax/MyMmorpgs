/**
 * 文件说明
 * 模块：mmorpg-common / 端口接口
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/port/BattleScenePort.java
 * 类型：接口
 * 职责：定义 BattleScenePort，供各业务模块复用与扩展。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.port;


import java.util.Optional;

/**
 * 战斗服访问场景怪物实例的只读/移除能力
 */
/**
 * 接口 BattleScenePort：封装相关业务逻辑与数据结构。
 */
public interface BattleScenePort {

    Optional<MonsterBattleRef> findMonsterForBattle(long playerId, long enemyEntityId);

    /**
     * remove怪物from场景；参数：long playerId, long enemyEntityId
     */
    void removeMonsterFromScene(long playerId, long enemyEntityId);

    /**
     * 判断玩家in场景是否为真
     */
    boolean isPlayerInScene(long playerId);

    Optional<Integer> getEntityTypeInLine(long playerId, long entityId);

    /**
     * 接口 BattleScenePort：封装相关业务逻辑与数据结构。
     */
    record MonsterBattleRef(long enemyEntityId, int sceneId, int monsterTemplateId) {
    }
}
