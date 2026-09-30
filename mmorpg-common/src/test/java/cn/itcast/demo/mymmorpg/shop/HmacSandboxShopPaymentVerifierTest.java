package cn.itcast.demo.mymmorpg.shop;

import cn.itcast.demo.mymmorpg.protocol.RetCode;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HmacSandboxShopPaymentVerifierTest {

    @Test
    public void verify_acceptsValidSandboxHmac() {
        HmacSandboxShopPaymentVerifier verifier =
                new HmacSandboxShopPaymentVerifier("wechat-secret", "", "", "");
        assertThat(verifier.supports("WECHAT")).isTrue();
        assertThat(verifier.supports("ALIPAY")).isFalse();
        assertThat(verifier.hasAnyChannelSecret()).isTrue();

        ShopOrder order = new ShopOrder();
        order.orderId = "S1";
        order.payAmount = 600;
        order.channel = "WECHAT";

        String channelOrderId = "WX-100";
        String receiptData = HmacSandboxShopPaymentVerifier.sign("wechat-secret", "S1", 600, channelOrderId);
        ShopPaymentReceipt receipt = ShopPaymentReceipt.of("S1", "WECHAT", channelOrderId, receiptData, 600);

        ShopPaymentVerifyResult result = verifier.verify(order, receipt);
        assertThat(result.ok()).isTrue();
        assertThat(result.channelOrderId()).isEqualTo(channelOrderId);
    }

    @Test
    public void verify_rejectsBadHmac() {
        HmacSandboxShopPaymentVerifier verifier =
                new HmacSandboxShopPaymentVerifier("wechat-secret", "", "", "");
        ShopOrder order = new ShopOrder();
        order.orderId = "S1";
        order.payAmount = 600;
        order.channel = "WECHAT";
        ShopPaymentReceipt receipt = ShopPaymentReceipt.of("S1", "WECHAT", "WX-1", "SANDBOX.deadbeef", 600);

        ShopPaymentVerifyResult result = verifier.verify(order, receipt);
        assertThat(result.ok()).isFalse();
        assertThat(result.retcode()).isEqualTo(RetCode.SHOP_PAYMENT_VERIFY_FAILED);
    }
}
