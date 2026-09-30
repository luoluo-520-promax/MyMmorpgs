package cn.itcast.demo.mymmorpg.shop;

import cn.itcast.demo.mymmorpg.protocol.RetCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 渠道占位：沙箱 HMAC 与 HTTP 适配均未声明支持时承接 WECHAT/ALIPAY 等，一律拒绝验签。
 */
@Component
@Order(200)
public class StubChannelShopPaymentVerifier implements ShopPaymentVerifier {

    private final boolean productionVerifyEnabled;
    private final HmacSandboxShopPaymentVerifier sandboxVerifier;
    private final HttpCallbackShopPaymentVerifier httpVerifier;

    public StubChannelShopPaymentVerifier(
            @Value("${shop.payment.production-verify-enabled:false}") boolean productionVerifyEnabled,
            HmacSandboxShopPaymentVerifier sandboxVerifier,
            HttpCallbackShopPaymentVerifier httpVerifier) {
        this.productionVerifyEnabled = productionVerifyEnabled;
        this.sandboxVerifier = sandboxVerifier;
        this.httpVerifier = httpVerifier;
    }

    @Override
    public boolean supports(String channel) {
        if (channel == null) {
            return false;
        }
        String c = channel.trim().toUpperCase();
        if (!("WECHAT".equals(c) || "ALIPAY".equals(c)
                || "APP_STORE".equals(c) || "GOOGLE_PLAY".equals(c))) {
            return false;
        }
        return !sandboxVerifier.supports(c) && !httpVerifier.supports(c);
    }

    @Override
    public ShopPaymentVerifyResult verify(ShopOrder order, ShopPaymentReceipt receipt) {
        if (!productionVerifyEnabled) {
            return ShopPaymentVerifyResult.fail(RetCode.SHOP_PAYMENT_VERIFY_FAILED,
                    "production channel verifier not configured (set shop.payment.verify-urls.*"
                            + " or shop.payment.channel-secrets.*)");
        }
        return ShopPaymentVerifyResult.fail(RetCode.SHOP_PAYMENT_VERIFY_FAILED,
                "channel SDK not integrated; set shop.payment.verify-urls.* for HTTP adapter"
                        + " or shop.payment.channel-secrets.* for sandbox HMAC");
    }
}
