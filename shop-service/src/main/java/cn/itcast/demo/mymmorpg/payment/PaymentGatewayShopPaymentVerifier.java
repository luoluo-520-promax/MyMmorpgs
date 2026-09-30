package cn.itcast.demo.mymmorpg.payment;

import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.shop.ShopOrder;
import cn.itcast.demo.mymmorpg.shop.ShopPaymentReceipt;
import cn.itcast.demo.mymmorpg.shop.ShopPaymentVerifier;
import cn.itcast.demo.mymmorpg.shop.ShopPaymentVerifyResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 优先走 {@link PaymentGatewayRouter} 的验签器：按渠道 Map 路由，无 if-else。
 * Order=20：高于 Stub(200)/Http(50) 中的部分路径，低于 HMAC sandbox(10) 与 Mock。
 */
@Component
@Order(20)
@ConditionalOnBean(PaymentGatewayRouter.class)
public class PaymentGatewayShopPaymentVerifier implements ShopPaymentVerifier {

    private final PaymentGatewayRouter router;

    public PaymentGatewayShopPaymentVerifier(PaymentGatewayRouter router) {
        this.router = router;
    }

    @Override
    public boolean supports(String channel) {
        return router.resolve(channel).isPresent();
    }

    @Override
    public ShopPaymentVerifyResult verify(ShopOrder order, ShopPaymentReceipt receipt) {
        String channel = receipt != null && receipt.channel != null && !receipt.channel.isBlank()
                ? receipt.channel
                : (order == null ? "" : order.channel);
        return router.resolve(channel)
                .map(g -> {
                    PaymentGateway.GatewayVerifyResult r = g.verify(order, receipt);
                    if (!r.ok()) {
                        return ShopPaymentVerifyResult.fail(RetCode.SHOP_PAYMENT_VERIFY_FAILED, r.message());
                    }
                    return ShopPaymentVerifyResult.success(r.channelOrderId());
                })
                .orElseGet(() -> ShopPaymentVerifyResult.fail(
                        RetCode.SHOP_PAYMENT_VERIFY_FAILED, "gateway not routed: " + channel));
    }
}
