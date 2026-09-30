package cn.itcast.demo.mymmorpg.repository;

import cn.itcast.demo.mymmorpg.entity.MqInboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MqInboxEventRepository extends JpaRepository<MqInboxEvent, Long> {
    boolean existsByConsumerGroupAndMessageKey(String consumerGroup, String messageKey);
}
