package cn.itcast.demo.mymmorpg.abyss;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每月 1 / 16 日凌晨 5:00 重置深渊星级。 */
@Component
public class AbyssResetScheduler {

    private static final Logger log = LoggerFactory.getLogger(AbyssResetScheduler.class);

    private final AbyssService abyssService;

    public AbyssResetScheduler(AbyssService abyssService) {
        this.abyssService = abyssService;
    }

    @Scheduled(cron = "0 0 5 1,16 * ?")
    public void biweeklyReset() {
        abyssService.resetAllStars();
        log.info("abyss stars reset triggered (cron 0 0 5 1,16 * ?)");
    }
}
