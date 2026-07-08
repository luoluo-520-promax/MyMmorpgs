/**
 * 文件说明：战斗数值策略接口。
 * 职责：抽象伤害与治疗量的计算逻辑，支持 Groovy 脚本热替换实现。
 * 默认实现：{@link cn.itcast.demo.mymmorpg.config.BattlePolicyConfiguration}。
 */
package cn.itcast.demo.mymmorpg.support;

/**
 * 战斗数值策略（Groovy 实现，便于热调）接口。
 */
public interface BattlePolicy { // 战斗数值计算策略

    /**
     * 计算伤害值。
     *
     * @param attackerAttack 攻击方攻击力
     * @param targetDefense  目标防御力
     * @param actionType     行动类型（1 普攻、2 技能、3 道具）
     * @param skillId        技能 ID
     * @return 最终伤害值
     */
    int computeDamage(int attackerAttack, int targetDefense, int actionType, int skillId); // 计算伤害

    /**
     * 计算治疗量。
     *
     * @param actionType 行动类型
     * @param itemId     道具 ID
     * @return 治疗数值
     */
    int computeHeal(int actionType, int itemId); // 计算治疗
}
