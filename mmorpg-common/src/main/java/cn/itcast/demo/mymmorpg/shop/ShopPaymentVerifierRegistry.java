package cn.itcast.demo.mymmorpg.shop;

import cn.itcast.demo.mymmorpg.protocol.RetCode;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ShopPaymentVerifierRegistry {

    private final List<ShopPaymentVerifier> verifiers;

    public ShopPaymentVerifierRegistry(List<ShopPaymentVerifier> verifiers) {
        this.verifiers = verifiers == null ? List.of() : verifiers;
    }

    public ShopPaymentVerifyResult verify(ShopOrder order, ShopPaymentReceipt receipt) {
        String channel = receipt == null || receipt.channel == null || receipt.channel.isBlank()
                ? (order == null ? "MOCK" : order.channel) : receipt.channel;
        for (ShopPaymentVerifier v : verifiers) {
            if (v.supports(channel)) {
                return v.verify(order, receipt);
            }
        }
        return ShopPaymentVerifyResult.fail(RetCode.SHOP_PAYMENT_VERIFY_FAILED,
                "unsupported channel: " + channel);
    }
}
