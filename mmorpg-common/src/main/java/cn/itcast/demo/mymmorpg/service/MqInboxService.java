package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.MqInboxEvent;
import cn.itcast.demo.mymmorpg.repository.MqInboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MQ Inbox：消费端幂等落库（唯一键 consumer_group + message_key）。
 */
@Service
@ConditionalOnBean(MqInboxEventRepository.class)
public class MqInboxService {

    private static final Logger log = LoggerFactory.getLogger(MqInboxService.class);

    private final MqInboxEventRepository inboxRepository;

    public MqInboxService(MqInboxEventRepository inboxRepository) {
        this.inboxRepository = inboxRepository;
    }

    /**
     * @return true 表示首次消费可继续业务；false 表示重复消息应跳过
     */
    @Transactional
    public boolean tryClaim(String consumerGroup, String messageKey, String topic, String payload) {
        if (consumerGroup == null || consumerGroup.isBlank() || messageKey == null || messageKey.isBlank()) {
            return true;
        }
        if (inboxRepository.existsByConsumerGroupAndMessageKey(consumerGroup, messageKey)) {
            return false;
        }
        MqInboxEvent row = new MqInboxEvent();
        row.setConsumerGroup(consumerGroup);
        row.setMessageKey(messageKey);
        row.setTopic(topic == null ? "" : topic);
        row.setPayload(payload);
        row.setCreatedAt(System.currentTimeMillis());
        try {
            inboxRepository.saveAndFlush(row);
            return true;
        } catch (DataIntegrityViolationException e) {
            log.debug("inbox duplicate group={} key={}", consumerGroup, messageKey);
            return false;
        }
    }
}
