package cn.itcast.demo.mymmorpg.shop;

import cn.itcast.demo.mymmorpg.protocol.RetCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 渠道验签 HTTP 适配：向配置的 verify-url 转发订单/票据，由对接方封装微信/支付宝官方 SDK。
 * <p>
 * 成功条件：HTTP 2xx 且 body 含 {@code "ok": true}（或空 body）。非官方 SDK 直连。
 */
@Component
@Order(50)
public class HttpCallbackShopPaymentVerifier implements ShopPaymentVerifier {

    private static final Logger log = LoggerFactory.getLogger(HttpCallbackShopPaymentVerifier.class);

    private final Map<String, String> verifyUrls;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public HttpCallbackShopPaymentVerifier(
            @Value("${shop.payment.verify-urls.wechat:}") String wechatUrl,
            @Value("${shop.payment.verify-urls.alipay:}") String alipayUrl,
            @Value("${shop.payment.verify-urls.app-store:}") String appStoreUrl,
            @Value("${shop.payment.verify-urls.google-play:}") String googlePlayUrl,
            ObjectMapper objectMapper) {
        this.verifyUrls = Map.of(
                "WECHAT", nullToEmpty(wechatUrl),
                "ALIPAY", nullToEmpty(alipayUrl),
                "APP_STORE", nullToEmpty(appStoreUrl),
                "GOOGLE_PLAY", nullToEmpty(googlePlayUrl));
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    @Override
    public boolean supports(String channel) {
        String c = normalize(channel);
        return c != null && StringUtils.hasText(verifyUrls.getOrDefault(c, ""));
    }

    @Override
    public ShopPaymentVerifyResult verify(ShopOrder order, ShopPaymentReceipt receipt) {
        String channel = normalize(receipt != null && StringUtils.hasText(receipt.channel)
                ? receipt.channel
                : (order == null ? null : order.channel));
        if (channel == null) {
            return ShopPaymentVerifyResult.fail(RetCode.SHOP_PAYMENT_VERIFY_FAILED, "missing channel");
        }
        String url = verifyUrls.get(channel);
        if (!StringUtils.hasText(url)) {
            return ShopPaymentVerifyResult.fail(RetCode.SHOP_PAYMENT_VERIFY_FAILED, "verify url not configured");
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("channel", channel);
            body.put("orderId", order == null ? null : order.orderId);
            body.put("playerId", order == null ? null : order.playerId);
            body.put("payAmount", order == null ? null : order.payAmount);
            body.put("productId", order == null ? null : order.productId);
            body.put("receiptData", receipt == null ? null : receipt.receiptData);
            body.put("channelOrderId", receipt == null ? null : receipt.channelOrderId);
            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return ShopPaymentVerifyResult.fail(RetCode.SHOP_PAYMENT_VERIFY_FAILED,
                        "verify http " + response.statusCode());
            }
            String respBody = response.body();
            if (respBody == null || respBody.isBlank()) {
                String channelOrderId = receipt == null ? "" : nullToEmpty(receipt.channelOrderId);
                return ShopPaymentVerifyResult.success(channelOrderId);
            }
            JsonNode node = objectMapper.readTree(respBody);
            boolean ok = node.path("ok").asBoolean(false) || node.path("success").asBoolean(false);
            if (!ok) {
                String msg = node.path("message").asText("verify rejected");
                return ShopPaymentVerifyResult.fail(RetCode.SHOP_PAYMENT_VERIFY_FAILED, msg);
            }
            String channelOrderId = node.path("channelOrderId").asText(
                    receipt == null ? "" : nullToEmpty(receipt.channelOrderId));
            return ShopPaymentVerifyResult.success(channelOrderId);
        } catch (Exception e) {
            log.warn("支付 HTTP 验签失败 channel={}", channel, e);
            return ShopPaymentVerifyResult.fail(RetCode.SHOP_PAYMENT_VERIFY_FAILED, "verify callback error");
        }
    }

    private static String normalize(String channel) {
        if (channel == null || channel.isBlank()) {
            return null;
        }
        return channel.trim().toUpperCase(Locale.ROOT);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
