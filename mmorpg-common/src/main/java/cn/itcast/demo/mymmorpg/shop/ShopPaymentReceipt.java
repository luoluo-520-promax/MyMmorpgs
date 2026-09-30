package cn.itcast.demo.mymmorpg.shop;

/**
 * 渠道支付票据（客户端二次确认或渠道回调统一入参）。
 */
public class ShopPaymentReceipt {
    public String orderId;
    public String channel;
    public String channelOrderId;
    public String receiptData;
    public long payAmount;

    public static ShopPaymentReceipt of(String orderId, String channel, String channelOrderId,
                                        String receiptData, long payAmount) {
        ShopPaymentReceipt r = new ShopPaymentReceipt();
        r.orderId = orderId;
        r.channel = channel;
        r.channelOrderId = channelOrderId;
        r.receiptData = receiptData;
        r.payAmount = payAmount;
        return r;
    }
}
