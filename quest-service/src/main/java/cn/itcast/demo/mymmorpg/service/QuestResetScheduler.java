package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.PlayerQuestProgress;
import cn.itcast.demo.mymmorpg.quest.QuestConfigService;
import cn.itcast.demo.mymmorpg.quest.QuestTemplate;
import cn.itcast.demo.mymmorpg.repository.PlayerQuestProgressRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;

/**
 * 日常(questType=3) / 周常(questType=4) 任务重置调度。
 * Redis SET NX EX 分布式锁，避免多实例重复重置。
 */
@Component
@EnableScheduling
public class QuestResetScheduler {

    private static final Logger log = LoggerFactory.getLogger(QuestResetScheduler.class);
    public static final int QUEST_TYPE_DAILY = 3;
    public static final int QUEST_TYPE_WEEKLY = 4;
    private static final String LOCK_KEY = "quest:reset:lock";

    private final QuestConfigService questConfigService;
    private final PlayerQuestProgressRepository progressRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final int resetHour;
    private final ZoneId zoneId;

    public QuestResetScheduler(QuestConfigService questConfigService,
                               PlayerQuestProgressRepository progressRepository,
                               StringRedisTemplate stringRedisTemplate,
                               @Value("${quest.reset.hour:5}") int resetHour,
                               @Value("${quest.reset.timezone:Asia/Shanghai}") String timezone) {
        this.questConfigService = questConfigService;
        this.progressRepository = progressRepository;
        this.stringRedisTemplate = stringRedisTemplate;
        this.resetHour = Math.max(0, Math.min(23, resetHour));
        this.zoneId = ZoneId.of(timezone == null || timezone.isBlank() ? "Asia/Shanghai" : timezone);
    }

    /** 每小时检查是否到达重置时刻；用日期键保证一天只跑一次。 */
    @Scheduled(cron = "0 0 * * * *", zone = "${quest.reset.timezone:Asia/Shanghai}")
    public void maybeReset() {
        var now = java.time.ZonedDateTime.now(zoneId);
        if (now.getHour() != resetHour) {
            return;
        }
        String dayKey = LocalDate.now(zoneId).toString();
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(LOCK_KEY + ":" + dayKey, "1", Duration.ofHours(2));
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }
        resetDailyAndWeekly();
    }

    /** 单测 / 运维强制触发。 */
    public int resetDailyAndWeekly() {
        int reset = 0;
        LocalDate today = LocalDate.now(zoneId);
        int week = today.get(WeekFields.of(Locale.CHINA).weekOfWeekBasedYear());
        for (QuestTemplate tpl : questConfigService.listAll()) {
            if (tpl.questType != QUEST_TYPE_DAILY && tpl.questType != QUEST_TYPE_WEEKLY) {
                continue;
            }
            if (tpl.questType == QUEST_TYPE_WEEKLY && today.getDayOfWeek().getValue() != 1
                    && !forceWeekly()) {
                // 周常默认仅周一重置；其它日跳过
                continue;
            }
            List<PlayerQuestProgress> rows = progressRepository.findAll().stream()
                    .filter(p -> p.getQuestId() != null && p.getQuestId() == tpl.questId)
                    .toList();
            for (PlayerQuestProgress p : rows) {
                p.setStatus(QuestService.STATUS_AVAILABLE);
                p.setProgress(0);
                progressRepository.save(p);
                reset++;
            }
        }
        log.info("quest reset done count={} day={} week={}", reset, today, week);
        return reset;
    }

    /** package-visible：单测可覆盖周常判断。 */
    boolean forceWeekly() {
        return false;
    }
}
