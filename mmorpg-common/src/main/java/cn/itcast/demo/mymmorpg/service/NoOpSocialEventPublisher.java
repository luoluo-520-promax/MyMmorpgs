/**
 * 社交事件空实现：rocketmq.enabled=false 时不投递，避免本地无 Broker 启动失败。
 */
package cn.itcast.demo.mymmorpg.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpSocialEventPublisher implements SocialEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NoOpSocialEventPublisher.class);

    @Override
    public void publishFriendOnline(long playerId) {
        log.debug("MQ 未启用，跳过 FRIEND_ONLINE playerId={}", playerId);
    }

    @Override
    public void publishPartyFormed(long leaderId, String partyId, int memberCount) {
        log.debug("MQ 未启用，跳过 PARTY_FORMED leader={} party={}", leaderId, partyId);
    }

    @Override
    public void publishAssistSettled(long borrowerId, long ownerId, boolean victory) {
        log.debug("MQ 未启用，跳过 ASSIST_SETTLED borrower={} owner={}", borrowerId, ownerId);
    }

    @Override
    public void publishHomeVisited(long visitorId, long ownerId) {
        log.debug("MQ 未启用，跳过 HOME_VISITED visitor={} owner={}", visitorId, ownerId);
    }

    @Override
    public void publishCoopInteraction(String roomId, String action, long actorId, long targetId) {
        log.debug("MQ 未启用，跳过 COOP action={} room={}", action, roomId);
    }

    @Override
    public void publish(String eventType, long actorId, long targetId, String payload) {
        log.debug("MQ 未启用，跳过 social type={}", eventType);
    }
}
