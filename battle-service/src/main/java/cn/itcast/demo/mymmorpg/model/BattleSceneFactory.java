/**
 * 文件说明：战斗场景工厂类。
 * 职责：统一创建 BattleRuntimeState 对象，规范战斗状态对象的创建流程。
 * 注意：工厂仅填充基础元数据，战斗属性（血量、攻击等）由 BattleService 继续赋值。
 */
package cn.itcast.demo.mymmorpg.model;

import cn.itcast.demo.mymmorpg.service.BattleService; // 引用运行时状态内部类
import cn.itcast.demo.mymmorpg.entity.MonsterConfig; // 怪物配置实体
import cn.itcast.demo.mymmorpg.entity.Player; // 玩家实体
import org.springframework.stereotype.Component; // Spring 组件注解

/**
 * 战斗场景工厂：统一创建 BattleRuntimeState，规范对象创建流程。
 */
@Component // 注册为 Bean，供 BattleService 注入
public class BattleSceneFactory { // 领域工厂：集中创建战斗状态对象

    /**
     * 根据开战参数创建战斗运行时状态骨架。
     *
     * @param battleId       战斗 ID
     * @param sceneId        场景 ID
     * @param playerId       玩家 ID
     * @param lineupId       阵容 ID
     * @param enemyEntityId  敌人实体 ID
     * @param mc             怪物配置
     * @param player         玩家实体
     * @param enemyName      敌人名称
     * @param enemyLevel     敌人等级
     * @param expReward      经验奖励
     * @param battleType     战斗类型
     * @return 未填充血量的战斗状态对象
     */
    public BattleService.BattleRuntimeState createState(long battleId, int sceneId, long playerId, long lineupId, // 根据开战参数创建状态骨架
                                                        long enemyEntityId, MonsterConfig mc, Player player,
                                                        String enemyName, int enemyLevel, int expReward,
                                                        int battleType) {
        BattleService.BattleRuntimeState state = new BattleService.BattleRuntimeState(); // 新建空状态对象
        state.battleId = battleId; // 设置战斗 ID
        state.playerId = playerId; // 设置玩家 ID
        state.sceneId = sceneId; // 设置场景 ID
        state.lineupId = lineupId; // 设置阵容 ID
        state.battleType = battleType; // 设置战斗类型
        state.enemyEntityId = enemyEntityId; // 设置敌人实体 ID
        state.monsterTemplateId = mc.getId(); // 怪物模板 ID 来自配置
        state.expReward = expReward; // 胜利经验奖励
        state.playerName = player.getName(); // 玩家名称
        state.enemyName = enemyName; // 敌人名称
        state.playerLevel = player.getLevel() == null ? 1 : player.getLevel(); // 玩家等级，空则默认 1
        state.enemyLevel = enemyLevel; // 敌人等级
        return state; // 返回未填充血量的状态，由 BattleService 继续赋值战斗属性
    }
}
