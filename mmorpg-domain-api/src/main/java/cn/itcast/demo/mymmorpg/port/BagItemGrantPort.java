package cn.itcast.demo.mymmorpg.port;

import cn.itcast.demo.mymmorpg.protocol.protobuf.ItemReward;

import java.util.List;

/**
 * 通用背包幂等发货（抽卡/商城/邮件等）。实现方通常为 bag-service 的 BagService。
 */
public interface BagItemGrantPort {

    /**
     * @return {@link cn.itcast.demo.mymmorpg.protocol.BagRetCode#OK} 成功
     */
    int grantItemsIdempotent(long playerId, String idempotencyKey, List<ItemReward> rewards);
}
