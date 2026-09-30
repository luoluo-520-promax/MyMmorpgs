package cn.itcast.demo.mymmorpg.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Apple IAP 生产就绪 stub。
 * <p>
 * 真实接入时应替换为 App Store Server API / verifyReceipt SDK jar 调用；
 * 当前仅做票据结构与 HMAC 占位校验（sandbox / production 前缀切换）。
 */
@Component
public class AppleIapAdapter extends AbstractHmacPaymentChannelAdapter {

    public AppleIapAdapter(
            @Value("${shop.payment.channel-secrets.app-store:}") String secret,
            @Value("${shop.payment.adapters.apple.sandbox:true}") boolean sandbox,
            @Value("${shop.payment.adapters.apple.enabled:true}") boolean enabled) {
        super("APP_STORE", secret, sandbox, enabled);
    }
}
