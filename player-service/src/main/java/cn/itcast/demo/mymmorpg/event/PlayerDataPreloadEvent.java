/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/event/PlayerDataPreloadEvent.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/event
 * 3) 主要职责：选角成功后预加载触发事件，以 playerId 为 owner 串行派发异步数据加载。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */

package cn.itcast.demo.mymmorpg.event; // player-service 领域事件与 EventBus 注册

import jforgame.commons.eventbus.BaseEvent; // 领域事件 BaseEvent 发布/订阅
/**
 * 选角成功后触发异步数据预加载。
 * 发布方：AccountPlayerService.publishPlayerDataPreloadEvent 选角完成后 eventBus.publish。
 * 消费方：PlayerDataAsyncPreloadHandler.onPlayerDataPreload 委托 PlayerDataAsyncPreloadService 并行拉取背包/技能等。
 */

public class PlayerDataPreloadEvent implements BaseEvent { // PlayerDataPreloadEvent 类型定义
    /** 已选中角色 id，与 PlayerSession 绑定一致 */

    private final long playerId; // 预加载目标角色，对应 player_bag_item / player_skill 等表
    public PlayerDataPreloadEvent(long playerId) { // 构造 PlayerDataPreloadEvent，注入 long playerId
        this.playerId = playerId; // 选角成功后携带 playerId 触发后台数据预热
    } // PlayerDataPreloadEvent 方法体结束
    /** record 风格访问器，避免与 getPlayerId JavaBean 混淆 */

    public long playerId() { // PlayerDataPreloadEvent.playerId：无参
        return playerId; // PlayerDataAsyncPreloadService.onPlayerSelected 入参
    } // playerId 方法体结束
    /**
     * 以 playerId 为 owner：同一玩家多次选角/重连的预加载任务串行，避免并发写 LoadState。
     */

    @Override // 实现接口/父类方法
    public Object getOwner() { // 读取 Owner（Owner）
        return playerId; // 同 playerId 预加载事件串行，防止 LoadState 竞态
    } // getOwner 方法体结束
} // PlayerDataPreloadEvent 类体结束
