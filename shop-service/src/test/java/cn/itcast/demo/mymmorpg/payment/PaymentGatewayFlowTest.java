package cn.itcast.demo.mymmorpg.payment;

import cn.itcast.demo.mymmorpg.shop.ShopOrder;
import cn.itcast.demo.mymmorpg.shop.ShopOrderStatus;
import cn.itcast.demo.mymmorpg.shop.ShopPaymentReceipt;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PaymentGateway 路由 + Order/Transaction 强校验流程。
 */
public class PaymentGatewayFlowTest {

    @Test
    public void router_resolvesByChannelAndAlias() {
        PaymentChannelAdapter wechat = new StubAdapter("WECHAT");
        PaymentChannelAdapter alipay = new StubAdapter("ALIPAY");
        PaymentGatewayRouter router = new PaymentGatewayRouter(
                List.of(new AdapterPaymentGateway(wechat), new AdapterPaymentGateway(alipay)),
                Map.of("WX_PAY", "WECHAT", "ALI_PAY", "ALIPAY"));

        assertThat(router.resolve("WECHAT")).isPresent();
        assertThat(router.resolve("wx_pay")).isPresent();
        assertThat(router.resolve("ALI_PAY").get().channel()).isEqualTo("ALIPAY");
        assertThat(router.resolve("UNKNOWN")).isEmpty();
    }

    @Test
    public void validateOrderAndTransaction_rejectsMismatch() {
        ShopOrder order = order("O1", "WECHAT", 100, ShopOrderStatus.CREATED.name());
        ShopPaymentReceipt receipt = receipt("O2", "WECHAT", "C1", 100);
        assertThat(AdapterPaymentGateway.validateOrderAndTransaction(order, receipt))
                .isEqualTo("orderId_mismatch");

        receipt = receipt("O1", "ALIPAY", "C1", 100);
        assertThat(AdapterPaymentGateway.validateOrderAndTransaction(order, receipt))
                .isEqualTo("channel_mismatch");

        receipt = receipt("O1", "WECHAT", "C1", 200);
        assertThat(AdapterPaymentGateway.validateOrderAndTransaction(order, receipt))
                .startsWith("amount_mismatch");

        order.status = ShopOrderStatus.DELIVERED.name();
        receipt = receipt("O1", "WECHAT", "C1", 100);
        assertThat(AdapterPaymentGateway.validateOrderAndTransaction(order, receipt))
                .startsWith("order_status_invalid");
    }

    @Test
    public void gatewayVerify_okWhenOrderMatches() {
        StubAdapter adapter = new StubAdapter("WECHAT");
        AdapterPaymentGateway gateway = new AdapterPaymentGateway(adapter);
        ShopOrder order = order("O1", "WECHAT", 88, ShopOrderStatus.CREATED.name());
        ShopPaymentReceipt receipt = receipt("O1", "WECHAT", "CH-1", 88);
        PaymentGateway.GatewayVerifyResult r = gateway.verify(order, receipt);
        assertThat(r.ok()).isTrue();
        assertThat(r.channelOrderId()).isEqualTo("CH-1");
        assertThat(r.transactionId()).isNotBlank();
    }

    private static ShopOrder order(String id, String channel, long amount, String status) {
        ShopOrder o = new ShopOrder();
        o.orderId = id;
        o.channel = channel;
        o.payAmount = amount;
        o.status = status;
        o.playerId = 1L;
        o.productId = 100;
        return o;
    }

    private static ShopPaymentReceipt receipt(String orderId, String channel, String channelOrderId, long amount) {
        return ShopPaymentReceipt.of(orderId, channel, channelOrderId, "PROD.signed", amount);
    }

    private static final class StubAdapter implements PaymentChannelAdapter {
        private final String channel;

        StubAdapter(String channel) {
            this.channel = channel;
        }

        @Override
        public String channel() {
            return channel;
        }

        @Override
        public AdapterVerifyResult verify(ShopPaymentReceipt receipt) {
            return AdapterVerifyResult.success(receipt.channelOrderId);
        }

        @Override
        public Map<String, Object> refund(String orderId, String reason) {
            return Map.of("ok", true);
        }

        @Override
        public Map<String, Object> queryOrder(String channelOrderId) {
            return Map.of("ok", true, "status", "SUCCESS");
        }
    }
}
