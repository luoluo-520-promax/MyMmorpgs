/**
 * 玩家会话事件 RocketMQ 发布实现：登录/选角/登出分别投递至
 * PLAYER_LOGIN 与 PLAYER_LOGOUT Topic，供在线人数统计、防沉迷、运营大屏消费。
 */
package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.support.DispatchFailureReporter;
import org.apache.rocketmq.client.producer.DefaultMQProducer; // 共享生产者，由 RocketMqProducerConfig 创建并 start
import org.apache.rocketmq.common.message.Message; // RocketMQ 消息对象（topic + tags + body 字节）
import org.slf4j.Logger; // 发送失败时 warn，不阻断主登录流程
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value; // 从 yml 读取 Topic 名，支持环境差异化
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // 仅 rocketmq.enabled=true 时注册本 Bean
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets; // 消息体统一 UTF-8 编码

@Component // 注入到 LoginService 等需要广播玩家生命周期事件的组件
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true") // MQ 关闭时不创建，避免连接 Broker 失败
class RocketMqPlayerEventPublisher implements PlayerEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RocketMqPlayerEventPublisher.class); // 本类日志

    private final DefaultMQProducer producer; // 已启动的生产者，send 时同步等待 Broker ACK（演示项目简化）
    private final DispatchFailureReporter failureReporter;

    /** 账号登录与选角进入共用 Topic，下游可按 tags 区分 account / enter */
    @Value("${player.mq.topic.login:PLAYER_LOGIN}")
    private String topicLogin;

    /** 登出单独 Topic，便于 BI 只订阅离线流而不收登录噪音 */
    @Value("${player.mq.topic.logout:PLAYER_LOGOUT}")
    private String topicLogout;

    RocketMqPlayerEventPublisher(DefaultMQProducer producer, DispatchFailureReporter failureReporter) {
        this.producer = producer; // 构造器注入，与 RocketMqSceneEventPublisher 等同享一个 producer 实例
        this.failureReporter = failureReporter;
    }

    @Override
    public void publishAccountLogin(long accountId, String accountName) {
        // tag=account：账号级登录（尚未选角），body 为管道符分隔键值文本便于轻量消费端解析
        send(topicLogin, "account", "accountLogin|accountId=" + accountId + "|name=" + accountName);
    }

    @Override
    public void publishPlayerEnter(long accountId, long playerId, String playerName) {
        // tag=enter：选角完成进入游戏世界，角色在线计数从此刻开始
        send(topicLogin, "enter", "playerEnter|accountId=" + accountId + "|playerId=" + playerId + "|name=" + playerName);
    }

    @Override
    public void publishPlayerLogout(long accountId, Long playerId, int reason) {
        // tag=logout：reason 为登出原因码（主动/踢线/超时），供在线时长与流失分析
        send(topicLogout, "logout", "logout|accountId=" + accountId + "|playerId=" + playerId + "|reason=" + reason);
    }

    /**
     * 统一发送入口：构造 Message 并同步 send；失败仅 warn，不影响玩家登录/登出主流程结果。
     */
    private void send(String topic, String tags, String body) {
        try {
            Message msg = new Message(topic, tags, body.getBytes(StandardCharsets.UTF_8)); // 指定 Topic、过滤 tag、UTF-8 载荷
            producer.send(msg); // 同步发送，Broker 不可达时进入 catch
        } catch (Exception e) {
            log.warn("RocketMQ 玩家事件发送失败 body={}", body, e);
            failureReporter.recordMqPublishFailure();
        }
    }
}
