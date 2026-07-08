/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/event/PlayerLevelUpEvent.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/event
 * 3) 主要职责：等级提升领域事件，携带 old/new level，驱动 FunctionConfigService 按等级解锁功能。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */

package cn.itcast.demo.mymmorpg.event; // player-service 领域事件与 EventBus 注册

import cn.itcast.demo.mymmorpg.entity.Player; // JPA 实体引用
import jforgame.commons.eventbus.BaseEvent; // 领域事件 BaseEvent 发布/订阅
import java.util.Objects; // Objects，PlayerLevelUpEvent.java 编译依赖
/**
 * 玩家等级提升时发布。
 * 发布方：PlayerProgressService.addExp 升级成功后 eventBus.publish。
 * 消费方：FunctionFacade.onPlayerLevelUp 检查 openType=Level 的功能是否 newly 解锁。
 */

public class PlayerLevelUpEvent implements BaseEvent { // PlayerLevelUpEvent 类型定义
    private final Player player; // 升级后的角色实体，含最新 level
    /** 升级前等级，用于判断跨越了哪些 unlock 阈值 */

    private final int oldLevel; // 升级前等级，对比 functions.list openMainParam 阈值
    /** 升级后等级，与 functions.list openMainParam 比较 */

    private final int newLevel; // 升级后等级，FunctionService.checkOpen 判定依据
    public PlayerLevelUpEvent(Player player, int oldLevel, int newLevel) { // 构造 PlayerLevelUpEvent，注入 Player player, int oldLevel, int newLevel
        this.player = Objects.requireNonNull(player, "player"); // 携带升级者角色
        this.oldLevel = oldLevel; // 记录升级前等级供区间判定
        this.newLevel = newLevel; // 记录升级后等级供解锁检查
    } // PlayerLevelUpEvent 方法体结束

    @Override // 实现接口/父类方法
    public Player getOwner() { // 读取 Owner（Owner）
        return player; // 同角色升级事件串行派发
    } // getOwner 方法体结束

    public int getOldLevel() { // 读取 OldLevel（OldLevel）
        return oldLevel; // 判断是否跨越某个功能解锁等级线
    } // getOldLevel 方法体结束

    public int getNewLevel() { // 读取 NewLevel（NewLevel）
        return newLevel; // 与 ConfigFunction.openMainParam 比较是否达标
    } // getNewLevel 方法体结束
} // PlayerLevelUpEvent 类体结束
