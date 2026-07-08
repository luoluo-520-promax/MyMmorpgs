/**
 * 技能领域事件发布抽象：学习新技能、战斗内释放技能后异步通知，
 * 供任务系统（「学会火球术」）、战斗日志、伤害统计订阅。
 */
package cn.itcast.demo.mymmorpg.service;

public interface SkillEventPublisher { // 技能事件对外发布契约：把学习与施法结果交给战斗周边系统

    /** 玩家成功学习或升级技能 */
    void publishSkillLearned(long playerId, int skillId); // 记录技能习得/升级，用于任务达成、成就与技能树解锁

    /**
     * 战斗内技能释放完成（含伤害/治疗结果）。
     *
     * @param targetEntityId 目标实体 ID（怪物或其他玩家）
     * @param damage         造成的伤害值，无伤害时为 0
     * @param heal           治疗量，纯伤害技能为 0
     */
    void publishSkillCast(long playerId, int skillId, long targetEntityId, int damage, int heal); // 记录施法结果，供战斗回放、伤害排行与治疗统计消费
}
