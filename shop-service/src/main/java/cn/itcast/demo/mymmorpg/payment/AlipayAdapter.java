package cn.itcast.demo.mymmorpg.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 支付宝生产就绪 stub。
 * <p>
 * 真实接入时应替换为 alipay-sdk-java 的验签 / 退款 / 查单 HTTP 调用；
 * 当前仅做票据结构与 HMAC 占位校验（sandbox / production 前缀切换）。
 */
@Component
public class AlipayAdapter extends AbstractHmacPaymentChannelAdapter {

    public AlipayAdapter(
            @Value("${shop.payment.channel-secrets.alipay:}") String secret,
            @Value("${shop.payment.adapters.alipay.sandbox:true}") boolean sandbox,
            @Value("${shop.payment.adapters.alipay.enabled:true}") boolean enabled) {
        super("ALIPAY", secret, sandbox, enabled);
    }
}
