package cn.itcast.demo.mymmorpg.service;

/**
 * 商城库存归零事件（SHOP_EVENTS / stock_depleted）。
 */
public interface ShopStockDepletedEventPublisher {

    void publishDepleted(int productId, long atMs);
}
