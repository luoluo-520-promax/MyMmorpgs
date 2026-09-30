package cn.itcast.demo.mymmorpg.payment;

import cn.itcast.demo.mymmorpg.shop.ShopOrder;
import cn.itcast.demo.mymmorpg.shop.ShopPaymentReceipt;

import java.util.Map;

/**
 * 将既有 {@link PaymentChannelAdapter} 适配为 {@link PaymentGateway}，
 * 并在验签前强制 Order / Transaction 强校验。
 */
public class AdapterPaymentGateway implements PaymentGateway {

    private final PaymentChannelAdapter adapter;

    public AdapterPaymentGateway(PaymentChannelAdapter adapter) {
        this.adapter = adapter;
    }

    @Override
    public String channel() {
        return adapter.channel();
    }

    @Override
    public boolean available() {
        if (adapter instanceof AbstractHmacPaymentChannelAdapter hmac) {
            return hmac.enabled();
        }
        return adapter.channel() != null && !adapter.channel().isBlank();
    }

    @Override
    public GatewayVerifyResult verify(ShopOrder order, ShopPaymentReceipt receipt) {
        String validationError = validateOrderAndTransaction(order, receipt);
        if (validationError != null) {
            return GatewayVerifyResult.fail(validationError);
        }
        PaymentChannelAdapter.AdapterVerifyResult r = adapter.verify(receipt);
        if (!r.ok()) {
            return GatewayVerifyResult.fail(r.message());
        }
        String txId = receipt.receiptData != null && !receipt.receiptData.isBlank()
                ? ("tx:" + Integer.toHexString(receipt.receiptData.hashCode()))
                : r.channelOrderId();
        return GatewayVerifyResult.success(r.channelOrderId(), txId);
    }

    @Override
    public Map<String, Object> refund(String orderId, String reason) {
        return adapter.refund(orderId, reason);
    }

    @Override
    public Map<String, Object> queryTransaction(String channelOrderId) {
        return adapter.queryOrder(channelOrderId);
    }

    /**
     * Order 与 Transaction 强校验：订单存在、状态可履约、金额/渠道一致、渠道单号非空。
     */
    public static String validateOrderAndTransaction(ShopOrder order, ShopPaymentReceipt receipt) {
        if (order == null) {
            return "order_missing";
        }
        if (receipt == null) {
            return "receipt_missing";
        }
        if (order.orderId == null || order.orderId.isBlank()
                || !order.orderId.equals(receipt.orderId)) {
            return "orderId_mismatch";
        }
        if (!"CREATED".equals(order.status) && !"PAID".equals(order.status)) {
            return "order_status_invalid:" + order.status;
        }
        if (receipt.channel != null && !receipt.channel.isBlank()
                && order.channel != null && !order.channel.isBlank()
                && !order.channel.trim().equalsIgnoreCase(receipt.channel.trim())) {
            return "channel_mismatch";
        }
        if (receipt.payAmount > 0 && order.payAmount > 0 && receipt.payAmount != order.payAmount) {
            return "amount_mismatch:order=" + order.payAmount + ",receipt=" + receipt.payAmount;
        }
        if (receipt.channelOrderId == null || receipt.channelOrderId.isBlank()) {
            return "channelOrderId_required";
        }
        String ch = receipt.channel != null && !receipt.channel.isBlank() ? receipt.channel : order.channel;
        if (ch == null || ch.isBlank()) {
            return "channel_required";
        }
        return null;
    }
}
