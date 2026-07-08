/**
 * 道具事件 RocketMQ 发布实现：使用/出售/丢弃背包物品后投递 ITEM_EVENTS Topic，
 * 供经济流水、任务计数、异常交易监控等下游消费。
 */
package cn.itcast.demo.mymmorpg.service;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true")
class RocketMqItemEventPublisher implements ItemEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RocketMqItemEventPublisher.class);

    private final DefaultMQProducer producer;

    @Value("${item.mq.topic:ITEM_EVENTS}")
    private String topic;

    RocketMqItemEventPublisher(DefaultMQProducer producer) {
        this.producer = producer;
    }

    @Override
    public void publishItemUsed(long playerId, long itemUid, int itemConfigId, int usedCount) {
        // tag=use：消耗品/卷轴使用，任务系统可统计「使用 N 次药水」
        send("use", "itemUse|playerId=" + playerId + "|uid=" + itemUid + "|cfg=" + itemConfigId + "|cnt=" + usedCount);
    }

    @Override
    public void publishItemSold(long playerId, long itemUid, int itemConfigId, int soldCount, long currencyGained) {
        // tag=sell：含 gold 字段，供经济监控检测异常刷金
        send("sell", "itemSell|playerId=" + playerId + "|uid=" + itemUid + "|cfg=" + itemConfigId
                + "|cnt=" + soldCount + "|gold=" + currencyGained);
    }

    @Override
    public void publishItemDiscarded(long playerId, long itemUid, int itemConfigId, int count) {
        // tag=discard：物品从背包永久移除，供审计追踪
        send("discard", "itemDiscard|playerId=" + playerId + "|uid=" + itemUid + "|cfg=" + itemConfigId + "|cnt=" + count);
    }

    private void send(String tags, String body) {
        try {
            Message msg = new Message(topic, tags, body.getBytes(StandardCharsets.UTF_8));
            producer.send(msg);
        } catch (Exception e) {
            log.warn("RocketMQ 道具事件发送失败 body={}", body, e);
        }
    }
}
