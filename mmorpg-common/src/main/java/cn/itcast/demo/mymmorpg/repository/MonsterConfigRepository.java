/**
 * 文件说明
 * 模块：mmorpg-common / 数据仓储
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/repository/MonsterConfigRepository.java
 * 类型：接口
 * 职责：怪物配置表（monster_config）的 JPA 仓储。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.repository; // 怪物配置仓储

import cn.itcast.demo.mymmorpg.entity.MonsterConfig; // 怪物模板：HP、攻击、掉落、经验等

import org.springframework.data.jpa.repository.JpaRepository; // CRUD 基接口

import java.util.List;

/**
 * 怪物配置数据访问接口。
 * <p>战斗开战时按 {@code monsterTemplateId} 调用 {@link #findById(Object)} 加载怪物属性。</p>
 */
public interface MonsterConfigRepository extends JpaRepository<MonsterConfig, Integer> { // 主键 monsterTemplateId

    List<MonsterConfig> findByMapId(Integer mapId);
}
