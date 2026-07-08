/**
 * 文件说明
 * 模块：mmorpg-common / 数据仓储
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/repository/PlayerSkillRepository.java
 * 类型：接口
 * 职责：玩家已学技能表（player_skill）的 JPA 仓储，复合主键 (playerId, skillId)。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.repository;

import cn.itcast.demo.mymmorpg.entity.PlayerSkill;
import cn.itcast.demo.mymmorpg.entity.PlayerSkillId;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 玩家已学技能数据访问接口。
 * <p>复合主键字段通过 {@code id.playerId}、{@code id.skillId} 路径，由方法名派生查询（与 {@link PlayerRepository} 一致）。</p>
 */
public interface PlayerSkillRepository extends JpaRepository<PlayerSkill, PlayerSkillId> {

    /**
     * 查询玩家全部已学技能，按学习时间升序（先学的排在前面）。
     *
     * @param playerId 角色 ID，对应嵌入主键 {@code id.playerId}
     * @return 该玩家的技能列表
     */
    List<PlayerSkill> findByIdPlayerIdOrderByLearnTimeAsc(Long playerId);

    /**
     * 判断玩家是否已学习指定技能（学习接口防重复、释放接口校验前置）。
     *
     * @param playerId 角色 ID
     * @param skillId  技能配置 ID
     * @return 已学习返回 true，否则 false
     */
    boolean existsByIdPlayerIdAndIdSkillId(Long playerId, Integer skillId);
}
