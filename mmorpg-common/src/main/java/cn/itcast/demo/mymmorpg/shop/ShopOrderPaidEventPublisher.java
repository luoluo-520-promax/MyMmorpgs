package cn.itcast.demo.mymmorpg.shop;

/**
 * 商城订单履约成功事件（ShopOrderPaid），供活动累充等下游订阅。
 */
public interface ShopOrderPaidEventPublisher {

    void publishPaid(long playerId, String orderId, int productId, long payAmount, String productType);
}
