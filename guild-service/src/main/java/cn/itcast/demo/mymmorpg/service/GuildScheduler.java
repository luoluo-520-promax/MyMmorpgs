package cn.itcast.demo.mymmorpg.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class GuildScheduler {

    private static final Logger log = LoggerFactory.getLogger(GuildScheduler.class);

    private final GuildService guildService;
    private final GuildExpeditionService expeditionService;

    public GuildScheduler(GuildService guildService, GuildExpeditionService expeditionService) {
        this.guildService = guildService;
        this.expeditionService = expeditionService;
    }

    /** 每日凌晨 4:00：会长 7 天未登录自动转让。 */
    @Scheduled(cron = "0 0 4 * * ?")
    public void scanLeaderInactive() {
        List<Map<String, Object>> transferred = guildService.scanLeaderTransfer();
        if (!transferred.isEmpty()) {
            log.info("guild leader auto-transfer count={}", transferred.size());
        }
    }

    /** 每周一 5:00：远征结算并重置。 */
    @Scheduled(cron = "0 0 5 ? * MON")
    public void weeklyExpeditionReset() {
        int n = expeditionService.weeklyResetAll();
        log.info("guild expedition weekly reset cleared={}", n);
    }
}
