/**
 * MQ 业务消息处理器契约：每种 MqMessage 类型在 Spring 容器中最多一个 Handler Bean，
 * 由 MqHandlerAutoRegistrar 自动注册到 MqMessageDispatcher。
 */
package cn.itcast.demo.mymmorpg.support;

public interface MqMessageHandler<T extends MqMessage> { // 泛型 T 为具体 MQ 消息类型

    /** 本 Handler 处理的 MqMessage 具体 Class，MqMessageDispatcher 按 message.getClass() 路由 */
    Class<T> messageType();

    /**
     * 本 Handler 所属服务实例标识，须与消息 {@link MqMessage#receiver()} 一致，
     * 广播模式下避免其他 game-server 误处理非本实例消息。
     */
    String receiver(); // 通常返回 spring.application.name

    /** 执行业务逻辑，如跨服邮件投递、公会广播、踢玩家下线等；由 MqMessageAdapter 工作线程调用 */
    void handle(T message);
}
