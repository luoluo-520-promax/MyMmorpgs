/**
 * 文件说明
 * 模块：mmorpg-common / 数据仓储
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/repository/ItemConfigRepository.java
 * 类型：接口
 * 职责：道具配置表（item_config）的 JPA 仓储。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.repository; // 道具配置仓储

import cn.itcast.demo.mymmorpg.entity.ItemConfig; // 道具静态配置：类型、堆叠上限、使用效果、售价等

import org.springframework.data.jpa.repository.JpaRepository; // JPA 仓储

/**
 * 道具配置数据访问接口。
 * <p>背包展示、使用道具、出售、活动发奖时按 itemId 查配置。</p>
 */
public interface ItemConfigRepository extends JpaRepository<ItemConfig, Integer> { // 主键 itemId 为 Integer
    // 继承 findById / findAll 即可，无需派生查询
}
