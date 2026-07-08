/**
 * 文件说明：战斗事件发布器接口定义。
 * 职责：抽象战斗开始与结束事件的发布能力，便于在 RocketMQ 与空实现之间切换。
 * 实现类：{@link RocketMqBattleEventPublisher}、{@link NoOpBattleEventPublisher}。
 */
package cn.itcast.demo.mymmorpg.service;

/**
 * 战斗结算与生命周期事件（RocketMQ）发布接口。
 */
public interface BattleEventPublisher { // 战斗事件发布抽象接口，便于测试与切换实现

    /**
     * 发布战斗开始事件。
     *
     * @param playerId          玩家 ID
     * @param battleId          战斗 ID
     * @param sceneId           场景 ID
     * @param enemyEntityId     敌人实体 ID
     * @param monsterTemplateId 怪物模板 ID
     */
    void publishBattleStarted(long playerId, long battleId, int sceneId, long enemyEntityId, int monsterTemplateId); // 战斗开始事件

    /**
     * 发布战斗结束事件。
     *
     * @param playerId    玩家 ID
     * @param battleId    战斗 ID
     * @param result      战斗结果
     * @param expReward   经验奖励
     * @param durationSec 战斗持续秒数
     */
    void publishBattleEnded(long playerId, long battleId, int result, int expReward, int durationSec); // 战斗结束事件
}
