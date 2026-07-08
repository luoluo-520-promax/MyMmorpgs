/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/service/FunctionService.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/service
 * 3) 主要职责：功能解锁业务，按配置 openType 检查条件并写入 FunctionBox、发布解锁事件。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.service; // 功能解锁核心：game.function.list 驱动 FunctionBox 持久化

import cn.itcast.demo.mymmorpg.entity.Player; // 玩家实体，含 level 等解锁判定字段
import cn.itcast.demo.mymmorpg.event.PlayerFuncOpenEvent; // 功能 newly 解锁时发布，通知客户端/UI
import cn.itcast.demo.mymmorpg.model.ConfigFunction; // 单条功能配置：id、openType、openMainParam
import cn.itcast.demo.mymmorpg.model.FunctionBox; // 玩家已解锁功能 ID 集合（持久化）
import cn.itcast.demo.mymmorpg.model.FunctionOpenType; // 解锁类型：Level=1、Quest=2
import cn.itcast.demo.mymmorpg.config.FunctionBoxStore; // 读写玩家 FunctionBox 持久化存储
import jforgame.commons.eventbus.EventBus; // 发布 PlayerFuncOpenEvent
import org.springframework.stereotype.Service; // FunctionFacade 注入本服务

import java.util.Collection; // 某 openType 下待检查的功能配置集合

/**
 * 功能服务：根据触发类型检查并开启功能（配置驱动）。
 */
@Service // 功能解锁核心逻辑，FunctionFacade 升级/登录时调用
public class FunctionService { // 按 openType 扫描 ConfigFunction 并写 FunctionBox

    /** 按 openType 索引的功能配置查询 */
    private final FunctionConfigService configService; // queryByOpenType 查 game.function.list 子集

    /** 玩家已解锁功能 ID 的读写存储（DB/Redis） */
    private final FunctionBoxStore boxStore; // load/save 玩家 FunctionBox 已解锁 funcId 集合

    /** 发布 PlayerFuncOpenEvent 供推送/日志消费 */
    private final EventBus eventBus; // newly 解锁时 publish PlayerFuncOpenEvent

    public FunctionService(FunctionConfigService configService, FunctionBoxStore boxStore, EventBus eventBus) { // 功能服务：根据触发类型检查并开启功能（配置驱动）
        this.configService = configService; // 按 openType 查 game.function.list 配置
        this.boxStore = boxStore; // 读写玩家 FunctionBox 已解锁功能 ID
        this.eventBus = eventBus; // newly 解锁时发布 PlayerFuncOpenEvent
    }

    /**
     * 按 openType 检查并解锁功能：遍历配置，满足条件且未解锁则 open。
     *
     * @param player 当前玩家实体
     * @param type   FunctionOpenType 值，如 Level=1
     */
    public void checkOpen(Player player, int type) { // 按 openType 检查并解锁功能：遍历配置，满足条件且未解锁则 open
        if (player == null || player.getId() == null) { // 无效 Player 实体
            return; // 跳过解锁检查
        }
        long playerId = player.getId(); // FunctionBox 持久化主键 player.id
        FunctionBox box = boxStore.load(playerId); // 加载该玩家已解锁 funcId 集合
        Collection<ConfigFunction> targets = configService.queryByOpenType(type); // 查该 openType 下全部 ConfigFunction
        targets.stream() // 遍历该 openType 下全部 ConfigFunction 配置
                .filter(func -> !box.isOpened(func.getId())) // 仅处理 FunctionBox 中尚未解锁的功能
                .forEach(func -> { // 逐条 ConfigFunction 按 openType 判定是否满足解锁条件
                    if (type == FunctionOpenType.Level.getType()) { // 等级型解锁 openType=1
                        int lv = player.getLevel() == null ? 1 : player.getLevel(); // 当前 player.level，null 默认 1
                        if (lv >= func.getOpenMainParam()) { // 等级达到 ConfigFunction.openMainParam 门槛
                            levelOpenFunc(player, box, func.getId()); // 写入 FunctionBox 并发布 PlayerFuncOpenEvent
                        }
                    } else if (type == FunctionOpenType.Quest.getType()) { // 任务型解锁 openType=2
                        // 任务解锁：本项目未实现任务系统，保留 questId 比对扩展点
                    }
                }); // forEach 结束：等级/任务型解锁检查完成
        boxStore.save(playerId, box); // 持久化更新后的 FunctionBox 至 DB/Redis
    }

    /** 等级型解锁：写入 FunctionBox 并发布 PlayerFuncOpenEvent */
    private void levelOpenFunc(Player player, FunctionBox box, int funcId) { // 等级型解锁：写入 FunctionBox 并发布 PlayerFuncOpenEvent
        if (box.open(funcId)) { // newly 解锁（此前 funcId 不在 FunctionBox 中）
            eventBus.publish(new PlayerFuncOpenEvent(player, funcId)); // 通知客户端展示新功能入口 UI
        }
    }
}
