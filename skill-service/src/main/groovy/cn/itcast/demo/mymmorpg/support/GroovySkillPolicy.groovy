/**
 * 文件维护说明
 * 1) 文件路径：skill-service/src/main/groovy/cn/itcast/demo/mymmorpg/support/GroovySkillPolicy.groovy
 * 2) 所属模块：skill-service / groovy
 * 3) 主要职责：Groovy 实现的 SkillPolicy，覆盖 SkillPolicyConfiguration 默认线性公式。
 * 4) 变更建议：可在此注入 SkillConfigRepository 按 skillId 读取 skill_config 专属系数。
 * 5) 风险提示：公式变更影响 CastSkillScRsp 伤害/治疗值与 MQ skill.cast 事件。
 */
package cn.itcast.demo.mymmorpg.support // 与 Java SkillPolicy 同包，Spring 扫描注册为 @Component

import groovy.transform.CompileStatic // 静态编译，施法热路径无 Groovy 动态派发开销
import org.springframework.stereotype.Component // 注册 Bean，因存在则 @ConditionalOnMissingBean 默认实现不生效

@Component // Spring 单例；优先级高于 SkillPolicyConfiguration 匿名 SkillPolicy
@CompileStatic // computeDamage/computeHeal 编译为静态方法调用
class GroovySkillPolicy implements SkillPolicy { // Groovy 版伤害/治疗策略，与 Java 默认公式当前一致

    @Override // 实现 SkillPolicy.computeDamage
    int computeDamage(int playerLevel, int skillId, int targetType) { // playerLevel=施法者等级，skillId=skill_config.id，targetType 预留减伤系数
        int lv = Math.max(1, playerLevel) // 等级下限 1，与 Java 默认实现保持一致
        return 80 + lv * 12 + skillId % 50 // 伤害 = 80 + 等级×12 + skillId%50 扰动，可扩展为读 skill_config.damage 字段
    }

    @Override // 实现 SkillPolicy.computeHeal
    int computeHeal(int playerLevel, int skillId) { // 友方/自身治疗结算
        int lv = Math.max(1, playerLevel) // 等级下限 1
        return 60 + lv * 8 // 治疗 = 60 + 等级×8，可扩展为读 skill_config.heal 字段
    }
}
