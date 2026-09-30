package cn.itcast.demo.mymmorpg.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时清理心跳超时的僵尸在线会话。
 */
@Component
public class OnlineHeartbeatSweeper {

    private static final Logger log = LoggerFactory.getLogger(OnlineHeartbeatSweeper.class);

    private final PlayerSessionService playerSessionService;

    public OnlineHeartbeatSweeper(PlayerSessionService playerSessionService) {
        this.playerSessionService = playerSessionService;
    }

    @Scheduled(fixedDelayString = "${game.session.online-sweep-ms:30000}")
    public void sweep() {
        int n = playerSessionService.sweepStaleSessions();
        if (n > 0) {
            log.info("swept {} stale online sessions", n);
        }
    }
}
