package cn.itcast.demo.mymmorpg.port;

import cn.itcast.demo.mymmorpg.protocol.BagRetCode;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ItemReward;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 活动服独立部署时的背包发奖兜底：无 player-service 时拒绝发奖。
 * 由 activity-service 的 {@link cn.itcast.demo.mymmorpg.config.ActivityPortConfiguration} 显式注册。
 */
public class NoOpActivityItemGrantPort implements ActivityItemGrantPort {

    private static final Logger log = LoggerFactory.getLogger(NoOpActivityItemGrantPort.class);

    @Override
    public int grantItemsForActivity(long playerId, String idempotencyKey, List<ItemReward> rewards) {
        log.warn("ActivityItemGrantPort 未实现，无法发放活动奖励 playerId={} key={} items={}",
                playerId, idempotencyKey, rewards == null ? 0 : rewards.size());
        return BagRetCode.ITEM_UNAVAILABLE;
    }
}
