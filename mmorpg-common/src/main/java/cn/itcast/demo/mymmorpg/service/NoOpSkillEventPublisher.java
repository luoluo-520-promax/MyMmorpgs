/**
 * 技能事件空实现：MQ 关闭时不发送 SKILL_EVENTS，战斗/学习主逻辑不受影响。
 */
package cn.itcast.demo.mymmorpg.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "false", matchIfMissing = true)
class NoOpSkillEventPublisher implements SkillEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NoOpSkillEventPublisher.class);

    @Override
    public void publishSkillLearned(long playerId, int skillId) {
        log.debug("MQ 未启用，跳过 skillLearn 事件 playerId={} skillId={}", playerId, skillId);
    }

    @Override
    public void publishSkillCast(long playerId, int skillId, long targetEntityId, int damage, int heal) {
        log.debug("MQ 未启用，跳过 skillCast 事件 playerId={} skillId={} target={} dmg={} heal={}",
                playerId, skillId, targetEntityId, damage, heal);
    }
}
