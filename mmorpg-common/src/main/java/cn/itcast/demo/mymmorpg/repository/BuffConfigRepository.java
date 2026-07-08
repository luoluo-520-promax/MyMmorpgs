/**
 * 文件说明
 * 模块：mmorpg-common / 数据仓储
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/repository/BuffConfigRepository.java
 * 类型：接口
 * 职责：Buff 配置表（buff_config）的 JPA 仓储。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.repository; // Buff 配置持久化

import cn.itcast.demo.mymmorpg.entity.BuffConfig; // Buff 模板：持续时间、叠层规则、效果类型等

import org.springframework.data.jpa.repository.JpaRepository; // 标准 CRUD

/**
 * Buff 配置数据访问接口。
 * <p>添加/更新 Buff 时按 buffTemplateId 加载配置，校验是否可驱散、最大层数等。</p>
 */
public interface BuffConfigRepository extends JpaRepository<BuffConfig, Integer> { // 主键 buffTemplateId
    // 无自定义方法：Buff 系统通过 findById 读取静态配置
}
