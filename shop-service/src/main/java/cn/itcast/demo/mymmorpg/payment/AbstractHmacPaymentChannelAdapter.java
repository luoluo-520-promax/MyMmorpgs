package cn.itcast.demo.mymmorpg.payment;

import cn.itcast.demo.mymmorpg.shop.ShopPaymentReceipt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 生产就绪 stub 基类：读取密钥、sandbox/production 切换、结构校验 + HMAC 占位。
 * <p>
 * 真实渠道 SDK jar 应替换 {@link #invokeChannelVerify} / {@link #invokeChannelRefund} /
 * {@link #invokeChannelQuery} 中的 HTTP 占位调用。
 */
public abstract class AbstractHmacPaymentChannelAdapter implements PaymentChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(AbstractHmacPaymentChannelAdapter.class);

    private final String channel;
    private final String secret;
    private final boolean sandbox;
    private final boolean enabled;

    protected AbstractHmacPaymentChannelAdapter(String channel, String secret, boolean sandbox, boolean enabled) {
        this.channel = channel == null ? "" : channel.trim().toUpperCase(Locale.ROOT);
        this.secret = secret == null ? "" : secret.trim();
        this.sandbox = sandbox;
        this.enabled = enabled;
    }

    @Override
    public final String channel() {
        return channel;
    }

    public boolean sandbox() {
        return sandbox;
    }

    public boolean enabled() {
        return enabled && StringUtils.hasText(secret);
    }

    @Override
    public AdapterVerifyResult verify(ShopPaymentReceipt receipt) {
        if (!enabled()) {
            return AdapterVerifyResult.fail(channel + " adapter disabled or secret missing");
        }
        if (receipt == null) {
            return AdapterVerifyResult.fail("receipt null");
        }
        if (!StringUtils.hasText(receipt.orderId)
                || !StringUtils.hasText(receipt.channelOrderId)
                || !StringUtils.hasText(receipt.receiptData)) {
            return AdapterVerifyResult.fail("receipt requires orderId, channelOrderId, receiptData");
        }
        if (StringUtils.hasText(receipt.channel)
                && !channel.equalsIgnoreCase(receipt.channel.trim())) {
            return AdapterVerifyResult.fail("channel mismatch: expected " + channel);
        }
        String expectedPrefix = sandbox ? "SANDBOX." : "PROD.";
        String data = receipt.receiptData.trim();
        if (!data.regionMatches(true, 0, expectedPrefix, 0, expectedPrefix.length())) {
            return AdapterVerifyResult.fail("receiptData must start with " + expectedPrefix
                    + " (mode=" + (sandbox ? "sandbox" : "production") + ")");
        }
        String expected = expectedPrefix + hmacHex(secret,
                receipt.orderId.trim() + "|" + receipt.payAmount + "|" + receipt.channelOrderId.trim());
        if (!expected.equalsIgnoreCase(data)) {
            // 占位：真实 SDK 在此调用渠道验票 API，而非本地 HMAC
            AdapterVerifyResult remote = invokeChannelVerify(receipt);
            if (remote != null) {
                return remote;
            }
            return AdapterVerifyResult.fail("hmac/signature mismatch");
        }
        return AdapterVerifyResult.success(receipt.channelOrderId.trim());
    }

    @Override
    public Map<String, Object> refund(String orderId, String reason) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (!enabled()) {
            out.put("ok", false);
            out.put("error", "adapter_disabled");
            return out;
        }
        if (!StringUtils.hasText(orderId)) {
            out.put("ok", false);
            out.put("error", "orderId_required");
            return out;
        }
        Map<String, Object> remote = invokeChannelRefund(orderId.trim(), reason);
        if (remote != null) {
            return remote;
        }
        out.put("ok", true);
        out.put("stub", true);
        out.put("channel", channel);
        out.put("orderId", orderId.trim());
        out.put("reason", reason == null ? "" : reason);
        out.put("mode", sandbox ? "sandbox" : "production");
        out.put("message", "refund accepted by stub; replace with real SDK HTTP call");
        return out;
    }

    @Override
    public Map<String, Object> queryOrder(String channelOrderId) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (!enabled()) {
            out.put("ok", false);
            out.put("error", "adapter_disabled");
            return out;
        }
        if (!StringUtils.hasText(channelOrderId)) {
            out.put("ok", false);
            out.put("error", "channelOrderId_required");
            return out;
        }
        Map<String, Object> remote = invokeChannelQuery(channelOrderId.trim());
        if (remote != null) {
            return remote;
        }
        out.put("ok", true);
        out.put("stub", true);
        out.put("channel", channel);
        out.put("channelOrderId", channelOrderId.trim());
        out.put("status", "UNKNOWN");
        out.put("mode", sandbox ? "sandbox" : "production");
        out.put("message", "query stub; replace with real SDK HTTP call");
        return out;
    }

    /**
     * @return null 表示继续走本地 HMAC 失败路径；非 null 则直接作为验签结果（供未来 SDK 接入）。
     */
    protected AdapterVerifyResult invokeChannelVerify(ShopPaymentReceipt receipt) {
        log.debug("{} stub skip remote verify orderId={} mode={}",
                channel, receipt.orderId, sandbox ? "sandbox" : "production");
        return null;
    }

    /** @return null 使用本地 stub 成功响应。 */
    protected Map<String, Object> invokeChannelRefund(String orderId, String reason) {
        return null;
    }

    /** @return null 使用本地 stub 成功响应。 */
    protected Map<String, Object> invokeChannelQuery(String channelOrderId) {
        return null;
    }

    protected static String hmacHex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC failed", e);
        }
    }

    /** 测试/联调：按当前模式生成占位签名票据。 */
    public String signReceipt(String orderId, long payAmount, String channelOrderId) {
        String prefix = sandbox ? "SANDBOX." : "PROD.";
        return prefix + hmacHex(secret, orderId + "|" + payAmount + "|" + channelOrderId);
    }
}
