/**
 * 道具事件空实现：MQ 关闭时不记录经济流水到 Broker，背包操作主流程照常完成。
 */
package cn.itcast.demo.mymmorpg.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "false", matchIfMissing = true)
class NoOpItemEventPublisher implements ItemEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NoOpItemEventPublisher.class);

    @Override
    public void publishItemUsed(long playerId, long itemUid, int itemConfigId, int usedCount) {
        log.debug("MQ 未启用，跳过 itemUse 事件 playerId={} uid={} cfg={} cnt={}", playerId, itemUid, itemConfigId, usedCount);
    }

    @Override
    public void publishItemSold(long playerId, long itemUid, int itemConfigId, int soldCount, long currencyGained) {
        log.debug("MQ 未启用，跳过 itemSell 事件 playerId={} uid={} gold={}", playerId, itemUid, currencyGained);
    }

    @Override
    public void publishItemDiscarded(long playerId, long itemUid, int itemConfigId, int count) {
        log.debug("MQ 未启用，跳过 itemDiscard 事件 playerId={} uid={} cnt={}", playerId, itemUid, count);
    }
}
