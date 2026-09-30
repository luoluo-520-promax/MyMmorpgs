package cn.itcast.demo.mymmorpg.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Google Play Billing 生产就绪 stub。
 * <p>
 * 真实接入时应替换为 Google Play Developer API / Billing Library 服务端校验；
 * 当前仅做票据结构与 HMAC 占位校验（sandbox / production 前缀切换）。
 */
@Component
public class GooglePlayAdapter extends AbstractHmacPaymentChannelAdapter {

    public GooglePlayAdapter(
            @Value("${shop.payment.channel-secrets.google-play:}") String secret,
            @Value("${shop.payment.adapters.google.sandbox:true}") boolean sandbox,
            @Value("${shop.payment.adapters.google.enabled:true}") boolean enabled) {
        super("GOOGLE_PLAY", secret, sandbox, enabled);
    }
}
