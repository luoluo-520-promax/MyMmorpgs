/**
 * 文件说明
 * 模块：mmorpg-common / 端口接口
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/port/ActivityItemGrantPort.java
 * 类型：接口
 * 职责：定义 ActivityItemGrantPort，供各业务模块复用与扩展。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.port;

// 导入项目类：ItemReward
import cn.itcast.demo.mymmorpg.protocol.protobuf.ItemReward;


import java.util.List;

/**
 * 活动领奖时向玩家背包发放道具
 */
/**
 * 接口 ActivityItemGrantPort：封装相关业务逻辑与数据结构。
 */
public interface ActivityItemGrantPort {

    /**
     * 活动领奖发道具（无幂等键时不保证防重）。
     */
    default int grantItemsForActivity(long playerId, List<ItemReward> rewards) {
        return grantItemsForActivity(playerId, "", rewards);
    }

    /**
     * 活动领奖发道具；同一 playerId + idempotencyKey 只成功发一次。
     */
    int grantItemsForActivity(long playerId, String idempotencyKey, List<ItemReward> rewards);
}
