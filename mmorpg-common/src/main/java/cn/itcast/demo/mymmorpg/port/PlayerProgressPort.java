/**
 * 文件说明
 * 模块：mmorpg-common / 端口接口
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/port/PlayerProgressPort.java
 * 类型：接口
 * 职责：定义 PlayerProgressPort，供各业务模块复用与扩展。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.port;

// 导入项目类：Player
import cn.itcast.demo.mymmorpg.entity.Player;

/**
 * 战斗结算时由 player 模块提供的进度写入能力
 */
/**
 * 接口 PlayerProgressPort：封装相关业务逻辑与数据结构。
 */
public interface PlayerProgressPort {

    /**
     * add经验；参数：Player player, int expReward
     */
    Player addExp(Player player, int expReward);

    /**
     * 消耗金币；不足时返回 null。
     */
    Player spendGold(Player player, long amount);

    /**
     * 增加金币。
     */
    Player addGold(Player player, long amount);
}
