package cn.itcast.demo.mymmorpg.shop;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpCallbackShopPaymentVerifierTest {

    @Test
    public void supports_onlyWhenUrlConfigured() {
        HttpCallbackShopPaymentVerifier v = new HttpCallbackShopPaymentVerifier(
                "http://127.0.0.1:9/wechat-verify",
                "",
                "",
                "",
                new ObjectMapper());
        assertThat(v.supports("WECHAT")).isTrue();
        assertThat(v.supports("ALIPAY")).isFalse();
        assertThat(v.supports("MOCK")).isFalse();
    }

    @Test
    public void verify_failsWhenEndpointUnreachable() {
        HttpCallbackShopPaymentVerifier v = new HttpCallbackShopPaymentVerifier(
                "http://127.0.0.1:1/no-such",
                "",
                "",
                "",
                new ObjectMapper());
        ShopOrder order = new ShopOrder();
        order.orderId = "o1";
        order.playerId = 1L;
        order.payAmount = 100;
        order.channel = "WECHAT";
        ShopPaymentReceipt receipt = new ShopPaymentReceipt();
        receipt.channel = "WECHAT";
        receipt.receiptData = "x";
        receipt.channelOrderId = "c1";
        ShopPaymentVerifyResult result = v.verify(order, receipt);
        assertThat(result.ok()).isFalse();
    }
}
