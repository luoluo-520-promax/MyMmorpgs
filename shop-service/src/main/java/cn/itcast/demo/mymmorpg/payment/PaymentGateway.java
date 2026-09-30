package cn.itcast.demo.mymmorpg.payment;

import cn.itcast.demo.mymmorpg.shop.ShopOrder;
import cn.itcast.demo.mymmorpg.shop.ShopPaymentReceipt;

import java.util.Map;

/**
 * 支付渠道网关：生产侧 SDK 接入点。按渠道实现，由 {@link PaymentGatewayRouter} 动态路由，
 * 避免 ShopService 内 if-else 渠道分支。验签时必须做 Order / Transaction 强校验。
 */
public interface PaymentGateway {

    String channel();

    /** 是否可用于生产路由（密钥/开关就绪）。 */
    default boolean available() {
        return true;
    }

    GatewayVerifyResult verify(ShopOrder order, ShopPaymentReceipt receipt);

    Map<String, Object> refund(String orderId, String reason);

    Map<String, Object> queryTransaction(String channelOrderId);

    record GatewayVerifyResult(boolean ok, String channelOrderId, String transactionId, String message) {
        public static GatewayVerifyResult success(String channelOrderId, String transactionId) {
            return new GatewayVerifyResult(true, channelOrderId, transactionId, "ok");
        }

        public static GatewayVerifyResult fail(String message) {
            return new GatewayVerifyResult(false, null, null, message == null ? "verify failed" : message);
        }
    }
}
