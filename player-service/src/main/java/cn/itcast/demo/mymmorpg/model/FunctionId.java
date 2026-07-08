/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/model/FunctionId.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/model
 * 3) 主要职责：功能 id 常量接口，与 functions.list 及客户端协议功能码对齐，避免魔数。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */

package cn.itcast.demo.mymmorpg.model; // player-service 功能解锁模型与配置 DTO

/**
 * 功能 ID 常量：FunctionService、ChatPolicy.isChannelUnlocked 等引用，新增功能在此追加。
 */

public interface FunctionId { // FunctionId 接口定义
    /** 坐骑系统，通常等级解锁 */

    int RIDE = 100; // 等级达标后 FunctionService 写入 FunctionBox
    /** 副本入口，等级或任务解锁 */

    int DUNGEON = 101; // 副本玩法准入检查用功能码
    /** 聊天频道扩展，未解锁时 ChatPolicy 可拒绝非世界频道 */

    int CHAT = 102; // 队伍/公会频道发送前 isChannelUnlocked 校验
} // FunctionId 类体结束
