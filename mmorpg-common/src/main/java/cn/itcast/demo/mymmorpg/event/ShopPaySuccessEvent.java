package cn.itcast.demo.mymmorpg.event;

import jforgame.commons.eventbus.BaseEvent;

/**
 * 商城支付成功事件，供任务进度等订阅。
 */
public class ShopPaySuccessEvent implements BaseEvent {

    private final long playerId;
    private final String orderId;
    private final int productId;
    private final long payAmount;

    public ShopPaySuccessEvent(long playerId, String orderId, int productId, long payAmount) {
        this.playerId = playerId;
        this.orderId = orderId == null ? "" : orderId;
        this.productId = productId;
        this.payAmount = payAmount;
    }

    public long getPlayerId() {
        return playerId;
    }

    public String getOrderId() {
        return orderId;
    }

    public int getProductId() {
        return productId;
    }

    public long getPayAmount() {
        return payAmount;
    }

    @Override
    public Object getOwner() {
        return playerId;
    }
}
