/**
 * 技能事件 RocketMQ 发布实现：学习技能与战斗释招后投递 SKILL_EVENTS Topic，
 * 供任务进度（「学会火球术」）、战斗回放、伤害排行榜等异步系统消费。
 */
package cn.itcast.demo.mymmorpg.service;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true")
class RocketMqSkillEventPublisher implements SkillEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RocketMqSkillEventPublisher.class);

    private final DefaultMQProducer producer; // 共享生产者 Bean

    @Value("${skill.mq.topic:SKILL_EVENTS}") // 技能领域专用 Topic，与场景/道具事件隔离
    private String topic;

    RocketMqSkillEventPublisher(DefaultMQProducer producer) {
        this.producer = producer;
    }

    @Override
    public void publishSkillLearned(long playerId, int skillId) {
        // tag=learn：玩家成功学习或升级技能，任务系统可订阅统计「学会 N 个技能」
        send("learn", "skillLearn|playerId=" + playerId + "|skillId=" + skillId);
    }

    @Override
    public void publishSkillCast(long playerId, int skillId, long targetEntityId, int damage, int heal) {
        // tag=cast：记录目标、伤害、治疗，供 DPS 统计与反作弊（异常高额伤害）分析
        send("cast", "skillCast|playerId=" + playerId + "|skillId=" + skillId + "|target=" + targetEntityId
                + "|dmg=" + damage + "|heal=" + heal);
    }

    /** 构造 Message 并同步 send；失败仅 warn，技能释放主流程已成功时不回滚 */
    private void send(String tags, String body) {
        try {
            Message msg = new Message(topic, tags, body.getBytes(StandardCharsets.UTF_8));
            producer.send(msg);
        } catch (Exception e) {
            log.warn("RocketMQ 技能事件发送失败 body={}", body, e);
        }
    }
}
