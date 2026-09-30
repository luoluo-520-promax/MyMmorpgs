package cn.itcast.demo.mymmorpg.shop;

import cn.itcast.demo.mymmorpg.protocol.RetCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 开发/联调验签：接受 MOCK 渠道或显式 mock 票据前缀。
 * 生产请关闭 {@code shop.payment.mock-enabled=false}。
 */
@Component
@ConditionalOnProperty(name = "shop.payment.mock-enabled", havingValue = "true", matchIfMissing = true)
public class MockShopPaymentVerifier implements ShopPaymentVerifier {

    @Override
    public boolean supports(String channel) {
        return channel == null || channel.isBlank()
                || "MOCK".equalsIgnoreCase(channel)
                || "DEV".equalsIgnoreCase(channel);
    }

    @Override
    public ShopPaymentVerifyResult verify(ShopOrder order, ShopPaymentReceipt receipt) {
        if (order == null) {
            return ShopPaymentVerifyResult.fail(RetCode.SHOP_ORDER_NOT_FOUND, "order null");
        }
        String channelOrderId = receipt == null || receipt.channelOrderId == null || receipt.channelOrderId.isBlank()
                ? "MOCK-" + order.orderId
                : receipt.channelOrderId.trim();
        String receiptData = receipt == null ? null : receipt.receiptData;
        if (receiptData != null && !receiptData.isBlank()
                && !receiptData.startsWith("MOCK")
                && !receiptData.startsWith("mock")) {
            // 允许空票据；非 MOCK 前缀的票据在 Mock 验签器下拒绝，逼迫走真实渠道实现
            return ShopPaymentVerifyResult.fail(RetCode.SHOP_PAYMENT_VERIFY_FAILED,
                    "mock verifier rejects non-mock receipt");
        }
        if (receipt != null && receipt.payAmount > 0 && receipt.payAmount != order.payAmount) {
            return ShopPaymentVerifyResult.fail(RetCode.SHOP_PAYMENT_AMOUNT_MISMATCH,
                    "payAmount mismatch");
        }
        return ShopPaymentVerifyResult.success(channelOrderId);
    }
}
