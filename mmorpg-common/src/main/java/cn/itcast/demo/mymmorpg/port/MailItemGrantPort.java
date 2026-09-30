package cn.itcast.demo.mymmorpg.port;

import cn.itcast.demo.mymmorpg.protocol.protobuf.ItemReward;

import java.util.List;

/** 邮件附件道具发放（幂等键建议 mail:{mailId}）。 */
public interface MailItemGrantPort {

    /**
     * @return {@link cn.itcast.demo.mymmorpg.protocol.BagRetCode#OK} 成功
     */
    int grantItemsForMail(long playerId, long mailId, List<ItemReward> rewards);
}
