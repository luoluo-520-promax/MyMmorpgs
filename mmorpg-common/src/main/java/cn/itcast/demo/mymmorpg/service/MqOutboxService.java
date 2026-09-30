package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.MqOutboxEvent;
import cn.itcast.demo.mymmorpg.repository.MqOutboxEventRepository;
import cn.itcast.demo.mymmorpg.shop.ShopOrderPaidEventPublisher;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 支付发货等关键事件的 Outbox：先落库再异步投递，避免「本地成功、MQ 丢失」。
 */
@Service
@ConditionalOnBean(MqOutboxEventRepository.class)
public class MqOutboxService {

    private static final Logger log = LoggerFactory.getLogger(MqOutboxService.class);
    private static final int MAX_RETRY = 20;

    private final MqOutboxEventRepository outboxRepository;
    private final ObjectProvider<ShopOrderPaidEventPublisher> shopOrderPaidEventPublisher;
    private final ObjectProvider<MeterRegistry> meterRegistry;
    private final ObjectProvider<ActivityRewardCommandPublisher> activityRewardCommandPublisher;
    private final ObjectProvider<ShopStockDepletedEventPublisher> stockDepletedEventPublisher;
    private final String shopTopic;
    private final String activityTopic;

    public MqOutboxService(MqOutboxEventRepository outboxRepository,
                           ObjectProvider<ShopOrderPaidEventPublisher> shopOrderPaidEventPublisher,
                           ObjectProvider<MeterRegistry> meterRegistry,
                           ObjectProvider<ActivityRewardCommandPublisher> activityRewardCommandPublisher,
                           ObjectProvider<ShopStockDepletedEventPublisher> stockDepletedEventPublisher,
                           @org.springframework.beans.factory.annotation.Value("${shop.mq.topic:SHOP_EVENTS}") String shopTopic,
                           @org.springframework.beans.factory.annotation.Value("${activity.mq.topic:ACTIVITY_EVENTS}") String activityTopic) {
        this.outboxRepository = outboxRepository;
        this.shopOrderPaidEventPublisher = shopOrderPaidEventPublisher;
        this.meterRegistry = meterRegistry;
        this.activityRewardCommandPublisher = activityRewardCommandPublisher;
        this.stockDepletedEventPublisher = stockDepletedEventPublisher;
        this.shopTopic = shopTopic;
        this.activityTopic = activityTopic;
    }

    @PostConstruct
    void registerGauges() {
        MeterRegistry registry = meterRegistry.getIfAvailable();
        if (registry == null) {
            return;
        }
        Gauge.builder("mmorpg.mq.outbox_failed", outboxRepository,
                        repo -> repo.countByStatus(MqOutboxEvent.STATUS_FAILED))
                .description("Failed MQ outbox rows pending retry")
                .register(registry);
        Gauge.builder("mmorpg.mq.outbox_new", outboxRepository,
                        repo -> repo.countByStatus(MqOutboxEvent.STATUS_NEW))
                .description("New MQ outbox rows waiting dispatch")
                .register(registry);
    }

    @Transactional
    public void enqueueShopOrderPaid(long playerId, String orderId, int productId, long payAmount, String productType) {
        String payload = "shopOrderPaid|playerId=" + playerId
                + "|orderId=" + orderId
                + "|productId=" + productId
                + "|amount=" + payAmount
                + "|productType=" + (productType == null ? "" : productType);
        long now = System.currentTimeMillis();
        MqOutboxEvent row = new MqOutboxEvent();
        row.setTopic(shopTopic);
        row.setTag("paid");
        row.setPayload(payload);
        row.setStatus(MqOutboxEvent.STATUS_NEW);
        row.setRetryCount(0);
        row.setNextRetryAt(now);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        outboxRepository.save(row);
    }

    @Transactional
    public void enqueueShopStockDepleted(int productId, long atMs) {
        String payload = "shopStockDepleted|productId=" + productId + "|atMs=" + atMs;
        enqueue(shopTopic, "stock_depleted", payload);
    }

    /**
     * 活动奖励履约命令：活动服只写 Outbox，背包服消费并写入 grant_idempotency。
     */
    @Transactional
    public void enqueueActivityRewardCommand(long playerId, long activityId, String idempotencyKey,
                                             String itemsCsv) {
        String payload = "activityRewardCmd|playerId=" + playerId
                + "|activityId=" + activityId
                + "|idempotencyKey=" + (idempotencyKey == null ? "" : idempotencyKey)
                + "|items=" + (itemsCsv == null ? "" : itemsCsv);
        enqueue(activityTopic, "reward_grant", payload);
    }

    private void enqueue(String topic, String tag, String payload) {
        long now = System.currentTimeMillis();
        MqOutboxEvent row = new MqOutboxEvent();
        row.setTopic(topic);
        row.setTag(tag);
        row.setPayload(payload);
        row.setStatus(MqOutboxEvent.STATUS_NEW);
        row.setRetryCount(0);
        row.setNextRetryAt(now);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        outboxRepository.save(row);
    }

    @Scheduled(fixedDelayString = "${game.mq.outbox.poll-ms:2000}")
    @Transactional
    public void pollAndDispatch() {
        long now = System.currentTimeMillis();
        List<MqOutboxEvent> batch = outboxRepository.findReady(
                MqOutboxEvent.STATUS_NEW, now, PageRequest.of(0, 50));
        if (batch.isEmpty()) {
            batch = outboxRepository.findReady(MqOutboxEvent.STATUS_FAILED, now, PageRequest.of(0, 20));
        }
        for (MqOutboxEvent event : batch) {
            dispatchOne(event, now);
        }
    }

    private void dispatchOne(MqOutboxEvent event, long now) {
        try {
            if ("paid".equals(event.getTag()) && shopTopic.equals(event.getTopic())) {
                publishShopPaid(event.getPayload());
            } else if ("stock_depleted".equals(event.getTag()) && shopTopic.equals(event.getTopic())) {
                publishStockDepleted(event.getPayload());
            } else if ("reward_grant".equals(event.getTag()) && activityTopic.equals(event.getTopic())) {
                publishActivityReward(event.getPayload());
            } else {
                log.warn("Unknown outbox topic/tag id={} topic={} tag={}",
                        event.getId(), event.getTopic(), event.getTag());
            }
            event.setStatus(MqOutboxEvent.STATUS_SENT);
            event.setUpdatedAt(now);
            outboxRepository.save(event);
        } catch (Exception e) {
            int retry = event.getRetryCount() == null ? 0 : event.getRetryCount();
            event.setRetryCount(retry + 1);
            event.setStatus(MqOutboxEvent.STATUS_FAILED);
            long backoff = Math.min(300_000L, (1L << Math.min(retry, 8)) * 1000L);
            event.setNextRetryAt(now + backoff);
            event.setUpdatedAt(now);
            outboxRepository.save(event);
            if (retry + 1 >= MAX_RETRY) {
                log.error("Outbox give up id={} payload={}", event.getId(), event.getPayload(), e);
            } else {
                log.warn("Outbox dispatch failed id={} retry={}", event.getId(), retry + 1, e);
            }
        }
    }

    private void publishStockDepleted(String payload) {
        ShopStockDepletedEventPublisher publisher =
                stockDepletedEventPublisher == null ? null : stockDepletedEventPublisher.getIfAvailable();
        if (publisher == null) {
            log.info("stock depleted outbox dispatched (no publisher) payload={}", payload);
            return;
        }
        int productId = (int) parseLong(payload, "productId");
        long atMs = parseLong(payload, "atMs");
        publisher.publishDepleted(productId, atMs);
    }

    private void publishActivityReward(String payload) {
        ActivityRewardCommandPublisher publisher =
                activityRewardCommandPublisher == null ? null : activityRewardCommandPublisher.getIfAvailable();
        if (publisher == null) {
            throw new IllegalStateException("ActivityRewardCommandPublisher unavailable");
        }
        long playerId = parseLong(payload, "playerId");
        long activityId = parseLong(payload, "activityId");
        String idem = parseField(payload, "idempotencyKey");
        String items = parseField(payload, "items");
        publisher.publish(playerId, activityId, idem, items);
    }

    private void publishShopPaid(String payload) {
        ShopOrderPaidEventPublisher publisher = shopOrderPaidEventPublisher.getIfAvailable();
        if (publisher == null) {
            throw new IllegalStateException("ShopOrderPaidEventPublisher unavailable");
        }
        long playerId = parseLong(payload, "playerId");
        String orderId = parseField(payload, "orderId");
        int productId = (int) parseLong(payload, "productId");
        long amount = parseLong(payload, "amount");
        String productType = parseField(payload, "productType");
        publisher.publishPaid(playerId, orderId, productId, amount, productType);
    }

    private static String parseField(String body, String key) {
        if (body == null) {
            return "";
        }
        String token = key + "=";
        for (String part : body.split("\\|")) {
            if (part.startsWith(token)) {
                return part.substring(token.length());
            }
        }
        return "";
    }

    private static long parseLong(String body, String key) {
        String v = parseField(body, key);
        if (v.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
