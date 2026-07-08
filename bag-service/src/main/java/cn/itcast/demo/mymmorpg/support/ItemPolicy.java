/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/support/ItemPolicy.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/support
 * 3) 主要职责：道具 effect_params JSON 解析策略，BagService 使用/消耗道具时读取 exp/hp/mp。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */

package cn.itcast.demo.mymmorpg.support; // player-service 业务策略接口与 JMX/ConfigManager

import cn.itcast.demo.mymmorpg.entity.ItemConfig; // JPA 实体引用
/**
 * 道具效果解析策略：GroovyItemPolicy 可热替换扩展复杂效果（随机区间、buff 触发），
 * 默认 PolicyConfiguration 用正则从 effect_params JSON 提取整数。
 */

public interface ItemPolicy { // ItemPolicy 接口定义
    /** 从 item_config.effect_params 解析单次使用获得的经验值，如 {"exp":1000} */

    int parseExpReward(ItemConfig config); // ItemPolicy 逻辑
    /** 解析恢复生命值，如 {"hp":500}，BagService 使用回血道具时调用 */

    int parseHpRestore(ItemConfig config); // ItemPolicy 逻辑
    /** 解析恢复法力值，如 {"mp":200}，BagService 使用回蓝道具时调用 */

    int parseMpRestore(ItemConfig config); // ItemPolicy 逻辑
} // ItemPolicy 类体结束
