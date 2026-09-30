package cn.itcast.demo.mymmorpg.shop;

/**
 * 可插拔支付验签：开发环境 Mock；生产对接微信/支付宝/App Store/Google Play。
 */
public interface ShopPaymentVerifier {

    /** 支持的渠道名，如 MOCK / WECHAT / ALIPAY / APP_STORE / GOOGLE_PLAY */
    boolean supports(String channel);

    ShopPaymentVerifyResult verify(ShopOrder order, ShopPaymentReceipt receipt);
}
