/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/event/PlayerDataAsyncPreloadHandler.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/event
 * 3) 主要职责：订阅 PlayerDataPreloadEvent，委托 PlayerDataAsyncPreloadService 异步预加载背包/技能等。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */

package cn.itcast.demo.mymmorpg.event; // player-service 领域事件与 EventBus 注册

import cn.itcast.demo.mymmorpg.service.PlayerDataAsyncPreloadService; // 领域 Service 委托（本文件为 Facade/Handler）
import jforgame.commons.eventbus.Subscribe; // Subscribe，PlayerDataAsyncPreloadHandler.java 编译依赖
import org.springframework.stereotype.Component; // Spring 组件 stereotype 注解
/**
 * EventBus 适配层：选角事件 -> 预加载服务，解耦 AccountPlayerService 与具体 preload 实现。
 * 订阅事件：PlayerDataPreloadEvent（AccountPlayerService 选角成功后发布）。
 */

@Component // Spring 单例组件

public class PlayerDataAsyncPreloadHandler { // PlayerDataAsyncPreloadHandler 类型定义
    private final PlayerDataAsyncPreloadService preloadService; // 并行拉取背包/技能/Buff 等模块数据
    public PlayerDataAsyncPreloadHandler(PlayerDataAsyncPreloadService preloadService) { // 构造 PlayerDataAsyncPreloadHandler，注入 PlayerDataAsyncPreloadService preloadService
        this.preloadService = preloadService; // 注入预加载编排服务
    } // PlayerDataAsyncPreloadHandler 方法体结束
    /**
     * 收到预加载事件：重置 LoadState 并 triggerAll，客户端进游戏前数据逐步就绪。
     */

    @Subscribe // @Subscribe 注解
    public void onPlayerDataPreload(PlayerDataPreloadEvent event) { // PlayerDataAsyncPreloadHandler.onPlayerDataPreload：PlayerDataPreloadEvent event
        if (event == null) { // PlayerDataAsyncPreloadHandler.if：event == null
            return; // 防御空事件，正常 EventBus 不会传入 null
        } // if 方法体结束
        preloadService.onPlayerSelected(event.playerId()); // @Async 并行加载 bag/skill/buff 等模块
    } // onPlayerDataPreload 方法体结束
} // PlayerDataAsyncPreloadHandler 类体结束
