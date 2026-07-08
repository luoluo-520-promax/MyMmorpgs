/**
 * MQ 消息类型注册表：RocketMQ 消息体为 JSON Envelope，messageType 字段存 Java 全类名，
 * 消费端据此反序列化 payload 为具体 MqMessage 实现类。
 */
package cn.itcast.demo.mymmorpg.support;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap; // 启动期注册，运行期只读，仍用并发 Map 保安全

@Component
public class MqMessageTypeRegistry {

    /** messageType（类全名）-> MqMessage 实现 Class */
    private final Map<String, Class<? extends MqMessage>> typeMap = new ConcurrentHashMap<>();

    /**
     * 注册一种 MQ 消息类型；同一 key 重复注册不同 Class 会抛异常，防止 Handler 歧义。
     */
    public <T extends MqMessage> void register(Class<T> type) {
        String key = type.getName(); // 与 Envelope.messageType 及 Jackson 默认类型标识一致
        Class<? extends MqMessage> old = typeMap.putIfAbsent(key, type);
        if (old != null && old != type) {
            throw new IllegalStateException("Duplicate MQ message type key: " + key);
        }
    }

    /** 消费时根据 Envelope 中的 messageType 查找 Class，未知类型返回 null */
    public Class<? extends MqMessage> find(String messageType) {
        return typeMap.get(messageType);
    }
}
