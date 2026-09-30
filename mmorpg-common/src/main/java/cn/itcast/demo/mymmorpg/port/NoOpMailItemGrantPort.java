package cn.itcast.demo.mymmorpg.port;

import cn.itcast.demo.mymmorpg.protocol.BagRetCode;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ItemReward;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class NoOpMailItemGrantPort implements MailItemGrantPort {

    private static final Logger log = LoggerFactory.getLogger(NoOpMailItemGrantPort.class);

    @Override
    public int grantItemsForMail(long playerId, long mailId, List<ItemReward> rewards) {
        if (rewards == null || rewards.isEmpty()) {
            return BagRetCode.OK;
        }
        log.warn("MailItemGrantPort 未实现，跳过道具发放 playerId={} mailId={} items={}",
                playerId, mailId, rewards.size());
        return BagRetCode.ITEM_UNAVAILABLE;
    }
}
