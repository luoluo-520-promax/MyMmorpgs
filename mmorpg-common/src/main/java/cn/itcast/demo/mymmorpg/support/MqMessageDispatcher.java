/**
 * MQ 消息同步分发器：按消息 Class 查找唯一 Handler 并调用 handle；
 * 二次校验 receiver 与 Handler.receiver() 一致，作为广播模式下的最后一道过滤。
 */
package cn.itcast.demo.mymmorpg.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MqMessageDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MqMessageDispatcher.class);

    /** 消息 Class -> 唯一 Handler，putIfAbsent 保证一种类型只有一个处理器 */
    private final Map<Class<? extends MqMessage>, MqMessageHandler<? extends MqMessage>> handlers = new ConcurrentHashMap<>();

    /** 注册 Handler；重复注册同一 messageType 抛 IllegalStateException */
    public void registerHandler(MqMessageHandler<? extends MqMessage> handler) {
        Class<? extends MqMessage> type = handler.messageType();
        MqMessageHandler<? extends MqMessage> exists = handlers.putIfAbsent(type, handler);
        if (exists != null) {
            throw new IllegalStateException("重复注册 MQ 处理器 messageType=" + type.getName());
        }
        log.info("注册 MQ 处理器 messageType={}, receiver={}", type.getSimpleName(), handler.receiver());
    }

    /**
     * 同步派发：由 MqMessageAdapter 的工作线程调用。
     * 无 Handler 或 receiver 不匹配时仅日志，不抛异常以免中断同队列后续消息。
     */
    @SuppressWarnings("unchecked")
    public <T extends MqMessage> void dispatch(T message) {
        MqMessageHandler<T> handler = (MqMessageHandler<T>) handlers.get(message.getClass());
        if (handler == null) {
            log.warn("未找到 MQ 处理器 messageType={}", message.getClass().getName());
            return;
        }
        if (!handler.receiver().equals(message.receiver())) {
            log.debug("跳过非目标接收者消息 messageType={}, target={}, current={}",
                    message.getClass().getSimpleName(), message.receiver(), handler.receiver());
            return;
        }
        handler.handle(message); // 执行具体 MMORPG 业务（如补发奖励、同步公会数据）
    }
}
