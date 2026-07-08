/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/model/FunctionOpenType.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/model
 * 3) 主要职责：功能解锁触发类型枚举，与 ConfigFunction.openType 及 YAML 配置数值对应。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */

package cn.itcast.demo.mymmorpg.model; // player-service 功能解锁模型与配置 DTO

/**
 * 功能开启触发类型：FunctionConfigService 根据 openType 监听 LevelUp 或 Quest 完成事件。
 */

public enum FunctionOpenType { // FunctionOpenType 枚举
    /** openMainParam 表示所需最低等级，openSubParam 预留扩展 */

    Level(1), // 等级达标解锁，FunctionFacade 监听 PlayerLevelUpEvent / PlayerLoginEvent
    /** openMainParam 表示任务 id，任务完成回调时 unlock */

    Quest(2); // 任务完成解锁，预留 quest 系统回调
    /** 写入 YAML functions.list[].openType 的整数值 */

    private final int type; // 与 ConfigFunction.openType 字段对应
    FunctionOpenType(int type) { // 构造 FunctionOpenType，注入 int type
        this.type = type; // 枚举常量绑定配置表整型编码
    } // FunctionOpenType 方法体结束
    /** 与 ConfigFunction.getOpenType() 比较时使用 */

    public int getType() { // 读取 Type（Type）
        return type; // FunctionService.checkOpen 过滤指定解锁类型
    } // getType 方法体结束
} // FunctionOpenType 类体结束
