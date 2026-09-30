/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/service/FunctionService.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/service
 * 3) 主要职责：功能解锁业务，按配置 openType 检查条件并写入 FunctionBox、发布解锁事件。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.config.FunctionBoxStore;
import cn.itcast.demo.mymmorpg.entity.Player;
import cn.itcast.demo.mymmorpg.entity.PlayerQuestProgress;
import cn.itcast.demo.mymmorpg.event.PlayerFuncOpenEvent;
import cn.itcast.demo.mymmorpg.model.ConfigFunction;
import cn.itcast.demo.mymmorpg.model.FunctionBox;
import cn.itcast.demo.mymmorpg.model.FunctionOpenType;
import cn.itcast.demo.mymmorpg.repository.PlayerQuestProgressRepository;
import jforgame.commons.eventbus.EventBus;
import org.springframework.stereotype.Service;

import java.util.Collection;

/**
 * 功能服务：根据触发类型检查并开启功能（配置驱动）。
 */
@Service
public class FunctionService {

    /** 任务已完成或已领奖，均可作为功能解锁条件 */
    private static final int QUEST_STATUS_COMPLETED = 2;
    private static final int QUEST_STATUS_CLAIMED = 3;

    private final FunctionConfigService configService;
    private final FunctionBoxStore boxStore;
    private final EventBus eventBus;
    private final PlayerQuestProgressRepository questProgressRepository;

    public FunctionService(FunctionConfigService configService,
                           FunctionBoxStore boxStore,
                           EventBus eventBus,
                           PlayerQuestProgressRepository questProgressRepository) {
        this.configService = configService;
        this.boxStore = boxStore;
        this.eventBus = eventBus;
        this.questProgressRepository = questProgressRepository;
    }

    /**
     * 按 openType 检查并解锁功能：遍历配置，满足条件且未解锁则 open。
     *
     * @param player 当前玩家实体
     * @param type   FunctionOpenType 值，如 Level=1、Quest=2
     */
    public void checkOpen(Player player, int type) {
        if (player == null || player.getId() == null) {
            return;
        }
        long playerId = player.getId();
        FunctionBox box = boxStore.load(playerId);
        Collection<ConfigFunction> targets = configService.queryByOpenType(type);
        targets.stream()
                .filter(func -> !box.isOpened(func.getId()))
                .forEach(func -> {
                    if (type == FunctionOpenType.Level.getType()) {
                        int lv = player.getLevel() == null ? 1 : player.getLevel();
                        if (lv >= func.getOpenMainParam()) {
                            openFunc(player, box, func.getId());
                        }
                    } else if (type == FunctionOpenType.Quest.getType()) {
                        int questId = func.getOpenMainParam();
                        if (isQuestCompleted(playerId, questId)) {
                            openFunc(player, box, func.getId());
                        }
                    }
                });
        boxStore.save(playerId, box);
    }

    private boolean isQuestCompleted(long playerId, int questId) {
        return questProgressRepository.findByPlayerIdAndQuestId(playerId, questId)
                .map(PlayerQuestProgress::getStatus)
                .map(status -> status != null
                        && (status == QUEST_STATUS_COMPLETED || status == QUEST_STATUS_CLAIMED))
                .orElse(false);
    }

    private void openFunc(Player player, FunctionBox box, int funcId) {
        if (box.open(funcId)) {
            eventBus.publish(new PlayerFuncOpenEvent(player, funcId));
        }
    }
}
