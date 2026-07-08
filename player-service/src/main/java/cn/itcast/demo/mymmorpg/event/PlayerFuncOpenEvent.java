/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/event/PlayerFuncOpenEvent.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/event
 * 3) 主要职责：功能解锁领域事件，携带 player 与 funcId，触发客户端推送与审计日志。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */

package cn.itcast.demo.mymmorpg.event; // player-service 领域事件与 EventBus 注册

import cn.itcast.demo.mymmorpg.entity.Player; // JPA 实体引用
import jforgame.commons.eventbus.BaseEvent; // 领域事件 BaseEvent 发布/订阅
import java.util.Objects; // Objects，PlayerFuncOpenEvent.java 编译依赖
/**
 * 玩家功能 newly 解锁时发布。
 * 发布方：FunctionService.open 写入 FunctionBoxStore 成功后 eventBus.publish。
 * 消费方：EventAuditHandlers.onFuncOpen 写审计日志；后续可扩展客户端 Notify 推送。
 */

public class PlayerFuncOpenEvent implements BaseEvent { // PlayerFuncOpenEvent 类型定义
    private final Player player; // 解锁功能的角色实体
    /** 对应 FunctionId 或 functions.list 中的 id，如 RIDE=100 */

    private final int funcId; // 客户端功能码，如 100=坐骑、102=聊天频道
    public PlayerFuncOpenEvent(Player player, int funcId) { // 构造 PlayerFuncOpenEvent，注入 Player player, int funcId
        this.player = Objects.requireNonNull(player, "player"); // 携带解锁者角色信息
        if (funcId <= 0) { // PlayerFuncOpenEvent.if：funcId <= 0
            throw new IllegalArgumentException("funcId must be > 0"); // 拒绝非法功能 id，防止脏数据写入 FunctionBox
        } // if 方法体结束
        this.funcId = funcId; // 记录本次 newly 解锁的功能编号
    } // PlayerFuncOpenEvent 方法体结束

    @Override // 实现接口/父类方法
    public Player getOwner() { // 读取 Owner（Owner）
        return player; // 同角色功能解锁事件串行派发
    } // getOwner 方法体结束

    public int getFuncId() { // 读取 FuncId（FuncId）
        return funcId; // 审计日志与客户端推送使用的功能 id
    } // getFuncId 方法体结束
} // PlayerFuncOpenEvent 类体结束
