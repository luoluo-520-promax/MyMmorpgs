/**
 * 文件说明
 * 模块：mmorpg-common / 数据仓储
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/repository/MapConfigRepository.java
 * 类型：接口
 * 职责：地图/场景配置表（map_config）的 JPA 仓储。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.repository; // 场景配置仓储

import cn.itcast.demo.mymmorpg.entity.MapConfig; // 场景配置：sceneId、名称、分线数、出生点等

import org.springframework.data.jpa.repository.JpaRepository; // 标准 JPA 接口

/**
 * 地图配置数据访问接口。
 * <p>进入场景、校验 sceneId、加载地图元数据时使用 {@code findById(sceneId)}。</p>
 */
public interface MapConfigRepository extends JpaRepository<MapConfig, Integer> { // 主键 sceneId 为 Integer
    // 无额外查询：场景列表通常启动时 findAll 缓存，或按 ID 单条加载
}
