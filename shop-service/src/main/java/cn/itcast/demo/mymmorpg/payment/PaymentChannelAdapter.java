package cn.itcast.demo.mymmorpg.payment;

import cn.itcast.demo.mymmorpg.shop.ShopPaymentReceipt;

import java.util.Map;

/**
 * 支付渠道适配器 SPI：验签 / 退款 / 查单。
 * <p>
 * 生产实现为可替换 stub：配置密钥与 sandbox/production 模式后做结构 + HMAC 占位校验；
 * 真实 SDK jar 接入后替换内部 HTTP/SDK 调用即可，不必改 {@link cn.itcast.demo.mymmorpg.service.ShopService}。
 */
public interface PaymentChannelAdapter {

    String channel();

    AdapterVerifyResult verify(ShopPaymentReceipt receipt);

    Map<String, Object> refund(String orderId, String reason);

    Map<String, Object> queryOrder(String channelOrderId);

    record AdapterVerifyResult(boolean ok, String channelOrderId, String message) {
        public static AdapterVerifyResult success(String channelOrderId) {
            return new AdapterVerifyResult(true, channelOrderId, "ok");
        }

        public static AdapterVerifyResult fail(String message) {
            return new AdapterVerifyResult(false, null, message == null ? "verify failed" : message);
        }
    }
}
