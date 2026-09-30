/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/service/FunctionFacade.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/service
 * 3) 主要职责：功能解锁事件门面，监听升级/登录/任务完成事件触发功能解锁检查。
 */
package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.Player;
import cn.itcast.demo.mymmorpg.event.PlayerLevelUpEvent;
import cn.itcast.demo.mymmorpg.event.PlayerLoginEvent;
import cn.itcast.demo.mymmorpg.event.QuestCompletedEvent;
import cn.itcast.demo.mymmorpg.model.FunctionOpenType;
import cn.itcast.demo.mymmorpg.port.PlayerCachePort;
import jforgame.commons.eventbus.Subscribe;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 事件门面：监听进度事件并触发功能解锁检查。
 */
@Component
public class FunctionFacade {

    private final FunctionService funcService;
    private final ObjectProvider<PlayerCachePort> playerCachePort;

    public FunctionFacade(FunctionService funcService, ObjectProvider<PlayerCachePort> playerCachePort) {
        this.funcService = funcService;
        this.playerCachePort = playerCachePort;
    }

    @Subscribe
    public void onPlayerLevelUp(PlayerLevelUpEvent levelUpEvent) {
        var player = levelUpEvent.getOwner();
        funcService.checkOpen(player, FunctionOpenType.Level.getType());
    }

    @Subscribe
    public void onPlayerLogin(PlayerLoginEvent loginEvent) {
        var player = loginEvent.getOwner();
        funcService.checkOpen(player, FunctionOpenType.Level.getType());
        funcService.checkOpen(player, FunctionOpenType.Quest.getType());
    }

    @Subscribe
    public void onQuestCompleted(QuestCompletedEvent event) {
        PlayerCachePort cache = playerCachePort.getIfAvailable();
        if (cache == null) {
            return;
        }
        Player player = cache.findById(event.getPlayerId());
        if (player != null) {
            funcService.checkOpen(player, FunctionOpenType.Quest.getType());
        }
    }
}
