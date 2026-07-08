/**
 * 文件维护说明
 * 1) 文件路径：src/main/groovy/cn/itcast/demo/mymmorpg/script/GroovyItemPolicy.groovy
 * 2) 所属模块：通用
 * 3) 主要职责：承载当前模块的核心实现，请优先通过公开方法进行调用与扩展。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.support

import cn.itcast.demo.mymmorpg.entity.ItemConfig
import groovy.transform.CompileStatic
import org.springframework.stereotype.Component

import java.util.regex.Pattern

/**
 * 解析 item_config.effect_params（JSON），例如 {"exp":1000,"hp":0,"mp":0}。
 * 使用轻量正则，避免额外依赖 groovy-json。
 */
@Component
@CompileStatic
class GroovyItemPolicy implements ItemPolicy {

    @Override
    int parseExpReward(ItemConfig config) {
        parseIntField(config?.effectParams, 'exp')
    }

    @Override
    int parseHpRestore(ItemConfig config) {
        parseIntField(config?.effectParams, 'hp')
    }

    @Override
    int parseMpRestore(ItemConfig config) {
        parseIntField(config?.effectParams, 'mp')
    }

    private static int parseIntField(String json, String field) {
        if (json == null || json.isBlank()) {
            return 0
        }
        Pattern p = Pattern.compile('"' + field + '"\\s*:\\s*(\\d+)')
        def m = p.matcher(json)
        return m.find() ? Integer.parseInt(m.group(1)) : 0
    }
}
