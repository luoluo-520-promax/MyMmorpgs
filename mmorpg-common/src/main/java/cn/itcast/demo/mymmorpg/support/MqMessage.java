/**
 * MQ 业务消息基接口：所有跨服/跨实例异步消息须实现 receiver()，
 * 标识目标 game-server 实例（如 player-service），配合广播消费避免重复执行业务。
 */
package cn.itcast.demo.mymmorpg.support;

public interface MqMessage { // 跨服 MQ 消息的统一契约

    /**
     * 本消息的目标处理者标识，通常与 mq.receiver-id / spring.application.name 一致。
     * RocketMQ 广播模式下每个 JVM 都会收到消息，仅 receiver 匹配的实例应 handle。
     */
    String receiver(); // 返回如 "player-service"，RocketMqConsumerStarter 与 MqMessageDispatcher 据此过滤
}
