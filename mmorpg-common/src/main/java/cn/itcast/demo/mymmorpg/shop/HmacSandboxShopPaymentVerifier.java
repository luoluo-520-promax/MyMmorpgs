package cn.itcast.demo.mymmorpg.shop;

import cn.itcast.demo.mymmorpg.protocol.RetCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

/**
 * 沙箱渠道验签：在配置了渠道密钥时，对 WECHAT/ALIPAY 做 HMAC-SHA256 验签。
 * <p>
 * 票据格式：{@code receiptData = "SANDBOX." + hex(hmac(orderId|payAmount|channelOrderId))}
 * 未配置对应渠道密钥时不声明支持，交给 Stub 拒绝。
 */
@Component
@Order(10)
public class HmacSandboxShopPaymentVerifier implements ShopPaymentVerifier {

    private final Map<String, String> channelSecrets;

    public HmacSandboxShopPaymentVerifier(
            @Value("${shop.payment.channel-secrets.wechat:}") String wechatSecret,
            @Value("${shop.payment.channel-secrets.alipay:}") String alipaySecret,
            @Value("${shop.payment.channel-secrets.app-store:}") String appStoreSecret,
            @Value("${shop.payment.channel-secrets.google-play:}") String googlePlaySecret) {
        this.channelSecrets = Map.of(
                "WECHAT", nullToEmpty(wechatSecret),
                "ALIPAY", nullToEmpty(alipaySecret),
                "APP_STORE", nullToEmpty(appStoreSecret),
                "GOOGLE_PLAY", nullToEmpty(googlePlaySecret));
    }

    @Override
    public boolean supports(String channel) {
        String c = normalize(channel);
        if (c == null) {
            return false;
        }
        String secret = channelSecrets.get(c);
        return StringUtils.hasText(secret);
    }

    @Override
    public ShopPaymentVerifyResult verify(ShopOrder order, ShopPaymentReceipt receipt) {
        if (order == null) {
            return ShopPaymentVerifyResult.fail(RetCode.SHOP_ORDER_NOT_FOUND, "order null");
        }
        String channel = normalize(receipt != null && StringUtils.hasText(receipt.channel)
                ? receipt.channel : order.channel);
        String secret = channel == null ? null : channelSecrets.get(channel);
        if (!StringUtils.hasText(secret)) {
            return ShopPaymentVerifyResult.fail(RetCode.SHOP_PAYMENT_VERIFY_FAILED,
                    "sandbox channel secret not configured for " + channel);
        }
        if (receipt == null || !StringUtils.hasText(receipt.channelOrderId)
                || !StringUtils.hasText(receipt.receiptData)) {
            return ShopPaymentVerifyResult.fail(RetCode.SHOP_PAYMENT_VERIFY_FAILED,
                    "sandbox receipt requires channelOrderId and receiptData");
        }
        if (receipt.payAmount > 0 && receipt.payAmount != order.payAmount) {
            return ShopPaymentVerifyResult.fail(RetCode.SHOP_PAYMENT_AMOUNT_MISMATCH, "payAmount mismatch");
        }
        String expected = "SANDBOX." + hmacHex(secret,
                order.orderId + "|" + order.payAmount + "|" + receipt.channelOrderId.trim());
        if (!expected.equalsIgnoreCase(receipt.receiptData.trim())) {
            return ShopPaymentVerifyResult.fail(RetCode.SHOP_PAYMENT_VERIFY_FAILED, "sandbox hmac mismatch");
        }
        return ShopPaymentVerifyResult.success(receipt.channelOrderId.trim());
    }

    /** 是否至少配置了一个真实/沙箱渠道密钥（供生产启动校验）。 */
    public boolean hasAnyChannelSecret() {
        return channelSecrets.values().stream().anyMatch(StringUtils::hasText);
    }

    static String sign(String secret, String orderId, long payAmount, String channelOrderId) {
        return "SANDBOX." + hmacHex(secret, orderId + "|" + payAmount + "|" + channelOrderId);
    }

    private static String hmacHex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC failed", e);
        }
    }

    private static String normalize(String channel) {
        if (channel == null || channel.isBlank()) {
            return null;
        }
        return channel.trim().toUpperCase(Locale.ROOT);
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v.trim();
    }
}
