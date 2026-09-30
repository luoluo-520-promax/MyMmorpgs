package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.shop.ShopOrderPaidEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * 库存归零 → 通过 SHOP_EVENTS / stock_depleted 通知活动服刷新展示状态。
 */
@Component
public class ShopStockDepletedMqNotifier implements Consumer<ShopStockService.StockDepletedEvent> {

    private static final Logger log = LoggerFactory.getLogger(ShopStockDepletedMqNotifier.class);

    private final ObjectProvider<MqOutboxService> mqOutboxService;
    private final ObjectProvider<ShopOrderPaidEventPublisher> publisher;

    public ShopStockDepletedMqNotifier(ObjectProvider<MqOutboxService> mqOutboxService,
                                       ObjectProvider<ShopOrderPaidEventPublisher> publisher) {
        this.mqOutboxService = mqOutboxService;
        this.publisher = publisher;
    }

    @Override
    public void accept(ShopStockService.StockDepletedEvent event) {
        MqOutboxService outbox = mqOutboxService.getIfAvailable();
        if (outbox != null) {
            outbox.enqueueShopStockDepleted(event.productId(), event.atMs());
            return;
        }
        ShopOrderPaidEventPublisher pub = publisher.getIfAvailable();
        if (pub != null) {
            // 复用 publisher 扩展：无 outbox 时直接打日志，由活动服轮询兜底
            log.info("stock_depleted productId={} (no outbox, activity should poll)", event.productId());
        }
    }
}
