/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/event/PlayerLoginEvent.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/event
 * 3) 主要职责：选角成功领域事件，携带 Player 实体供 EventBus 订阅者做登录后逻辑。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */

package cn.itcast.demo.mymmorpg.event; // player-service 领域事件与 EventBus 注册

import cn.itcast.demo.mymmorpg.entity.Player; // JPA 实体引用
import jforgame.commons.eventbus.BaseEvent; // 领域事件 BaseEvent 发布/订阅
import java.util.Objects; // Objects，PlayerLoginEvent.java 编译依赖
/**
 * 玩家成功选角/进入会话后发布。
 * 发布方：AccountPlayerService.publishPlayerLoginEvent 选角成功后 eventBus.publish。
 * 消费方：FunctionFacade.onPlayerLogin 补检等级型功能解锁；EventAuditHandlers.onPlayerLogin 写审计日志。
 */

public class PlayerLoginEvent implements BaseEvent { // PlayerLoginEvent 类型定义
    /** 不可变：事件发布后 Player 实体不应被订阅者修改，避免与 Session 状态不一致 */

    private final Player player; // 当前选中角色快照，含 level/exp 等进度字段
    public PlayerLoginEvent(Player player) { // 构造 PlayerLoginEvent，注入 Player player
        this.player = Objects.requireNonNull(player, "player"); // 选角成功后携带完整 Player 实体
    } // PlayerLoginEvent 方法体结束
    /** EventBus owner 路由键：同一 Player 的事件可串行派发，避免并发改同一角色状态 */

    @Override // 实现接口/父类方法
    public Player getOwner() { // 读取 Owner（Owner）
        return player; // 以 Player 实例为 owner，同角色事件串行处理
    } // getOwner 方法体结束
} // PlayerLoginEvent 类体结束
