/**
 * 文件维护说明
 * 1) 文件路径：src/main/groovy/cn/itcast/demo/mymmorpg/script/GroovyActivityPolicy.groovy
 * 2) 所属模块：通用
 * 3) 主要职责：承载当前模块的核心实现，请优先通过公开方法进行调用与扩展。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.support

import groovy.transform.CompileStatic
import org.springframework.stereotype.Component

import java.util.Map

/**
 * Groovy 活动策略：默认可领，可在脚本中按 activityType / ctx 扩展。
 */
@Component
@CompileStatic
class GroovyActivityPolicy implements ActivityPolicy {

    @Override
    boolean allowClaimReward(int activityType, long activityId, long playerId, int rewardIndex, Map<String, Object> ctx) {
        true
    }
}

