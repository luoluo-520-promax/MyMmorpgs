/**
 * 文件说明
 * 模块：mmorpg-common / 数据仓储
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/repository/PlayerBagItemRepository.java
 * 类型：接口
 * 职责：玩家背包物品表（player_bag_item）的 JPA 仓储。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.repository; // 背包持久化接口

import cn.itcast.demo.mymmorpg.entity.PlayerBagItem; // 背包格子条目：playerId、slotIndex、itemId、count 等

import org.springframework.data.jpa.repository.JpaRepository; // CRUD 基类

import java.util.List; // 背包多格子

import java.util.Optional; // 单条物品可能不存在

/**
 * 玩家背包物品数据访问接口。
 */
public interface PlayerBagItemRepository extends JpaRepository<PlayerBagItem, Long> { // 主键 itemUid（背包条目唯一 ID）

    /**
     * 按玩家 ID 查询全部背包物品，按格子序号升序（与客户端 UI 格子顺序一致）。
     *
     * @param playerId 当前角色 ID
     * @return 该玩家背包内所有条目
     */
    List<PlayerBagItem> findByPlayerIdOrderBySlotIndexAsc(long playerId); // WHERE player_id = ? ORDER BY slot_index ASC

    /**
     * 统计玩家当前占用的背包格子数（用于判断是否背包已满）。
     *
     * @param playerId 角色 ID
     * @return 该玩家背包条目总数
     */
    int countByPlayerId(long playerId); // SELECT COUNT(*) WHERE player_id = ?

    /**
     * 按物品 UID 与玩家 ID 联合查询（防止操作他人背包条目）。
     *
     * @param itemUid  背包条目主键（客户端 bagItemId）
     * @param playerId 当前角色 ID
     * @return 归属正确则返回条目，否则 empty
     */
    Optional<PlayerBagItem> findByIdAndPlayerId(long itemUid, long playerId); // WHERE id = ? AND player_id = ?
}
