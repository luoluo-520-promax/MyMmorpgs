package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.activity.ActivitySnapshotManager;
import cn.itcast.demo.mymmorpg.cache.CacheWarmUpService;
import cn.itcast.demo.mymmorpg.entity.VersionTimeline;
import cn.itcast.demo.mymmorpg.repository.VersionTimelineRepository;
import cn.itcast.demo.mymmorpg.version.VersionGateKeeper;
import cn.itcast.demo.mymmorpg.world.content.OpenWorldConfigPatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 自动化版本时间线引擎：加载未来 7 天 Timeline，每秒检查并触发预下载/预热/发布/快照。
 */
@Service
@ConditionalOnProperty(name = "spring.application.name", havingValue = "admin-service")
public class VersionTimelineScheduler {

    private static final Logger log = LoggerFactory.getLogger(VersionTimelineScheduler.class);
    private static final long LOOKAHEAD_MS = 7L * 24 * 60 * 60 * 1000;
    private static final long PREHEAT_BEFORE_MS = 10L * 60 * 1000;
    private static final long SNAPSHOT_BEFORE_MS = 5L * 60 * 1000;

    private final VersionTimelineRepository timelineRepository;
    private final OpenWorldConfigPatchService configPatchService;
    private final VersionGateKeeper versionGateKeeper;
    private final CacheWarmUpService cacheWarmUpService;
    private final ActivitySnapshotManager snapshotManager;
    private final CopyOnWriteArrayList<VersionTimeline> memoryTimeline = new CopyOnWriteArrayList<>();

    public VersionTimelineScheduler(
            VersionTimelineRepository timelineRepository,
            OpenWorldConfigPatchService configPatchService,
            VersionGateKeeper versionGateKeeper,
            CacheWarmUpService cacheWarmUpService,
            ActivitySnapshotManager snapshotManager) {
        this.timelineRepository = timelineRepository;
        this.configPatchService = configPatchService;
        this.versionGateKeeper = versionGateKeeper;
        this.cacheWarmUpService = cacheWarmUpService;
        this.snapshotManager = snapshotManager;
    }

    /** 服务启动及每小时刷新：加载未来 7 天 Timeline 到内存。 */
    @Scheduled(fixedDelay = 3600_000, initialDelay = 5_000)
    public void reloadMemoryTimeline() {
        long now = System.currentTimeMillis();
        List<VersionTimeline> upcoming = timelineRepository.findUpcoming(now, now + LOOKAHEAD_MS);
        memoryTimeline.clear();
        memoryTimeline.addAll(upcoming);
        log.info("VersionTimelineScheduler loaded {} entries for next 7 days", upcoming.size());
    }

    /** 每秒检查 Timeline 节点。 */
    @Scheduled(fixedRate = 1000)
    @Transactional
    public void tick() {
        long now = System.currentTimeMillis();
        for (VersionTimeline entry : memoryTimeline) {
            if (!Boolean.TRUE.equals(entry.getEnabled())) {
                continue;
            }
            processEntry(entry, now);
        }
    }

    private void processEntry(VersionTimeline entry, long now) {
        String versionCode = entry.getVersionCode();

        if (!Boolean.TRUE.equals(entry.getExecutedPreheat())
                && entry.getPredownloadAtMs() != null
                && now >= entry.getPredownloadAtMs()) {
            versionGateKeeper.markPreheat(versionCode);
            entry.setExecutedPreheat(true);
            timelineRepository.save(entry);
            log.info("Preheat marked for version {}", versionCode);
        }

        if (!Boolean.TRUE.equals(entry.getExecutedWarmup())
                && entry.getEffectiveAtMs() != null
                && now >= entry.getEffectiveAtMs() - PREHEAT_BEFORE_MS) {
            cacheWarmUpService.warmUpForVersion(versionCode, CacheWarmUpService.defaultHotKeys());
            entry.setExecutedWarmup(true);
            timelineRepository.save(entry);
            log.info("Cache warm-up done for version {}", versionCode);
        }

        if (!Boolean.TRUE.equals(entry.getExecutedPublish())
                && entry.getEffectiveAtMs() != null
                && now >= entry.getEffectiveAtMs() - SNAPSHOT_BEFORE_MS
                && now < entry.getEffectiveAtMs()) {
            snapshotManager.snapshotActivePlayers(versionCode);
        }

        if (!Boolean.TRUE.equals(entry.getExecutedPublish())
                && entry.getEffectiveAtMs() != null
                && now >= entry.getEffectiveAtMs()) {
            Map<String, Object> published = configPatchService.publishStaging(now);
            entry.setExecutedPublish(true);
            timelineRepository.save(entry);
            log.info("Auto publishStaging for version {} result={}", versionCode, published);
        }
    }

    public List<Map<String, Object>> listMemoryTimeline() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (VersionTimeline v : memoryTimeline) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", v.getId());
            row.put("versionCode", v.getVersionCode());
            row.put("configVersion", v.getConfigVersion());
            row.put("effectiveAtMs", v.getEffectiveAtMs());
            row.put("predownloadAtMs", v.getPredownloadAtMs());
            row.put("executedPublish", v.getExecutedPublish());
            row.put("executedPreheat", v.getExecutedPreheat());
            row.put("executedWarmup", v.getExecutedWarmup());
            out.add(row);
        }
        return out;
    }

    @Transactional
    public VersionTimeline save(VersionTimeline timeline) {
        VersionTimeline saved = timelineRepository.save(timeline);
        reloadMemoryTimeline();
        return saved;
    }

    public List<VersionTimeline> findAll() {
        return timelineRepository.findAll();
    }
}
