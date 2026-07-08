/**
 * 文件维护说明
 * 1) 文件路径：src/main/groovy/cn/itcast/demo/mymmorpg/script/GroovyBattlePolicy.groovy
 * 2) 所属模块：通用
 * 3) 主要职责：承载当前模块的核心实现，请优先通过公开方法进行调用与扩展。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.support

import groovy.transform.CompileStatic
import org.springframework.stereotype.Component

/**
 * Groovy 战斗伤害与治疗计算。
 */
@Component
@CompileStatic
class GroovyBattlePolicy implements BattlePolicy {

    @Override
    int computeDamage(int attackerAttack, int targetDefense, int actionType, int skillId) {
        int base = Math.max(1, attackerAttack - targetDefense)
        if (actionType == 2 && skillId > 0) {
            return (int) (base * 1.5d)
        }
        return base
    }

    @Override
    int computeHeal(int actionType, int itemId) {
        if (actionType != 3) {
            return 0
        }
        return itemId > 0 ? 150 : 100
    }
}
