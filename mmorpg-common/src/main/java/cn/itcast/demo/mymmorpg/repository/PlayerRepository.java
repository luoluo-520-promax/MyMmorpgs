/**
 * 文件说明
 * 模块：mmorpg-common / 数据仓储
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/repository/PlayerRepository.java
 * 类型：接口
 * 职责：玩家角色表（player）的 JPA 仓储，供选角、战斗、背包等业务加载角色数据。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.repository; // 仓储层包

import cn.itcast.demo.mymmorpg.entity.Player; // 玩家角色实体，含等级、账号归属等字段

import org.springframework.data.jpa.repository.JpaRepository; // JPA 标准仓储基接口

import java.util.List; // 一个账号下可有多个角色，返回列表

import java.util.Optional; // 单条查询可能不存在

/**
 * 玩家角色数据访问接口。
 */
public interface PlayerRepository extends JpaRepository<Player, Long> { // 主键 playerId 为 Long

    /**
     * 查询某账号下所有角色，按 playerId 升序（选角界面展示顺序稳定）。
     *
     * @param accountId 账号主键，对应 Player.accountId
     * @return 该账号拥有的角色列表，无角色时返回空列表（非 null）
     */
    List<Player> findByAccountIdOrderByIdAsc(Long accountId); // 派生查询：WHERE account_id = ? ORDER BY id ASC

    /**
     * 校验角色是否属于指定账号（防止越权选角或操作他人角色）。
     *
     * @param playerId  角色 ID
     * @param accountId 当前登录账号 ID
     * @return 匹配则返回角色实体，否则 empty
     */
    Optional<Player> findByIdAndAccountId(Long playerId, Long accountId); // WHERE id = ? AND account_id = ?

    boolean existsByName(String name);

    long countByAccountId(Long accountId);

    List<Player> findTop50ByOrderByLevelDesc();

    List<Player> findTop50ByOrderByPowerScoreDesc();
}
