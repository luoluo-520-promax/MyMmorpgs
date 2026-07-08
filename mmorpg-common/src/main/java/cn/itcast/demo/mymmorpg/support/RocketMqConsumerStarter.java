/**
 * RocketMQ 广播消费者启动器：订阅 PLAYER_EVENTS 等 Topic，JSON 反序列化后
 * 按 receiverId 过滤，再经 MqMessageAdapter 异步有序分发给业务 Handler。
 */
package cn.itcast.demo.mymmorpg.support;

import com.fasterxml.jackson.databind.ObjectMapper; // 解析 MqMessageEnvelope 与各 MqMessage 子类 JSON

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer; // 推模式消费者，Broker 主动推送消息
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext; // 并发消费上下文（本类未使用）
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus; // 消费成功/稍后重试状态码
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently; // 并发消息监听接口
import org.apache.rocketmq.common.consumer.ConsumeFromWhere; // 首次订阅时从队列末尾还是开头消费
import org.apache.rocketmq.common.message.MessageExt; // 含 body、重试次数等扩展字段的消息
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel; // 集群消费 vs 广播消费

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean; // 容器关闭时 shutdown 消费者
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * MQ 消费者启动类：
 * 1) 广播模式，集群内每个 game-server 实例都能收到同一条消息；
 * 2) 各实例用 receiverId 与 message.receiver() 比对，只处理发给自己的消息；
 * 3) 通过 MqMessageAdapter 线程池异步处理，并按类型+接收者保证顺序。
 */
@Component
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true")
public class RocketMqConsumerStarter implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(RocketMqConsumerStarter.class);

    private final ObjectMapper objectMapper; // Spring 注入的全局 JSON 映射器
    private final MqMessageAdapter adapter; // 提交到工作线程池并按序执行 dispatch
    private final MqMessageTypeRegistry typeRegistry; // messageType 字符串 -> Java Class
    private final String receiverId; // 本实例标识，通常等于 spring.application.name（如 player-service）
    private final DefaultMQPushConsumer consumer; // RocketMQ 消费者实例

    /**
     * 构造并立即启动广播消费者。
     */
    public RocketMqConsumerStarter(ObjectMapper objectMapper,
            MqMessageAdapter adapter,
            MqMessageTypeRegistry typeRegistry,
            @Value("${mq.receiver-id:${spring.application.name:player-service}}") String receiverId,
            @Value("${rocketmq.name-server}") String nameServer,
            @Value("${rocketmq.consumer.group:player-consumer-group}") String group,
            @Value("${rocketmq.consumer.topic:PLAYER_EVENTS}") String topic) throws Exception {
        this.objectMapper = objectMapper;
        this.adapter = adapter;
        this.typeRegistry = typeRegistry;
        this.receiverId = receiverId;

        this.consumer = new DefaultMQPushConsumer(group); // 消费组名，广播模式下各实例仍独立消费
        this.consumer.setNamesrvAddr(nameServer); // 连接 NameServer 发现 Broker
        this.consumer.subscribe(topic, "*"); // 订阅 Topic 下所有 tag（* 表示不过滤 tag）
        this.consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET); // 新组从最新偏移开始，避免重放历史
        this.consumer.setMessageModel(MessageModel.BROADCASTING); // 广播：每个 JVM 副本各消费一份
        this.consumer.registerMessageListener(new InternalListener()); // 注册批量并发回调
        this.consumer.start(); // 启动拉取/推送线程
        log.info("RocketMQ consumer started group={} topic={} receiverId={}", group, topic, receiverId);
    }

    @Override
    public void destroy() {
        consumer.shutdown(); // 应用关闭时释放消费者连接与线程
    }

    /** 内部消息监听器：反序列化 -> 类型查找 -> 接收者过滤 -> 提交适配器 */
    private final class InternalListener implements MessageListenerConcurrently {

        @Override
        public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
            try {
                for (MessageExt msg : msgs) { // RocketMQ 可能一次推送多条，逐条处理
                    String json = new String(msg.getBody(), StandardCharsets.UTF_8); // 外层 Envelope JSON 字符串
                    MqMessageEnvelope env = objectMapper.readValue(json, MqMessageEnvelope.class); // 解析 messageType + payload

                    Class<? extends MqMessage> type = typeRegistry.find(env.getMessageType()); // 根据全类名查具体消息 Class
                    if (type == null) {
                        log.warn("Skip unknown MQ message type: {}", env.getMessageType()); // 未注册类型，跳过避免 ClassNotFound
                        continue;
                    }

                    MqMessage message = objectMapper.readValue(env.getPayload(), type); // payload 反序列化为业务消息
                    if (!receiverId.equals(message.receiver())) {
                        continue; // 广播下所有实例都收到，仅目标 receiver 继续处理
                    }

                    adapter.submit(message); // 异步提交，不阻塞 RocketMQ 消费线程
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS; // 本批全部处理完成（含被 skip 的）
            } catch (Exception e) {
                log.error("Consume MQ message failed", e);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER; // 反序列化等失败，稍后重试
            }
        }
    }
}
