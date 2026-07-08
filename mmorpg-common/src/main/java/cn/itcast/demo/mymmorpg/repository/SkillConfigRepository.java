/**
 * 文件说明
 * 模块：mmorpg-common / 数据仓储
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/repository/SkillConfigRepository.java
 * 类型：接口
 * 职责：技能配置表（skill_config）的 JPA 仓储，只读策划配置数据。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.repository; // 配置类仓储

import cn.itcast.demo.mymmorpg.entity.SkillConfig; // 技能静态配置：名称、CD、消耗、伤害公式等

import org.springframework.data.jpa.repository.JpaRepository; // 提供 findById(skillId) 等

/**
 * 技能配置数据访问接口。
 * <p>无自定义方法时，业务通过 {@link #findById(Object)}、{@link #findAll()} 读取配置。</p>
 */
public interface SkillConfigRepository extends JpaRepository<SkillConfig, Integer> { // 主键 skillId 为 Integer
    // 继承的 CRUD 已满足 ConfigQueryService 等按 ID 查技能配置的需求
}
