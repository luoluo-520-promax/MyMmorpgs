/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/support/BagServiceJmx.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/support
 * 3) 主要职责：JMX MBean 暴露背包容量常量与玩家占用格子数，供运维 JConsole/VisualVM 监控。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */

package cn.itcast.demo.mymmorpg.support; // player-service 业务策略接口与 JMX/ConfigManager

import cn.itcast.demo.mymmorpg.repository.PlayerBagItemRepository; // JPA Repository 仓储
import cn.itcast.demo.mymmorpg.service.BagService; // 领域 Service 委托（本文件为 Facade/Handler）
import org.springframework.jmx.export.annotation.ManagedAttribute; // ManagedAttribute，BagServiceJmx.java 编译依赖
import org.springframework.jmx.export.annotation.ManagedOperation; // ManagedOperation，BagServiceJmx.java 编译依赖
import org.springframework.jmx.export.annotation.ManagedOperationParameter; // ManagedOperationParameter，BagServiceJmx.java 编译依赖
import org.springframework.jmx.export.annotation.ManagedResource; // ManagedResource，BagServiceJmx.java 编译依赖
import org.springframework.stereotype.Component; // Spring 组件 stereotype 注解
/**
 * JMX 背包运维：联调时快速查看某 playerId 背包占用，无需写 SQL。
 */

@Component // Spring 单例组件
@ManagedResource( // @ManagedResource 注解
        objectName = "cn.itcast.demo.mymmorpg:type=BagService,name=Bag", // BagServiceJmx 逻辑
        description = "道具与背包" // BagServiceJmx 逻辑
) // BagServiceJmx 逻辑

public class BagServiceJmx { // BagServiceJmx 类型定义
    private final PlayerBagItemRepository playerBagItemRepository; // player_bag_item 背包槽位表
    public BagServiceJmx(PlayerBagItemRepository playerBagItemRepository) { // 构造 BagServiceJmx，注入 PlayerBagItemRepository playerBagItemRepository
        this.playerBagItemRepository = playerBagItemRepository; // 注入背包道具仓储
    } // BagServiceJmx 方法体结束
    /** 暴露 BagService.DEFAULT_CAPACITY，配置变更时 JMX 读数与代码同步 */

    @ManagedAttribute(description = "默认背包格子容量（与 BagService 一致）") // @ManagedAttribute 注解
    public int getDefaultBagCapacity() { // 读取 DefaultBagCapacity（DefaultBagCapacity）
        return BagService.DEFAULT_CAPACITY; // JConsole 查看默认背包格子数
    } // getDefaultBagCapacity 方法体结束
    /** 运维查询：player_bag_item 中该玩家的行数（每行一个槽位） */

    @ManagedOperation(description = "某玩家当前背包占用格子数") // @ManagedOperation 注解
    @ManagedOperationParameter(name = "playerId", description = "玩家 ID") // @ManagedOperationParameter 注解
    public int countBagSlots(long playerId) { // BagServiceJmx.countBagSlots：long playerId
        return playerBagItemRepository.countByPlayerId(playerId); // GM 排查指定玩家背包占用
    } // countBagSlots 方法体结束
} // BagServiceJmx 类体结束
