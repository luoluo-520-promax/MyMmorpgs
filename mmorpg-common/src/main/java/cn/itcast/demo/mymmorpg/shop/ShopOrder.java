package cn.itcast.demo.mymmorpg.shop;

import java.util.ArrayList;
import java.util.List;

/**
 * 商城订单（Redis 热缓存 + MySQL shop_order 持久化）。
 */
public class ShopOrder {
    public String orderId;
    public long playerId;
    public int productId;
    public String productType;
    public long payAmount;
    public String currency = "CNY";
    public String channel = "MOCK";
    public String channelSku = "";
    public String channelOrderId = "";
    public String status = ShopOrderStatus.CREATED.name();
    public long createdAt;
    public long paidAt;
    public long fulfilledAt;
    public String idempotencyKey = "";
    public List<ShopRewardConfig> rewards = new ArrayList<>();
}
