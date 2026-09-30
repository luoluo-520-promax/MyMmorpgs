package cn.itcast.demo.mymmorpg.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 微信支付生产就绪 stub。
 * <p>
 * 真实接入时应替换为 wechatpay-java SDK 的验签 / 退款 / 查单 HTTP 调用；
 * 当前仅做票据结构与 HMAC 占位校验（sandbox / production 前缀切换）。
 */
@Component
public class WechatPayAdapter extends AbstractHmacPaymentChannelAdapter {

    public WechatPayAdapter(
            @Value("${shop.payment.channel-secrets.wechat:}") String secret,
            @Value("${shop.payment.adapters.wechat.sandbox:true}") boolean sandbox,
            @Value("${shop.payment.adapters.wechat.enabled:true}") boolean enabled) {
        super("WECHAT", secret, sandbox, enabled);
    }
}
