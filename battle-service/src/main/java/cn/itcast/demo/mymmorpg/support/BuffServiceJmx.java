/**
 * 文件说明：Buff 服务 JMX 监控与运维操作暴露类。
 * 职责：通过 JMX 暴露 Buff 配置数量，并提供施放/移除 Buff 的运维测试操作。
 */
package cn.itcast.demo.mymmorpg.support;

import cn.itcast.demo.mymmorpg.repository.BuffConfigRepository; // Buff 配置仓库
import cn.itcast.demo.mymmorpg.service.BuffService; // Buff 核心业务服务
import org.springframework.jmx.export.annotation.ManagedAttribute; // JMX 可读属性
import org.springframework.jmx.export.annotation.ManagedOperation; // JMX 可调用操作
import org.springframework.jmx.export.annotation.ManagedOperationParameter; // 操作参数描述
import org.springframework.jmx.export.annotation.ManagedResource; // JMX 资源注册
import org.springframework.stereotype.Component; // Spring 组件

/**
 * JMX：Buff 配置表与运行时施加/移除（运维测试）。
 */
@Component // 注册为 Spring Bean
@ManagedResource(
        objectName = "cn.itcast.demo.mymmorpg:type=BuffService,name=Buff", // JMX ObjectName
        description = "Buff 系统" // JMX 资源描述
)
public class BuffServiceJmx { // Buff 服务 JMX 暴露类

    /** Buff 配置数据仓库 */
    private final BuffConfigRepository buffConfigRepository; // 配置表查询

    /** Buff 核心业务服务 */
    private final BuffService buffService; // Buff 业务操作

    /**
     * 构造器注入依赖。
     *
     * @param buffConfigRepository Buff 配置仓库
     * @param buffService          Buff 业务服务
     */
    public BuffServiceJmx(BuffConfigRepository buffConfigRepository, BuffService buffService) { // 构造器注入
        this.buffConfigRepository = buffConfigRepository; // 保存配置仓库
        this.buffService = buffService; // 保存业务服务
    }

    /**
     * 获取 buff_config 表行数。
     *
     * @return 配置行数
     */
    @ManagedAttribute(description = "buff_config 行数") // JMX 可读属性
    public long getBuffConfigCount() { // 查询配置数量
        return buffConfigRepository.count(); // 统计行数
    }

    /**
     * 为实体施加 Buff（与技能效果一致）。
     *
     * @param entityId 实体 ID
     * @param buffId   buff_config.id
     */
    @ManagedOperation(description = "为实体施加 Buff（与技能效果一致）") // JMX 可调用操作
    @ManagedOperationParameter(name = "entityId", description = "实体 ID") // 参数说明
    @ManagedOperationParameter(name = "buffId", description = "buff_config.id") // 参数说明
    public void applyBuff(long entityId, int buffId) { // 施加 Buff
        buffService.applyBuff(entityId, buffId); // 委托 BuffService
    }

    /**
     * 按模板 ID 移除实体上的 Buff（主动取消原因码）。
     *
     * @param entityId 实体 ID
     * @param buffId   buff_config.id
     * @return 返回码
     */
    @ManagedOperation(description = "按模板 ID 移除实体上的 Buff（主动取消原因码）") // JMX 可调用操作
    @ManagedOperationParameter(name = "entityId", description = "实体 ID") // 参数说明
    @ManagedOperationParameter(name = "buffId", description = "buff_config.id") // 参数说明
    public int removeBuff(long entityId, int buffId) { // 移除 Buff
        return buffService.removeBuffInternal(entityId, buffId, BuffService.REMOVE_REASON_CANCEL); // 以取消原因移除
    }
}
