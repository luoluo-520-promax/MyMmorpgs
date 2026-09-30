package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.model.ActivityRewardCommand;
import cn.itcast.demo.mymmorpg.model.RewardTierPayload;
import cn.itcast.demo.mymmorpg.port.ActivityItemGrantPort;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ItemReward;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 活动发奖履约：默认走 Outbox（ActivityRewardCommand），避免活动服直调背包；
 * 关闭开关时回退同步 HTTP（兼容单测/本地）。
 */
@Service
public class ActivityRewardFulfillmentService {

    private static final Logger log = LoggerFactory.getLogger(ActivityRewardFulfillmentService.class);

    private final ActivityItemGrantPort activityItemGrantPort;
    private final ObjectProvider<MqOutboxService> mqOutboxService;
    private final boolean outboxEnabled;

    public ActivityRewardFulfillmentService(
            ActivityItemGrantPort activityItemGrantPort,
            ObjectProvider<MqOutboxService> mqOutboxService,
            @Value("${activity.reward.outbox-enabled:false}") boolean outboxEnabled) {
        this.activityItemGrantPort = activityItemGrantPort;
        this.mqOutboxService = mqOutboxService;
        this.outboxEnabled = outboxEnabled;
    }

    /**
     * @return 0=OK；非 0=同步路径背包错误码。Outbox 路径成功返回 0（异步履约）。
     */
    public int fulfill(long playerId, long activityId, String idempotencyKey,
                       List<RewardTierPayload> tiers, List<ItemReward> grantList) {
        MqOutboxService outbox = mqOutboxService.getIfAvailable();
        if (outboxEnabled && outbox != null) {
            String itemsCsv = "";
            for (RewardTierPayload t : tiers) {
                itemsCsv = ActivityRewardCommand.appendItem(itemsCsv, t.itemId, t.count);
            }
            outbox.enqueueActivityRewardCommand(playerId, activityId, idempotencyKey, itemsCsv);
            log.info("ActivityRewardCommand enqueued playerId={} activityId={} key={}",
                    playerId, activityId, idempotencyKey);
            return 0;
        }
        return activityItemGrantPort.grantItemsForActivity(playerId, idempotencyKey, grantList);
    }
}
