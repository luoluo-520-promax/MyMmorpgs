/**
 * 文件维护说明
 * 1) 文件路径：skill-service/src/main/java/cn/itcast/demo/mymmorpg/support/SkillPolicy.java
 * 2) 所属模块：skill-service / support
 * 3) 主要职责：技能伤害/治疗数值策略接口，SkillService 施法结算时调用。
 * 4) 变更建议：修改前先确认 SkillService.handleCastSkill 与 battle 结算是否共用同一策略。
 * 5) 风险提示：公式变更会影响战斗平衡，需同步回归 CastSkill 与伤害日志。
 */
package cn.itcast.demo.mymmorpg.support; // skill-service 策略接口包，GroovySkillPolicy 与 SkillPolicyConfiguration 均实现本接口

/**
 * 技能数值策略：SkillPolicyConfiguration 提供默认线性公式，GroovySkillPolicy 可热替换实现复杂公式。
 */
public interface SkillPolicy { // 策略模式接口：将伤害/治疗计算从 SkillService 业务逻辑中解耦

    /**
     * 计算技能对目标造成的伤害值。
     *
     * @param playerLevel 施法者等级，来自 player.level，参与线性伤害成长
     * @param skillId     skill_config.id，可用于按技能差异化系数
     * @param targetType  skill_config.targetType（TARGET_ENEMY 等），预留对玩家/怪物不同减伤系数
     * @return 本次施法造成的整数伤害，写入 CastSkillScRsp.damage 与 skill.cast MQ 事件
     */
    int computeDamage(int playerLevel, int skillId, int targetType); // SkillService.handleCastSkill 对敌对目标调用

    /**
     * 计算技能治疗量（对自身或友方）。
     *
     * @param playerLevel 施法者等级
     * @param skillId     skill_config.id，Groovy 实现可读配置表做技能专属治疗系数
     * @return 本次施法治疗整数，写入 CastSkillScRsp.heal 与 skill.cast MQ 事件
     */
    int computeHeal(int playerLevel, int skillId); // SkillService.handleCastSkill 对 TARGET_ALLY / TARGET_SELF 调用
}
