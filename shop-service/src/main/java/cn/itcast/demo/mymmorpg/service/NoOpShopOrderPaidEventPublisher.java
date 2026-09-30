package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.shop.ShopOrderPaidEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpShopOrderPaidEventPublisher implements ShopOrderPaidEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NoOpShopOrderPaidEventPublisher.class);

    @Override
    public void publishPaid(long playerId, String orderId, int productId, long payAmount, String productType) {
        log.debug("NoOp ShopOrderPaid playerId={} orderId={} amount={}", playerId, orderId, payAmount);
    }
}
