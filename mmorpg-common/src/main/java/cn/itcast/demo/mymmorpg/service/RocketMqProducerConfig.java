/**
 * RocketMQ 生产者 Spring 配置：仅在 rocketmq.enabled=true 时注册 DefaultMQProducer Bean，
 * 供各领域事件发布器（场景/技能/道具/玩家等）向 MQ 投递异步消息。
 */
package cn.itcast.demo.mymmorpg.service;

import org.apache.rocketmq.client.exception.MQClientException; // 生产者 start 失败时抛出
import org.apache.rocketmq.client.producer.DefaultMQProducer; // RocketMQ 同步/异步消息生产者
import org.springframework.beans.factory.annotation.Value; // 从 application.yml 注入 NameServer、生产者组
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // MQ 关闭时不创建 Bean，避免连接失败
import org.springframework.context.annotation.Bean; // 声明 Spring 管理的生产者 Bean
import org.springframework.context.annotation.Configuration; // 标记为 Spring 配置类

@Configuration // 本类在 Spring 容器启动时被扫描并加载
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true") // 与 NoOp*EventPublisher 条件互斥
class RocketMqProducerConfig {

    /**
     * 创建并启动 RocketMQ 生产者 Bean；容器销毁时自动调用 shutdown 释放网络连接。
     *
     * @param nameServer NameServer 地址，Broker 路由注册中心（如 127.0.0.1:9876）
     * @param group      生产者组名，用于事务消息与运维追踪，默认 player-producer-group
     * @return 已 start() 的生产者实例，RocketMq*EventPublisher 直接注入使用
     */
    @Bean(destroyMethod = "shutdown") // 应用关闭时优雅 shutdown，防止 Netty 连接泄漏
    DefaultMQProducer mqProducer(
            @Value("${rocketmq.name-server}") String nameServer,
            @Value("${rocketmq.producer.group:player-producer-group}") String group) throws MQClientException {
        DefaultMQProducer producer = new DefaultMQProducer(group); // 指定生产者组，同组实例负载均衡发送
        producer.setNamesrvAddr(nameServer); // 绑定 NameServer，获取 Topic 对应 Broker 地址
        producer.start(); // 启动网络线程，之后 RocketMq*EventPublisher 方可 send
        return producer; // 注入到所有 RocketMq*EventPublisher 组件
    }
}
