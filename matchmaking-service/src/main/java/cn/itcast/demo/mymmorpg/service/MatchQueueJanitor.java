package cn.itcast.demo.mymmorpg.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 匹配队列清道夫：剔除心跳超时幽灵玩家，并处理等待超时弹出。
 */
@Component
@ConditionalOnProperty(name = "spring.application.name", havingValue = "matchmaking-service")
public class MatchQueueJanitor {

    private static final Logger log = LoggerFactory.getLogger(MatchQueueJanitor.class);

    private final MatchmakingService matchmakingService;

    public MatchQueueJanitor(MatchmakingService matchmakingService) {
        this.matchmakingService = matchmakingService;
    }

    @Scheduled(fixedDelayString = "${game.match.janitor-interval-ms:5000}")
    public void sweep() {
        try {
            int ghosts = matchmakingService.purgeGhostPlayers();
            int timeouts = matchmakingService.processQueueTimeouts();
            if (ghosts > 0 || timeouts > 0) {
                log.info("match janitor: purgedGhosts={}, timedOut={}", ghosts, timeouts);
            }
        } catch (Exception e) {
            log.warn("match janitor failed: {}", e.getMessage());
        }
    }
}
