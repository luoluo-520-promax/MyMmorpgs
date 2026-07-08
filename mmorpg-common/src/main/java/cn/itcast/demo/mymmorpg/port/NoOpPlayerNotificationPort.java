package cn.itcast.demo.mymmorpg.port;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 战斗/活动服独立部署时的玩家推送兜底：无在线连接时不执行推送。
 * 由 activity-service / battle-service 的 PortConfiguration 显式注册。
 */
public class NoOpPlayerNotificationPort implements PlayerNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(NoOpPlayerNotificationPort.class);

    @Override
    public void send(long playerId, int msgId, byte[] payload) {
        log.debug("PlayerNotificationPort 未实现，忽略单播 playerId={} msgId={}", playerId, msgId);
    }

    @Override
    public void broadcastAllOnline(int msgId, byte[] payload, Long excludePlayerId) {
        log.debug("PlayerNotificationPort 未实现，忽略广播 msgId={}", msgId);
    }
}
