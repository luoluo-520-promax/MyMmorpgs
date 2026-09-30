package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.client.ShopActivityRechargeClient;
import cn.itcast.demo.mymmorpg.client.ShopBagGrantClient;
import cn.itcast.demo.mymmorpg.client.ShopSkinGrantClient;
import cn.itcast.demo.mymmorpg.entity.ShopOrderRecord;
import cn.itcast.demo.mymmorpg.port.PlayerNotificationPort;
import cn.itcast.demo.mymmorpg.repository.ShopOrderRecordRepository;
import cn.itcast.demo.mymmorpg.skin.SkinConfig;
import cn.itcast.demo.mymmorpg.skin.SkinConfigRepository;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.protocol.protobuf.CreateShopOrderCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.CreateShopOrderScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetShopOrderCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetShopOrderScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetShopPurchaseHistoryCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetShopPurchaseHistoryScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetShopShelfCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetShopShelfScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ShopOrderPaidScNotify;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ShopProductInfo;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ShopPurchaseRecord;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ShopRewardItem;
import cn.itcast.demo.mymmorpg.shop.ChannelBillProvider;
import cn.itcast.demo.mymmorpg.shop.ShopConfigService;
import cn.itcast.demo.mymmorpg.shop.ShopLimitType;
import cn.itcast.demo.mymmorpg.shop.ShopOrder;
import cn.itcast.demo.mymmorpg.shop.ShopOrderPaidEventPublisher;
import cn.itcast.demo.mymmorpg.shop.ShopOrderStatus;
import cn.itcast.demo.mymmorpg.shop.ShopPaymentReceipt;
import cn.itcast.demo.mymmorpg.shop.ShopPaymentVerifierRegistry;
import cn.itcast.demo.mymmorpg.shop.ShopPaymentVerifyResult;
import cn.itcast.demo.mymmorpg.shop.ShopProductConfig;
import cn.itcast.demo.mymmorpg.shop.ShopProductType;
import cn.itcast.demo.mymmorpg.shop.ShopRewardConfig;
import cn.itcast.demo.mymmorpg.support.ShopMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 商城货架 / 下单 / 验签履约 / 退款（可嵌入 player 或独立 shop-service）。
 */
@Service
public class ShopService {

    private static final Logger log = LoggerFactory.getLogger(ShopService.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final String ORDER_KEY = "shop:order:";
    private static final String PLAYER_ORDERS_KEY = "shop:player:orders:";
    private static final String COUNTER_KEY = "shop:limit:";
    private static final String IDEM_KEY = "shop:idem:";
    private static final String CHANNEL_ORDER_KEY = "shop:channelOrder:";
    private static final String REFUND_KEY = "shop:refund:";
    private static final Duration ORDER_TTL = Duration.ofDays(30);

    private final ShopConfigService shopConfigService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<ShopBagGrantClient> bagGrantClient;
    private final ObjectProvider<ShopActivityRechargeClient> rechargeClient;
    private final ObjectProvider<ShopSkinGrantClient> skinGrantClient;
    private final ObjectProvider<SkinConfigRepository> skinConfigRepository;
    private final ObjectProvider<PlayerNotificationPort> notificationPort;
    private final ShopOrderPaidEventPublisher shopOrderPaidEventPublisher;
    private final ShopPaymentVerifierRegistry paymentVerifierRegistry;
    private final ObjectProvider<ShopMetrics> shopMetrics;
    private final ObjectProvider<ShopOrderRecordRepository> shopOrderRecordRepository;
    private final ObjectProvider<MqOutboxService> mqOutboxService;
    private final ObjectProvider<ChannelBillProvider> channelBillProviders;
    private final ObjectProvider<PassService> passService;
    private final ObjectProvider<ShopStockService> shopStockService;
    private final boolean mockPaymentEnabled;
    private final int passSeasonId;

    public ShopService(ShopConfigService shopConfigService,
                       StringRedisTemplate stringRedisTemplate,
                       ObjectMapper objectMapper,
                       ObjectProvider<ShopBagGrantClient> bagGrantClient,
                       ObjectProvider<ShopActivityRechargeClient> rechargeClient,
                       ObjectProvider<ShopSkinGrantClient> skinGrantClient,
                       ObjectProvider<SkinConfigRepository> skinConfigRepository,
                       ObjectProvider<PlayerNotificationPort> notificationPort,
                       ShopOrderPaidEventPublisher shopOrderPaidEventPublisher,
                       ShopPaymentVerifierRegistry paymentVerifierRegistry,
                       ObjectProvider<ShopMetrics> shopMetrics,
                       ObjectProvider<ShopOrderRecordRepository> shopOrderRecordRepository,
                       ObjectProvider<MqOutboxService> mqOutboxService,
                       @Value("${shop.payment.mock-enabled:true}") boolean mockPaymentEnabled) {
        this(shopConfigService, stringRedisTemplate, objectMapper, bagGrantClient, rechargeClient,
                skinGrantClient, skinConfigRepository, notificationPort, shopOrderPaidEventPublisher,
                paymentVerifierRegistry, shopMetrics, shopOrderRecordRepository, mqOutboxService,
                null, null, null, mockPaymentEnabled, 1);
    }

    /** 单测兼容：带渠道账单 Provider，无战令 Bean。 */
    public ShopService(ShopConfigService shopConfigService,
                       StringRedisTemplate stringRedisTemplate,
                       ObjectMapper objectMapper,
                       ObjectProvider<ShopBagGrantClient> bagGrantClient,
                       ObjectProvider<ShopActivityRechargeClient> rechargeClient,
                       ObjectProvider<ShopSkinGrantClient> skinGrantClient,
                       ObjectProvider<SkinConfigRepository> skinConfigRepository,
                       ObjectProvider<PlayerNotificationPort> notificationPort,
                       ShopOrderPaidEventPublisher shopOrderPaidEventPublisher,
                       ShopPaymentVerifierRegistry paymentVerifierRegistry,
                       ObjectProvider<ShopMetrics> shopMetrics,
                       ObjectProvider<ShopOrderRecordRepository> shopOrderRecordRepository,
                       ObjectProvider<MqOutboxService> mqOutboxService,
                       ObjectProvider<ChannelBillProvider> channelBillProviders,
                       boolean mockPaymentEnabled) {
        this(shopConfigService, stringRedisTemplate, objectMapper, bagGrantClient, rechargeClient,
                skinGrantClient, skinConfigRepository, notificationPort, shopOrderPaidEventPublisher,
                paymentVerifierRegistry, shopMetrics, shopOrderRecordRepository, mqOutboxService,
                channelBillProviders, null, null, mockPaymentEnabled, 1);
    }

    /** 单测兼容：带战令，无库存服务。 */
    public ShopService(ShopConfigService shopConfigService,
                       StringRedisTemplate stringRedisTemplate,
                       ObjectMapper objectMapper,
                       ObjectProvider<ShopBagGrantClient> bagGrantClient,
                       ObjectProvider<ShopActivityRechargeClient> rechargeClient,
                       ObjectProvider<ShopSkinGrantClient> skinGrantClient,
                       ObjectProvider<SkinConfigRepository> skinConfigRepository,
                       ObjectProvider<PlayerNotificationPort> notificationPort,
                       ShopOrderPaidEventPublisher shopOrderPaidEventPublisher,
                       ShopPaymentVerifierRegistry paymentVerifierRegistry,
                       ObjectProvider<ShopMetrics> shopMetrics,
                       ObjectProvider<ShopOrderRecordRepository> shopOrderRecordRepository,
                       ObjectProvider<MqOutboxService> mqOutboxService,
                       ObjectProvider<ChannelBillProvider> channelBillProviders,
                       ObjectProvider<PassService> passService,
                       boolean mockPaymentEnabled,
                       int passSeasonId) {
        this(shopConfigService, stringRedisTemplate, objectMapper, bagGrantClient, rechargeClient,
                skinGrantClient, skinConfigRepository, notificationPort, shopOrderPaidEventPublisher,
                paymentVerifierRegistry, shopMetrics, shopOrderRecordRepository, mqOutboxService,
                channelBillProviders, passService, null, mockPaymentEnabled, passSeasonId);
    }

    @Autowired
    public ShopService(ShopConfigService shopConfigService,
                       StringRedisTemplate stringRedisTemplate,
                       ObjectMapper objectMapper,
                       ObjectProvider<ShopBagGrantClient> bagGrantClient,
                       ObjectProvider<ShopActivityRechargeClient> rechargeClient,
                       ObjectProvider<ShopSkinGrantClient> skinGrantClient,
                       ObjectProvider<SkinConfigRepository> skinConfigRepository,
                       ObjectProvider<PlayerNotificationPort> notificationPort,
                       ShopOrderPaidEventPublisher shopOrderPaidEventPublisher,
                       ShopPaymentVerifierRegistry paymentVerifierRegistry,
                       ObjectProvider<ShopMetrics> shopMetrics,
                       ObjectProvider<ShopOrderRecordRepository> shopOrderRecordRepository,
                       ObjectProvider<MqOutboxService> mqOutboxService,
                       ObjectProvider<ChannelBillProvider> channelBillProviders,
                       ObjectProvider<PassService> passService,
                       ObjectProvider<ShopStockService> shopStockService,
                       @Value("${shop.payment.mock-enabled:true}") boolean mockPaymentEnabled,
                       @Value("${game.pass.season-id:1}") int passSeasonId) {
        this.shopConfigService = shopConfigService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.bagGrantClient = bagGrantClient;
        this.rechargeClient = rechargeClient;
        this.skinGrantClient = skinGrantClient;
        this.skinConfigRepository = skinConfigRepository;
        this.notificationPort = notificationPort;
        this.shopOrderPaidEventPublisher = shopOrderPaidEventPublisher;
        this.paymentVerifierRegistry = paymentVerifierRegistry;
        this.shopMetrics = shopMetrics;
        this.shopOrderRecordRepository = shopOrderRecordRepository;
        this.mqOutboxService = mqOutboxService;
        this.channelBillProviders = channelBillProviders;
        this.passService = passService;
        this.shopStockService = shopStockService;
        this.mockPaymentEnabled = mockPaymentEnabled;
        this.passSeasonId = Math.max(1, passSeasonId);
    }

    public ProtocolMessage handleGetShelf(long playerId, GetShopShelfCsReq req) {
        if (playerId <= 0) {
            return shelfRsp(RetCode.PLAYER_NOT_SELECTED, req.getTabId(), List.of(), shopConfigService.version());
        }
        long now = System.currentTimeMillis();
        List<ShopProductInfo> products = new ArrayList<>();
        for (ShopProductConfig cfg : shopConfigService.listOnSale(req.getTabId(), now)) {
            int bought = getBoughtCount(playerId, cfg, now);
            int remain = remainCount(cfg, bought);
            boolean firstDouble = isFirstChargeDoubleEligible(playerId, cfg);
            products.add(toProductInfo(cfg, remain, firstDouble));
        }
        return shelfRsp(RetCode.OK, req.getTabId(), products, shopConfigService.version());
    }

    public ProtocolMessage handleCreateOrder(long playerId, CreateShopOrderCsReq req) {
        if (playerId <= 0) {
            return createRsp(RetCode.PLAYER_NOT_SELECTED, null);
        }
        Optional<ShopProductConfig> opt = shopConfigService.findById(req.getProductId());
        if (opt.isEmpty()) {
            return createRsp(RetCode.SHOP_PRODUCT_NOT_FOUND, null);
        }
        ShopProductConfig cfg = opt.get();
        long now = System.currentTimeMillis();
        if (!cfg.isOnSale(now)) {
            return createRsp(RetCode.SHOP_PRODUCT_NOT_ON_SALE, null);
        }
        if (cfg.typeEnum() == ShopProductType.DISCOUNT_PACK && !cfg.isDiscountWindowActive(now)) {
            return createRsp(RetCode.SHOP_DISCOUNT_EXPIRED, null);
        }
        int bought = getBoughtCount(playerId, cfg, now);
        if (remainCount(cfg, bought) == 0) {
            return createRsp(RetCode.SHOP_LIMIT_EXCEEDED, null);
        }
        String idem = req.getIdempotencyKey() == null || req.getIdempotencyKey().isBlank()
                ? null : req.getIdempotencyKey().trim();
        if (idem != null) {
            String existing = stringRedisTemplate.opsForValue().get(IDEM_KEY + playerId + ":" + idem);
            if (existing != null) {
                ShopOrder prev = loadOrder(existing);
                if (prev != null) {
                    return createRsp(RetCode.OK, prev);
                }
            }
        }
        ShopOrder order = new ShopOrder();
        order.orderId = "S" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8);
        order.playerId = playerId;
        order.productId = cfg.productId;
        order.productType = cfg.productType;
        order.payAmount = cfg.price;
        order.currency = cfg.currency == null ? "CNY" : cfg.currency;
        String channel = req.getChannel() == null || req.getChannel().isBlank() ? "MOCK" : req.getChannel().trim();
        if (!mockPaymentEnabled && ("MOCK".equalsIgnoreCase(channel) || "DEV".equalsIgnoreCase(channel))) {
            return createRsp(RetCode.SHOP_PAYMENT_VERIFY_FAILED, null);
        }
        order.channel = channel;
        order.channelSku = cfg.channelSku;
        order.status = ShopOrderStatus.CREATED.name();
        order.createdAt = now;
        order.idempotencyKey = idem == null ? "" : idem;
        order.rewards = cfg.rewards == null ? List.of() : new ArrayList<>(cfg.rewards);
        saveOrder(order);
        if (idem != null) {
            stringRedisTemplate.opsForValue().set(IDEM_KEY + playerId + ":" + idem, order.orderId, ORDER_TTL);
        }
        stringRedisTemplate.opsForList().leftPush(PLAYER_ORDERS_KEY + playerId, order.orderId);
        stringRedisTemplate.expire(PLAYER_ORDERS_KEY + playerId, ORDER_TTL);
        ShopMetrics metrics = shopMetrics.getIfAvailable();
        if (metrics != null) {
            metrics.recordOrderCreated();
        }
        return createRsp(RetCode.OK, order);
    }

    /** 供运维 / 测试加载订单。 */
    public ShopOrder findOrder(String orderId) {
        return loadOrder(orderId);
    }

    public ProtocolMessage handleGetOrder(long playerId, GetShopOrderCsReq req) {
        if (playerId <= 0) {
            return orderRsp(RetCode.PLAYER_NOT_SELECTED, null);
        }
        ShopOrder order = loadOrder(req.getOrderId());
        if (order == null || order.playerId != playerId) {
            return orderRsp(RetCode.SHOP_ORDER_NOT_FOUND, null);
        }
        return orderRsp(RetCode.OK, order);
    }

    public ProtocolMessage handlePurchaseHistory(long playerId, GetShopPurchaseHistoryCsReq req) {
        if (playerId <= 0) {
            return historyRsp(RetCode.PLAYER_NOT_SELECTED, List.of());
        }
        int limit = req.getLimit() <= 0 ? 20 : Math.min((int) req.getLimit(), 100);
        List<String> ids = stringRedisTemplate.opsForList().range(PLAYER_ORDERS_KEY + playerId, 0, limit - 1);
        List<ShopPurchaseRecord> records = new ArrayList<>();
        if (ids != null) {
            for (String id : ids) {
                ShopOrder o = loadOrder(id);
                if (o == null) {
                    continue;
                }
                if (!ShopOrderStatus.isDelivered(o.status)
                        && !ShopOrderStatus.PAID.name().equals(o.status)) {
                    continue;
                }
                records.add(ShopPurchaseRecord.newBuilder()
                        .setOrderId(o.orderId)
                        .setProductId(o.productId)
                        .setProductType(nullToEmpty(o.productType))
                        .setPayAmount(o.payAmount)
                        .setStatus(o.status)
                        .setPaidAt(o.paidAt)
                        .build());
            }
        }
        records.sort(Comparator.comparingLong(ShopPurchaseRecord::getPaidAt).reversed());
        return historyRsp(RetCode.OK, records);
    }

    /**
     * Mock 支付快捷入口（联调）。生产关闭 {@code shop.payment.mock-enabled} 后拒绝。
     */
    public Map<String, Object> mockPay(String orderId, String channelOrderId) {
        Map<String, Object> denied = new HashMap<>();
        if (!mockPaymentEnabled) {
            denied.put("ok", false);
            denied.put("retcode", RetCode.SHOP_PAYMENT_VERIFY_FAILED);
            denied.put("message", "MOCK payment disabled");
            return denied;
        }
        ShopOrder order = loadOrder(orderId);
        String channel = order == null ? "MOCK" : order.channel;
        return confirmPay(ShopPaymentReceipt.of(orderId, channel, channelOrderId, "MOCK", 0));
    }

    /**
     * 渠道回调 / 客户端票据二次确认 → 验签 → 履约。
     */
    public Map<String, Object> confirmPay(ShopPaymentReceipt receipt) {
        Map<String, Object> result = new HashMap<>();
        if (receipt == null || receipt.orderId == null || receipt.orderId.isBlank()) {
            result.put("ok", false);
            result.put("retcode", RetCode.SHOP_ORDER_NOT_FOUND);
            return result;
        }
        ShopOrder order = loadOrder(receipt.orderId);
        if (order == null) {
            result.put("ok", false);
            result.put("retcode", RetCode.SHOP_ORDER_NOT_FOUND);
            return result;
        }
        if (ShopOrderStatus.isDelivered(order.status)) {
            result.put("ok", true);
            result.put("status", order.status);
            result.put("idempotent", true);
            return result;
        }
        if (ShopOrderStatus.REFUNDED.name().equals(order.status)
                || ShopOrderStatus.CLOSED.name().equals(order.status)) {
            result.put("ok", false);
            result.put("retcode", RetCode.SHOP_ORDER_INVALID_STATUS);
            result.put("status", order.status);
            return result;
        }
        if (!ShopOrderStatus.CREATED.name().equals(order.status)
                && !ShopOrderStatus.PAID.name().equals(order.status)) {
            result.put("ok", false);
            result.put("retcode", RetCode.SHOP_ORDER_INVALID_STATUS);
            result.put("status", order.status);
            return result;
        }

        ShopPaymentVerifyResult verified = paymentVerifierRegistry.verify(order, receipt);
        if (!verified.ok()) {
            ShopMetrics metrics = shopMetrics.getIfAvailable();
            if (metrics != null) {
                metrics.recordPayVerifyFailure();
            }
            result.put("ok", false);
            result.put("retcode", verified.retcode());
            result.put("message", verified.message());
            return result;
        }

        Optional<ShopProductConfig> cfgOpt = shopConfigService.findById(order.productId);
        if (cfgOpt.isEmpty() || !cfgOpt.get().isOnSale(System.currentTimeMillis())) {
            order.status = ShopOrderStatus.CLOSED.name();
            saveOrder(order);
            result.put("ok", false);
            result.put("retcode", RetCode.SHOP_PRODUCT_NOT_ON_SALE);
            return result;
        }
        ShopProductConfig cfg = cfgOpt.get();
        if (cfg.typeEnum() == ShopProductType.DISCOUNT_PACK
                && !cfg.isDiscountWindowActive(System.currentTimeMillis())) {
            order.status = ShopOrderStatus.CLOSED.name();
            saveOrder(order);
            result.put("ok", false);
            result.put("retcode", RetCode.SHOP_DISCOUNT_EXPIRED);
            return result;
        }
        long now = System.currentTimeMillis();
        int bought = getBoughtCount(order.playerId, cfg, now);
        if (remainCount(cfg, bought) == 0 && !ShopOrderStatus.PAID.name().equals(order.status)) {
            order.status = ShopOrderStatus.CLOSED.name();
            saveOrder(order);
            result.put("ok", false);
            result.put("retcode", RetCode.SHOP_LIMIT_EXCEEDED);
            return result;
        }

        boolean stockDeducted = false;
        ShopStockService stock = shopStockService == null ? null : shopStockService.getIfAvailable();
        if (stock != null && cfg.stockTotal > 0) {
            ensureStockInitialized(stock, cfg);
            long left = stock.tryDeduct(cfg.productId, 1);
            if (left == -1L) {
                order.status = ShopOrderStatus.CLOSED.name();
                saveOrder(order);
                result.put("ok", false);
                result.put("retcode", RetCode.SHOP_STOCK_EXCEEDED);
                return result;
            }
            if (left >= 0) {
                stockDeducted = true;
            }
        }

        String resolvedChannelOrderId = verified.channelOrderId() == null || verified.channelOrderId().isBlank()
                ? "PAY-" + order.orderId : verified.channelOrderId();
        String channelKey = CHANNEL_ORDER_KEY + order.channel + ":" + resolvedChannelOrderId;
        Boolean firstChannel = stringRedisTemplate.opsForValue().setIfAbsent(channelKey, order.orderId, ORDER_TTL);
        if (Boolean.FALSE.equals(firstChannel)) {
            String existingOrderId = stringRedisTemplate.opsForValue().get(channelKey);
            ShopOrder existing = loadOrder(existingOrderId);
            if (existing != null && ShopOrderStatus.isDelivered(existing.status)) {
                if (stockDeducted && stock != null) {
                    stock.restore(cfg.productId, 1);
                }
                result.put("ok", true);
                result.put("status", existing.status);
                result.put("idempotent", true);
                result.put("orderId", existing.orderId);
                return result;
            }
        }

        boolean firstDouble = isFirstChargeDoubleEligible(order.playerId, cfg);
        order.channelOrderId = resolvedChannelOrderId;
        order.status = ShopOrderStatus.PAID.name();
        order.paidAt = now;
        saveOrder(order);

        int grantRc = grantRewards(order, firstDouble);
        if (grantRc != 0) {
            stringRedisTemplate.delete(channelKey);
            if (stockDeducted && stock != null) {
                stock.restore(cfg.productId, 1);
            }
            result.put("ok", false);
            result.put("retcode", RetCode.SHOP_FULFILL_FAILED);
            result.put("bagRetcode", grantRc);
            return result;
        }
        // 方案 A：发货后按 SkinConfig.itemId 自动兑换皮肤拥有关系（幂等）
        autoUnlockSkins(order, firstDouble);

        incrLimitCounter(order.playerId, cfg, now);
        order.status = ShopOrderStatus.DELIVERED.name();
        order.fulfilledAt = System.currentTimeMillis();
        if (firstDouble) {
            order.rewards = doubledRewards(order.rewards);
        }
        saveOrder(order);

        notifyRecharge(order);
        applyPassProgress(order, firstDouble);
        MqOutboxService outbox = mqOutboxService.getIfAvailable();
        if (outbox != null) {
            outbox.enqueueShopOrderPaid(
                    order.playerId, order.orderId, order.productId, order.payAmount, order.productType);
        } else {
            shopOrderPaidEventPublisher.publishPaid(
                    order.playerId, order.orderId, order.productId, order.payAmount, order.productType);
        }
        pushPaidNotify(order);
        ShopMetrics metrics = shopMetrics.getIfAvailable();
        if (metrics != null) {
            metrics.recordFulfilled();
        }

        result.put("ok", true);
        result.put("status", order.status);
        result.put("orderId", order.orderId);
        result.put("productId", order.productId);
        result.put("firstChargeDouble", firstDouble);
        return result;
    }

    public Map<String, Object> fulfillByOps(String orderId) {
        return mockPay(orderId, null);
    }

    /**
     * 退款工单：标记 REFUNDED；累充不自动回退（人工运维）；默认不追回道具。
     */
    public Map<String, Object> refundOrder(String orderId, String reason, boolean reclaimItems) {
        Map<String, Object> result = new HashMap<>();
        ShopOrder order = loadOrder(orderId);
        if (order == null) {
            result.put("ok", false);
            result.put("retcode", RetCode.SHOP_ORDER_NOT_FOUND);
            return result;
        }
        if (ShopOrderStatus.REFUNDED.name().equals(order.status)) {
            result.put("ok", true);
            result.put("status", order.status);
            result.put("idempotent", true);
            return result;
        }
        if (!ShopOrderStatus.canRefund(order.status)) {
            result.put("ok", false);
            result.put("retcode", RetCode.SHOP_REFUND_REJECTED);
            result.put("message", "only PAID/DELIVERED(FULFILLED) can refund");
            result.put("status", order.status);
            return result;
        }
        Boolean first = stringRedisTemplate.opsForValue()
                .setIfAbsent(REFUND_KEY + order.orderId, "1", ORDER_TTL);
        if (Boolean.FALSE.equals(first)) {
            result.put("ok", true);
            result.put("idempotent", true);
            result.put("status", ShopOrderStatus.REFUNDED.name());
            return result;
        }
        order.status = ShopOrderStatus.REFUNDED.name();
        saveOrder(order);
        ShopMetrics metrics = shopMetrics.getIfAvailable();
        if (metrics != null) {
            metrics.recordRefund();
        }
        log.warn("Shop refund orderId={} playerId={} amount={} reason={} reclaimItems={} (recharge not rolled back)",
                order.orderId, order.playerId, order.payAmount, reason, reclaimItems);
        result.put("ok", true);
        result.put("status", order.status);
        result.put("orderId", order.orderId);
        result.put("reclaimItems", reclaimItems);
        result.put("rechargeRollback", false);
        result.put("message", "refund recorded; recharge rollback requires ops ticket");
        return result;
    }

    private int grantRewards(ShopOrder order, boolean firstChargeDouble) {
        ShopBagGrantClient client = bagGrantClient.getIfAvailable();
        if (client == null) {
            log.warn("ShopBagGrantClient unavailable, skip grant orderId={}", order.orderId);
            return 0;
        }
        List<ShopRewardConfig> grantList = firstChargeDouble
                ? doubledRewards(order.rewards) : order.rewards;
        List<Map<String, Object>> rewards = new ArrayList<>();
        if (grantList != null) {
            for (ShopRewardConfig r : grantList) {
                Map<String, Object> item = new HashMap<>();
                item.put("itemId", r.itemId);
                item.put("count", r.count);
                rewards.add(item);
            }
        }
        Map<String, Object> body = new HashMap<>();
        body.put("idempotencyKey", "shop:" + order.orderId);
        body.put("rewards", rewards);
        try {
            Integer rc = client.grant(order.playerId, body);
            return rc == null ? -1 : rc;
        } catch (Exception e) {
            log.warn("shop grant failed orderId={}", order.orderId, e);
            return -1;
        }
    }

    /**
     * 履约后自动解锁：rewards.itemId 能映射到 SkinConfig 时调用 unlock-by-item。
     * 失败仅打日志，不回滚订单（道具已入包，玩家可手动使用皮肤卡）。
     */
    private void autoUnlockSkins(ShopOrder order, boolean firstChargeDouble) {
        ShopSkinGrantClient skinClient = skinGrantClient.getIfAvailable();
        SkinConfigRepository skins = skinConfigRepository.getIfAvailable();
        if (skinClient == null || skins == null || order.rewards == null) {
            return;
        }
        List<ShopRewardConfig> grantList = firstChargeDouble
                ? doubledRewards(order.rewards) : order.rewards;
        for (ShopRewardConfig r : grantList) {
            if (r == null || r.itemId <= 0) {
                continue;
            }
            SkinConfig skin = skins.findByItemId(r.itemId);
            if (skin == null || skin.isDefault() || !skin.isEnabled()) {
                continue;
            }
            try {
                Integer rc = skinClient.unlockByItem(order.playerId, Map.of("itemId", r.itemId));
                if (rc == null || (rc != RetCode.OK && rc != RetCode.SKIN_ALREADY_OWNED)) {
                    log.warn("shop auto unlock skin failed orderId={} itemId={} skinId={} rc={}",
                            order.orderId, r.itemId, skin.skinId(), rc);
                }
            } catch (Exception e) {
                log.warn("shop auto unlock skin exception orderId={} itemId={}", order.orderId, r.itemId, e);
            }
        }
    }

    /**
     * 直充钻石档：该 SKU 终身首次购买时奖励数量 ×2（展示与发货一致）。
     */
    boolean isFirstChargeDoubleEligible(long playerId, ShopProductConfig cfg) {
        return cfg != null
                && cfg.typeEnum() == ShopProductType.DIRECT_TOPUP
                && cfg.tags != null
                && cfg.tags.contains("diamond")
                && getLifetimeBought(playerId, cfg.productId) == 0;
    }

    static List<ShopRewardConfig> doubledRewards(List<ShopRewardConfig> rewards) {
        if (rewards == null) {
            return List.of();
        }
        List<ShopRewardConfig> out = new ArrayList<>(rewards.size());
        for (ShopRewardConfig r : rewards) {
            ShopRewardConfig copy = new ShopRewardConfig();
            copy.itemId = r.itemId;
            copy.count = r.count * 2;
            copy.bindType = r.bindType;
            out.add(copy);
        }
        return out;
    }

    private void notifyRecharge(ShopOrder order) {
        ShopActivityRechargeClient client = rechargeClient.getIfAvailable();
        if (client == null) {
            return;
        }
        try {
            client.addRecharge(order.playerId, Map.of(
                    "amount", order.payAmount,
                    "orderId", order.orderId,
                    "productId", order.productId));
        } catch (Exception e) {
            log.warn("shop recharge notify failed orderId={}", order.orderId, e);
        }
    }

    /**
     * 支付履约成功后联动战令：充值加 XP；带 battle_pass / monthly_card 标签则解锁对应权益。
     */
    void applyPassProgress(ShopOrder order, boolean firstChargeDouble) {
        PassService pass = passService == null ? null : passService.getIfAvailable();
        if (pass == null || order == null || order.playerId <= 0) {
            return;
        }
        try {
            int xp = Math.max(50, (int) (Math.max(0L, order.payAmount) / 10L));
            pass.addDailyXp(order.playerId, passSeasonId, xp);

            Optional<ShopProductConfig> cfgOpt = shopConfigService.findById(order.productId);
            List<String> tags = cfgOpt.map(c -> c.tags == null ? List.<String>of() : c.tags).orElse(List.of());
            String type = order.productType == null ? "" : order.productType.toUpperCase(Locale.ROOT);

            if (tags.contains("battle_pass") || type.contains("BATTLE_PASS") || type.contains("PASS")) {
                pass.unlockPaid(order.playerId, passSeasonId);
            }
            if (tags.contains("monthly_card") || tags.contains("monthly") || type.contains("MONTHLY")) {
                pass.activateMonthlyCard(order.playerId, passSeasonId, 30);
            }
            if (firstChargeDouble || tags.contains("first_charge")) {
                pass.applyFirstChargeDouble(order.playerId, passSeasonId,
                        (int) Math.max(1L, order.payAmount / 100L));
            }
        } catch (Exception e) {
            log.warn("shop pass progress failed orderId={} playerId={}", order.orderId, order.playerId, e);
        }
    }

    private void pushPaidNotify(ShopOrder order) {
        PlayerNotificationPort push = notificationPort.getIfAvailable();
        if (push == null) {
            return;
        }
        ShopOrderPaidScNotify notify = ShopOrderPaidScNotify.newBuilder()
                .setOrderId(order.orderId)
                .setProductId(order.productId)
                .setStatus(order.status)
                .setPayAmount(order.payAmount)
                .addAllRewards(toProtoRewards(order.rewards))
                .build();
        push.send(order.playerId, MessageId.SHOP_ORDER_PAID_SC_NOTIFY, notify.toByteArray());
    }

    private ShopProductInfo toProductInfo(ShopProductConfig cfg, int remain, boolean firstDouble) {
        return ShopProductInfo.newBuilder()
                .setProductId(cfg.productId)
                .setProductType(nullToEmpty(cfg.productType))
                .setName(nullToEmpty(cfg.name))
                .setDesc(nullToEmpty(cfg.desc))
                .setIcon(nullToEmpty(cfg.icon))
                .setChannelSku(nullToEmpty(cfg.channelSku))
                .setCurrency(nullToEmpty(cfg.currency))
                .setPrice(cfg.price)
                .setOriginalPrice(cfg.originalPrice > 0 ? cfg.originalPrice : cfg.price)
                .setDiscountRate(cfg.discountRate == null ? 0 : cfg.discountRate)
                .setDiscountEnd(cfg.discountEndMs())
                .setSaleEnd(cfg.saleEndMs())
                .setTabId(nullToEmpty(cfg.tabId))
                .addAllTags(cfg.tags == null ? List.of() : cfg.tags)
                .addAllRewards(toProtoRewards(cfg.rewards))
                .setLimitType(nullToEmpty(cfg.limitType))
                .setLimitCount(Math.max(cfg.limitCount, 0))
                .setRemainCount(remain < 0 ? 999999 : remain)
                .setFirstChargeDouble(firstDouble)
                .setSort(cfg.sort)
                .setVersion(cfg.version)
                .build();
    }

    private List<ShopRewardItem> toProtoRewards(List<ShopRewardConfig> rewards) {
        List<ShopRewardItem> list = new ArrayList<>();
        if (rewards == null) {
            return list;
        }
        for (ShopRewardConfig r : rewards) {
            list.add(ShopRewardItem.newBuilder()
                    .setItemId(r.itemId)
                    .setCount(r.count)
                    .setBindType(r.bindType)
                    .build());
        }
        return list;
    }

    private int remainCount(ShopProductConfig cfg, int bought) {
        ShopLimitType limit = cfg.limitEnum();
        if (limit == null || limit == ShopLimitType.NONE) {
            return -1;
        }
        return Math.max(0, cfg.limitCount - bought);
    }

    private int getBoughtCount(long playerId, ShopProductConfig cfg, long nowMs) {
        ShopLimitType limit = cfg.limitEnum();
        if (limit == null || limit == ShopLimitType.NONE) {
            return 0;
        }
        String period = periodKey(limit, nowMs);
        String raw = stringRedisTemplate.opsForValue().get(COUNTER_KEY + playerId + ":" + cfg.productId + ":" + period);
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int getLifetimeBought(long playerId, int productId) {
        String raw = stringRedisTemplate.opsForValue().get(COUNTER_KEY + playerId + ":" + productId + ":LIFETIME");
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void incrLimitCounter(long playerId, ShopProductConfig cfg, long nowMs) {
        ShopLimitType limit = cfg.limitEnum();
        if (limit == null || limit == ShopLimitType.NONE) {
            return;
        }
        String period = periodKey(limit, nowMs);
        String key = COUNTER_KEY + playerId + ":" + cfg.productId + ":" + period;
        Long v = stringRedisTemplate.opsForValue().increment(key);
        if (v != null && v == 1L) {
            stringRedisTemplate.expire(key, Duration.ofDays(40));
        }
        if (limit != ShopLimitType.LIFETIME && limit != ShopLimitType.ACCOUNT) {
            stringRedisTemplate.opsForValue().increment(COUNTER_KEY + playerId + ":" + cfg.productId + ":LIFETIME");
            stringRedisTemplate.expire(COUNTER_KEY + playerId + ":" + cfg.productId + ":LIFETIME", Duration.ofDays(3650));
        }
    }

    private void ensureStockInitialized(ShopStockService stock, ShopProductConfig cfg) {
        if (cfg.stockTotal <= 0) {
            return;
        }
        if (stock.remaining(cfg.productId) < 0) {
            stock.initStock(cfg.productId, cfg.stockTotal);
        }
    }

    static String periodKey(ShopLimitType limit, long nowMs) {
        LocalDate day = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(nowMs), ZONE);
        return switch (limit) {
            case DAILY -> "D:" + day;
            case WEEKLY -> {
                WeekFields wf = WeekFields.of(Locale.CHINA);
                yield "W:" + day.get(wf.weekBasedYear()) + "-" + day.get(wf.weekOfWeekBasedYear());
            }
            case MONTHLY -> "M:" + day.getYear() + "-" + day.getMonthValue();
            case LIFETIME, ACCOUNT -> "LIFETIME";
            default -> "NONE";
        };
    }

    private void saveOrder(ShopOrder order) {
        try {
            stringRedisTemplate.opsForValue().set(ORDER_KEY + order.orderId,
                    objectMapper.writeValueAsString(order), ORDER_TTL);
        } catch (Exception e) {
            throw new IllegalStateException("save shop order failed", e);
        }
        persistOrder(order);
    }

    private void persistOrder(ShopOrder order) {
        ShopOrderRecordRepository repo = shopOrderRecordRepository.getIfAvailable();
        if (repo == null || order == null || order.orderId == null) {
            return;
        }
        try {
            ShopOrderRecord row = new ShopOrderRecord();
            row.setOrderId(order.orderId);
            row.setPlayerId(order.playerId);
            row.setProductId(order.productId);
            row.setProductType(nullToEmpty(order.productType));
            row.setPayAmount(order.payAmount);
            row.setCurrency(nullToEmpty(order.currency));
            row.setChannel(nullToEmpty(order.channel));
            row.setChannelSku(nullToEmpty(order.channelSku));
            row.setChannelOrderId(nullToEmpty(order.channelOrderId));
            row.setStatus(nullToEmpty(order.status));
            row.setCreatedAt(order.createdAt);
            row.setPaidAt(order.paidAt);
            row.setFulfilledAt(order.fulfilledAt);
            row.setIdempotencyKey(nullToEmpty(order.idempotencyKey));
            row.setRewardsJson(objectMapper.writeValueAsString(order.rewards == null ? List.of() : order.rewards));
            repo.save(row);
        } catch (Exception e) {
            log.warn("persist shop_order failed orderId={}", order.orderId, e);
        }
    }

    private ShopOrder loadOrder(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return null;
        }
        try {
            String json = stringRedisTemplate.opsForValue().get(ORDER_KEY + orderId);
            if (json != null && !json.isBlank()) {
                return objectMapper.readValue(json, ShopOrder.class);
            }
        } catch (Exception e) {
            log.warn("load shop order from redis failed id={}", orderId, e);
        }
        ShopOrderRecordRepository repo = shopOrderRecordRepository.getIfAvailable();
        if (repo == null) {
            return null;
        }
        return repo.findById(orderId).map(this::toShopOrder).orElse(null);
    }

    private ShopOrder toShopOrder(ShopOrderRecord row) {
        ShopOrder order = new ShopOrder();
        order.orderId = row.getOrderId();
        order.playerId = row.getPlayerId() == null ? 0L : row.getPlayerId();
        order.productId = row.getProductId() == null ? 0 : row.getProductId();
        order.productType = nullToEmpty(row.getProductType());
        order.payAmount = row.getPayAmount() == null ? 0L : row.getPayAmount();
        order.currency = nullToEmpty(row.getCurrency());
        order.channel = nullToEmpty(row.getChannel());
        order.channelSku = nullToEmpty(row.getChannelSku());
        order.channelOrderId = nullToEmpty(row.getChannelOrderId());
        order.status = nullToEmpty(row.getStatus());
        order.createdAt = row.getCreatedAt() == null ? 0L : row.getCreatedAt();
        order.paidAt = row.getPaidAt() == null ? 0L : row.getPaidAt();
        order.fulfilledAt = row.getFulfilledAt() == null ? 0L : row.getFulfilledAt();
        order.idempotencyKey = nullToEmpty(row.getIdempotencyKey());
        try {
            if (row.getRewardsJson() != null && !row.getRewardsJson().isBlank()) {
                order.rewards = objectMapper.readValue(row.getRewardsJson(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, ShopRewardConfig.class));
            }
        } catch (Exception e) {
            log.warn("parse shop order rewards failed orderId={}", order.orderId, e);
            order.rewards = new ArrayList<>();
        }
        return order;
    }

    /**
     * 日终对账：扫描 PAID 卡单 / 缺渠道单号，并与 {@link ChannelBillProvider} 账单做差异对比。
     */
    public Map<String, Object> reconcile(long fromMs, long toMs) {
        Map<String, Object> result = new HashMap<>();
        ShopOrderRecordRepository repo = shopOrderRecordRepository.getIfAvailable();
        if (repo == null) {
            result.put("ok", false);
            result.put("message", "shop_order repository unavailable");
            return result;
        }
        List<ShopOrderRecord> paidStuck = repo.findByStatusAndPaidAtBetween(
                ShopOrderStatus.PAID.name(), fromMs, toMs);
        List<ShopOrderRecord> delivered = new ArrayList<>();
        delivered.addAll(repo.findByStatusAndPaidAtBetween(ShopOrderStatus.DELIVERED.name(), fromMs, toMs));
        delivered.addAll(repo.findByStatusAndPaidAtBetween(ShopOrderStatus.FULFILLED.name(), fromMs, toMs));
        List<String> missingChannelOrder = new ArrayList<>();
        for (ShopOrderRecord r : delivered) {
            if (r.getChannelOrderId() == null || r.getChannelOrderId().isBlank()) {
                missingChannelOrder.add(r.getOrderId());
            }
        }

        List<String> channelOnly = new ArrayList<>();
        List<String> localOnly = new ArrayList<>();
        List<String> statusMismatch = new ArrayList<>();
        int billLines = 0;
        if (channelBillProviders != null) {
            java.util.Set<String> localChannelIds = new java.util.HashSet<>();
            java.util.Map<String, ShopOrderRecord> byChannel = new HashMap<>();
            for (ShopOrderRecord r : delivered) {
                if (r.getChannelOrderId() != null && !r.getChannelOrderId().isBlank()) {
                    localChannelIds.add(r.getChannelOrderId());
                    byChannel.put(r.getChannelOrderId(), r);
                }
            }
            for (ShopOrderRecord r : paidStuck) {
                if (r.getChannelOrderId() != null && !r.getChannelOrderId().isBlank()) {
                    localChannelIds.add(r.getChannelOrderId());
                    byChannel.put(r.getChannelOrderId(), r);
                }
            }
            List<ChannelBillProvider> providers = channelBillProviders.orderedStream().toList();
            List<ChannelBillProvider.ChannelBillLine> allBills = new ArrayList<>();
            for (ChannelBillProvider provider : providers) {
                allBills.addAll(provider.fetchBills(provider.channel(), fromMs, toMs));
            }
            billLines = allBills.size();
            java.util.Set<String> billChannelIds = new java.util.HashSet<>();
            for (ChannelBillProvider.ChannelBillLine bill : allBills) {
                if (bill.channelOrderId() == null || bill.channelOrderId().isBlank()) {
                    continue;
                }
                billChannelIds.add(bill.channelOrderId());
                if (!localChannelIds.contains(bill.channelOrderId())) {
                    channelOnly.add(bill.channelOrderId());
                } else {
                    ShopOrderRecord local = byChannel.get(bill.channelOrderId());
                    if (local != null && "SUCCESS".equalsIgnoreCase(bill.status())
                            && ShopOrderStatus.PAID.name().equals(local.getStatus())) {
                        statusMismatch.add(local.getOrderId());
                    }
                }
            }
            if (billLines > 0) {
                for (String cid : localChannelIds) {
                    if (!billChannelIds.contains(cid)) {
                        localOnly.add(cid);
                    }
                }
            }
        }

        List<String> retryFulfillCandidates = paidStuck.stream()
                .map(ShopOrderRecord::getOrderId)
                .toList();

        result.put("ok", true);
        result.put("fromMs", fromMs);
        result.put("toMs", toMs);
        result.put("paidStuckCount", paidStuck.size());
        result.put("paidStuckOrderIds", paidStuck.stream().map(ShopOrderRecord::getOrderId).toList());
        result.put("deliveredCount", delivered.size());
        result.put("missingChannelOrderIds", missingChannelOrder);
        result.put("channelBillLineCount", billLines);
        result.put("diffChannelOnly", channelOnly);
        result.put("diffLocalOnly", localOnly);
        result.put("diffPaidStuckVsChannelSuccess", statusMismatch);
        result.put("retryFulfillCandidates", retryFulfillCandidates);
        result.put("message", "reconcile with ChannelBillProvider; retryFulfillCandidates for auto-repair hook");
        return result;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private ProtocolMessage shelfRsp(int rc, String tabId, List<ShopProductInfo> products, int version) {
        GetShopShelfScRsp body = GetShopShelfScRsp.newBuilder()
                .setRetcode(rc)
                .setTabId(nullToEmpty(tabId))
                .addAllProducts(products)
                .setVersion(version)
                .build();
        return new ProtocolMessage(MessageId.GET_SHOP_SHELF_SC_RSP, body.toByteArray());
    }

    private ProtocolMessage createRsp(int rc, ShopOrder order) {
        CreateShopOrderScRsp.Builder b = CreateShopOrderScRsp.newBuilder().setRetcode(rc);
        if (order != null) {
            b.setOrderId(order.orderId)
                    .setProductId(order.productId)
                    .setPayAmount(order.payAmount)
                    .setCurrency(nullToEmpty(order.currency))
                    .setChannelSku(nullToEmpty(order.channelSku))
                    .setChannel(nullToEmpty(order.channel))
                    .setStatus(order.status);
        }
        return new ProtocolMessage(MessageId.CREATE_SHOP_ORDER_SC_RSP, b.build().toByteArray());
    }

    private ProtocolMessage orderRsp(int rc, ShopOrder order) {
        GetShopOrderScRsp.Builder b = GetShopOrderScRsp.newBuilder().setRetcode(rc);
        if (order != null) {
            b.setOrderId(order.orderId)
                    .setProductId(order.productId)
                    .setPayAmount(order.payAmount)
                    .setStatus(order.status)
                    .addAllRewards(toProtoRewards(order.rewards));
        }
        return new ProtocolMessage(MessageId.GET_SHOP_ORDER_SC_RSP, b.build().toByteArray());
    }

    private ProtocolMessage historyRsp(int rc, List<ShopPurchaseRecord> records) {
        GetShopPurchaseHistoryScRsp body = GetShopPurchaseHistoryScRsp.newBuilder()
                .setRetcode(rc)
                .addAllRecords(records)
                .build();
        return new ProtocolMessage(MessageId.GET_SHOP_PURCHASE_HISTORY_SC_RSP, body.toByteArray());
    }
}
