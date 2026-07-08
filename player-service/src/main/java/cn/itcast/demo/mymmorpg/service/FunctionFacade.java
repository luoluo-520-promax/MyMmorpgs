/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/service/FunctionFacade.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/service
 * 3) 主要职责：功能解锁事件门面，监听升级/登录事件触发等级型功能解锁检查。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.service; // EventBus 订阅 PlayerLevelUpEvent/PlayerLoginEvent 触发功能解锁

import cn.itcast.demo.mymmorpg.event.PlayerLevelUpEvent; // 玩家升级后发布，携带最新 Player 实体
import cn.itcast.demo.mymmorpg.event.PlayerLoginEvent; // 选角进游戏后发布，补检已达成但未解锁的功能
import cn.itcast.demo.mymmorpg.model.FunctionOpenType; // 解锁类型枚举，Level=1
import jforgame.commons.eventbus.Subscribe; // 标注 EventBus 订阅方法
import org.springframework.stereotype.Component; // EventBus 扫描注册 @Subscribe 订阅者

/**
 * 事件门面：监听进度事件并触发功能解锁检查。
 */
@Component // EventBus 自动发现并注册 @Subscribe 方法
public class FunctionFacade { // 升级/登录时触发 FunctionService.checkOpen(Level)

    /** 功能解锁业务服务，按 openType 检查并开启功能 */
    private final FunctionService funcService; // checkOpen 扫描 game.function.list 并写 FunctionBox

    public FunctionFacade(FunctionService funcService) { // 事件门面：监听进度事件并触发功能解锁检查
        this.funcService = funcService; // 升级/登录 EventBus 回调中执行等级型功能解锁检查
    }

    /** 玩家升级时：检查等级型功能是否 newly 达成解锁条件 */
    @Subscribe // EventBus 回调：PlayerLevelUpEvent 发布后触发
    public void onPlayerLevelUp(PlayerLevelUpEvent levelUpEvent) { // 事件门面：监听进度事件并触发功能解锁检查
        var player = levelUpEvent.getOwner(); // 取升级后的 Player 实体（含新 player.level）
        funcService.checkOpen(player, FunctionOpenType.Level.getType()); // openType=1 扫描 game.function.list 等级门槛
    }

    /** 玩家登录进游戏时：补检等级型功能（防止离线期间配置变更或数据不一致） */
    @Subscribe // EventBus 回调：PlayerLoginEvent 选角成功后触发
    public void onPlayerLogin(PlayerLoginEvent loginEvent) { // 事件门面：监听进度事件并触发功能解锁检查
        var player = loginEvent.getOwner(); // 取当前登录的 Player 实体
        funcService.checkOpen(player, FunctionOpenType.Level.getType()); // 登录补检等级解锁功能，补齐离线期间未解锁项
    }
}
