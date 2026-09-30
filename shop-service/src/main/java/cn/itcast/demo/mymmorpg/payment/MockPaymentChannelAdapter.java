package cn.itcast.demo.mymmorpg.payment;

import cn.itcast.demo.mymmorpg.shop.ShopPaymentReceipt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 开发/联调 Mock 渠道适配器（与 {@code shop.payment.mock-enabled} 对齐）。
 */
@Component
@ConditionalOnProperty(name = "shop.payment.mock-enabled", havingValue = "true", matchIfMissing = true)
public class MockPaymentChannelAdapter implements PaymentChannelAdapter {

    @Override
    public String channel() {
        return "MOCK";
    }

    @Override
    public AdapterVerifyResult verify(ShopPaymentReceipt receipt) {
        if (receipt == null || receipt.orderId == null || receipt.orderId.isBlank()) {
            return AdapterVerifyResult.fail("orderId required");
        }
        String channelOrderId = receipt.channelOrderId == null || receipt.channelOrderId.isBlank()
                ? "MOCK-" + receipt.orderId.trim()
                : receipt.channelOrderId.trim();
        if (receipt.receiptData != null && !receipt.receiptData.isBlank()
                && !receipt.receiptData.regionMatches(true, 0, "MOCK", 0, 4)) {
            return AdapterVerifyResult.fail("mock adapter rejects non-mock receipt");
        }
        return AdapterVerifyResult.success(channelOrderId);
    }

    @Override
    public Map<String, Object> refund(String orderId, String reason) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("channel", channel());
        out.put("orderId", orderId);
        out.put("reason", reason == null ? "" : reason);
        out.put("stub", true);
        return out;
    }

    @Override
    public Map<String, Object> queryOrder(String channelOrderId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("channel", channel());
        out.put("channelOrderId", channelOrderId);
        out.put("status", "SUCCESS");
        out.put("stub", true);
        return out;
    }
}
