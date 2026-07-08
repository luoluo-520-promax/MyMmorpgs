/**
 * 文件说明：战斗服务 JMX 监控暴露类。
 * 职责：通过 JMX 暴露战斗 ID 序列与 Redis 活跃战斗绑定数量，供运维监控使用。
 */
package cn.itcast.demo.mymmorpg.support;

import cn.itcast.demo.mymmorpg.service.BattleService; // 战斗核心业务服务
import org.springframework.jmx.export.annotation.ManagedAttribute; // JMX 可读属性注解
import org.springframework.jmx.export.annotation.ManagedResource; // JMX 资源注册注解
import org.springframework.stereotype.Component; // Spring 组件

/**
 * JMX：战斗 ID 序列与进行中战斗绑定数量监控。
 */
@Component // 注册为 Spring Bean
@ManagedResource(
        objectName = "cn.itcast.demo.mymmorpg:type=BattleService,name=Battle", // JMX ObjectName
        description = "状态同步战斗" // JMX 资源描述
)
public class BattleServiceJmx { // 战斗服务 JMX 暴露类

    /** 战斗核心业务服务 */
    private final BattleService battleService; // 委托 BattleService 查询指标

    /**
     * 构造器注入战斗服务。
     *
     * @param battleService 战斗业务服务
     */
    public BattleServiceJmx(BattleService battleService) { // 构造器注入
        this.battleService = battleService; // 保存服务引用
    }

    /**
     * 获取已分配的最大 battle_id（序列值）。
     *
     * @return 当前最大战斗 ID
     */
    @ManagedAttribute(description = "已分配的最大 battle_id（序列）") // JMX 可读属性
    public long getIssuedBattleIdMax() { // 查询最大战斗 ID
        return battleService.getIssuedBattleIdMax(); // 委托 BattleService
    }

    /**
     * 获取 Redis battle:active:* 绑定数量。
     *
     * @return 活跃战斗绑定数
     */
    @ManagedAttribute(description = "Redis battle:active:* 绑定数量") // JMX 可读属性
    public int getActiveBattleBindings() { // 查询活跃绑定数
        return battleService.countActiveBattleBindings(); // 委托 BattleService
    }
}
