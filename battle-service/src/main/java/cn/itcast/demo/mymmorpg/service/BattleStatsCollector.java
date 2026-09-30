package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.model.admin.BattleStatsSnapshot;
import cn.itcast.demo.mymmorpg.model.ai.PlayerBattleLite;
import cn.itcast.demo.mymmorpg.model.ai.SkillUsageAgg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 战斗事件聚合：进程内存（实时）+ Redis 日/周 Hash（跨实例、重启可续）。
 */
@Component
public class BattleStatsCollector {

    private static final Logger log = LoggerFactory.getLogger(BattleStatsCollector.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_RECENT = 500;
    private static final int TOP_SKILLS = 20;
    private static final String KEY_DAILY = "battle:stats:daily:";
    private static final String KEY_WEEKLY = "battle:stats:weekly:";
    private static final Duration DAILY_TTL = Duration.ofDays(8);
    private static final Duration WEEKLY_TTL = Duration.ofDays(40);

    private final long collectedSinceEpochMs = System.currentTimeMillis();
    private final LongAdder endedTotal = new LongAdder();
    private final LongAdder winCount = new LongAdder();
    private final LongAdder loseCount = new LongAdder();
    private final LongAdder drawCount = new LongAdder();
    private final LongAdder durationSumSec = new LongAdder();
    private final LongAdder skillCastTotal = new LongAdder();
    private final AtomicLong maxActiveBindings = new AtomicLong();
    private final ConcurrentHashMap<Integer, MonsterCounter> byMonster = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, LongAdder> bySkillCast = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, PlayerCounter> byPlayer = new ConcurrentHashMap<>();
    private final Object recentLock = new Object();
    private final List<RecentEnd> recent = new ArrayList<>();

    private StringRedisTemplate redis;

    public BattleStatsCollector() {
    }

    @Autowired
    public void bindRedis(ObjectProvider<StringRedisTemplate> redisProvider) {
        this.redis = redisProvider.getIfAvailable();
    }

    public void recordSkillCast(long playerId, int skillId) {
        if (skillId <= 0) {
            return;
        }
        skillCastTotal.increment();
        bySkillCast.computeIfAbsent(skillId, id -> new LongAdder()).increment();
        byPlayer.computeIfAbsent(playerId, id -> new PlayerCounter()).skillCasts.increment();
        redisIncr("skillCast", 1);
        redisIncr("skill:" + skillId, 1);
    }

    public void recordEnded(long playerId, long battleId, int result, int durationSec, int monsterTemplateId,
                            int playerLevel, int expReward) {
        endedTotal.increment();
        durationSumSec.add(Math.max(0, durationSec));
        switch (result) {
            case 1 -> winCount.increment();
            case 0 -> loseCount.increment();
            default -> drawCount.increment();
        }
        MonsterCounter counter = byMonster.computeIfAbsent(monsterTemplateId, id -> new MonsterCounter());
        counter.total.increment();
        if (result == 1) {
            counter.wins.increment();
        }
        PlayerCounter pc = byPlayer.computeIfAbsent(playerId, id -> new PlayerCounter());
        pc.ended.increment();
        switch (result) {
            case 1 -> pc.wins.increment();
            case 0 -> pc.losses.increment();
            default -> pc.draws.increment();
        }
        synchronized (recentLock) {
            recent.add(new RecentEnd(playerId, battleId, result, durationSec, monsterTemplateId, playerLevel, expReward,
                    System.currentTimeMillis()));
            while (recent.size() > MAX_RECENT) {
                recent.remove(0);
            }
        }
        redisIncr("ended", 1);
        redisIncr("durationSum", Math.max(0, durationSec));
        if (result == 1) {
            redisIncr("win", 1);
        } else if (result == 0) {
            redisIncr("lose", 1);
        } else {
            redisIncr("draw", 1);
        }
        redisIncr("monster:" + monsterTemplateId + ":t", 1);
        if (result == 1) {
            redisIncr("monster:" + monsterTemplateId + ":w", 1);
        }
    }

    public void observeActiveBindings(int active) {
        maxActiveBindings.updateAndGet(prev -> Math.max(prev, active));
    }

    public PlayerBattleLite playerStats(long playerId) {
        PlayerBattleLite lite = new PlayerBattleLite();
        lite.setPlayerId(playerId);
        PlayerCounter pc = byPlayer.get(playerId);
        if (pc == null) {
            return lite;
        }
        long total = pc.ended.sum();
        long wins = pc.wins.sum();
        lite.setEndedTotal(total);
        lite.setWinCount(wins);
        lite.setLoseCount(pc.losses.sum());
        lite.setDrawCount(pc.draws.sum());
        lite.setWinRate(total == 0 ? 0.0 : (double) wins / total);
        lite.setSkillCastTotal(pc.skillCasts.sum());
        return lite;
    }

    public BattleStatsSnapshot snapshot(long issuedBattleIdMax, int activeBattleBindings) {
        observeActiveBindings(activeBattleBindings);
        BattleStatsSnapshot snap = new BattleStatsSnapshot();
        snap.setSnapshotEpochMs(System.currentTimeMillis());
        snap.setIssuedBattleIdMax(issuedBattleIdMax);
        snap.setActiveBattleBindings(activeBattleBindings);
        snap.setMaxActiveBindingsObserved(maxActiveBindings.get());

        RedisWindow weekly = loadWindow(KEY_WEEKLY + weekKey(), WEEKLY_TTL);
        if (weekly != null && weekly.ended > 0) {
            fillFromRedis(snap, weekly);
            LocalDate weekStart = LocalDate.now(ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            snap.setCollectedSinceEpochMs(weekStart.atStartOfDay(ZONE).toInstant().toEpochMilli());
            snap.getNotes().add("数据窗口=Redis 周表 " + weekKey() + "（跨实例累加，TTL≈40 天）");
            snap.getNotes().add("进程内存仍保留实时样本：recentSampleSize=" + recentSize()
                    + ", trackedPlayers=" + byPlayer.size());
        } else {
            fillFromMemory(snap);
            snap.setCollectedSinceEpochMs(collectedSinceEpochMs);
            snap.getNotes().add("数据窗口=进程内存（Redis 周表暂无样本或未配置 Redis）");
            snap.getNotes().add("recentSampleSize=" + recentSize());
            snap.getNotes().add("trackedPlayers=" + byPlayer.size());
        }
        RedisWindow daily = loadWindow(KEY_DAILY + dayKey(), DAILY_TTL);
        if (daily != null && daily.ended > 0) {
            snap.getNotes().add("今日 Redis 日表 " + dayKey() + ": ended=" + daily.ended
                    + ", winRate=" + String.format(Locale.ROOT, "%.2f%%", daily.winRate() * 100));
        }
        return snap;
    }

    private void fillFromMemory(BattleStatsSnapshot snap) {
        long total = endedTotal.sum();
        long wins = winCount.sum();
        snap.setEndedTotal(total);
        snap.setWinCount(wins);
        snap.setLoseCount(loseCount.sum());
        snap.setDrawCount(drawCount.sum());
        snap.setWinRate(total == 0 ? 0.0 : (double) wins / total);
        snap.setAvgDurationSec(total == 0 ? 0.0 : (double) durationSumSec.sum() / total);
        long casts = skillCastTotal.sum();
        snap.setSkillCastTotal(casts);

        Map<Integer, BattleStatsSnapshot.MonsterAgg> monsters = snap.getByMonsterTemplate();
        List<Map.Entry<Integer, MonsterCounter>> sorted = new ArrayList<>(byMonster.entrySet());
        sorted.sort(Comparator.comparingLong((Map.Entry<Integer, MonsterCounter> e) -> e.getValue().total.sum()).reversed());
        for (Map.Entry<Integer, MonsterCounter> e : sorted) {
            long mTotal = e.getValue().total.sum();
            long mWins = e.getValue().wins.sum();
            BattleStatsSnapshot.MonsterAgg agg = new BattleStatsSnapshot.MonsterAgg();
            agg.setMonsterTemplateId(e.getKey());
            agg.setTotal(mTotal);
            agg.setWins(mWins);
            agg.setWinRate(mTotal == 0 ? 0.0 : (double) mWins / mTotal);
            monsters.put(e.getKey(), agg);
        }

        List<Map.Entry<Integer, LongAdder>> skillSorted = new ArrayList<>(bySkillCast.entrySet());
        skillSorted.sort(Comparator.comparingLong((Map.Entry<Integer, LongAdder> e) -> e.getValue().sum()).reversed());
        List<SkillUsageAgg> topSkills = new ArrayList<>();
        int limit = Math.min(TOP_SKILLS, skillSorted.size());
        for (int i = 0; i < limit; i++) {
            Map.Entry<Integer, LongAdder> e = skillSorted.get(i);
            long c = e.getValue().sum();
            SkillUsageAgg usage = new SkillUsageAgg();
            usage.setSkillId(e.getKey());
            usage.setCastCount(c);
            usage.setUsageRate(casts == 0 ? 0.0 : (double) c / casts);
            topSkills.add(usage);
        }
        snap.setTopSkillUsage(topSkills);
    }

    private void fillFromRedis(BattleStatsSnapshot snap, RedisWindow w) {
        snap.setEndedTotal(w.ended);
        snap.setWinCount(w.win);
        snap.setLoseCount(w.lose);
        snap.setDrawCount(w.draw);
        snap.setWinRate(w.winRate());
        snap.setAvgDurationSec(w.ended == 0 ? 0.0 : (double) w.durationSum / w.ended);
        snap.setSkillCastTotal(w.skillCast);
        snap.getByMonsterTemplate().putAll(w.monsters);
        snap.setTopSkillUsage(w.topSkills);
    }

    private void redisIncr(String field, long delta) {
        if (redis == null || delta == 0) {
            return;
        }
        try {
            String dailyKey = KEY_DAILY + dayKey();
            String weeklyKey = KEY_WEEKLY + weekKey();
            redis.opsForHash().increment(dailyKey, field, delta);
            redis.expire(dailyKey, DAILY_TTL);
            redis.opsForHash().increment(weeklyKey, field, delta);
            redis.expire(weeklyKey, WEEKLY_TTL);
        } catch (Exception e) {
            log.warn("战斗统计写入 Redis 失败 field={} delta={}", field, delta, e);
        }
    }

    private RedisWindow loadWindow(String key, Duration ttl) {
        if (redis == null) {
            return null;
        }
        try {
            Map<Object, Object> raw = redis.opsForHash().entries(key);
            if (raw == null || raw.isEmpty()) {
                return null;
            }
            redis.expire(key, ttl);
            RedisWindow w = new RedisWindow();
            w.ended = longField(raw, "ended");
            w.win = longField(raw, "win");
            w.lose = longField(raw, "lose");
            w.draw = longField(raw, "draw");
            w.durationSum = longField(raw, "durationSum");
            w.skillCast = longField(raw, "skillCast");
            for (Map.Entry<Object, Object> e : raw.entrySet()) {
                String f = String.valueOf(e.getKey());
                if (f.startsWith("monster:") && f.endsWith(":t")) {
                    int mid = Integer.parseInt(f.substring("monster:".length(), f.length() - 2));
                    BattleStatsSnapshot.MonsterAgg agg = w.monsters.computeIfAbsent(mid, id -> {
                        BattleStatsSnapshot.MonsterAgg a = new BattleStatsSnapshot.MonsterAgg();
                        a.setMonsterTemplateId(id);
                        return a;
                    });
                    agg.setTotal(longValue(e.getValue()));
                    agg.setWins(longField(raw, "monster:" + mid + ":w"));
                    agg.setWinRate(agg.getTotal() == 0 ? 0.0 : (double) agg.getWins() / agg.getTotal());
                } else if (f.startsWith("skill:")) {
                    int sid = Integer.parseInt(f.substring("skill:".length()));
                    SkillUsageAgg usage = new SkillUsageAgg();
                    usage.setSkillId(sid);
                    usage.setCastCount(longValue(e.getValue()));
                    w.skillList.add(usage);
                }
            }
            w.skillList.sort(Comparator.comparingLong(SkillUsageAgg::getCastCount).reversed());
            int limit = Math.min(TOP_SKILLS, w.skillList.size());
            for (int i = 0; i < limit; i++) {
                SkillUsageAgg u = w.skillList.get(i);
                u.setUsageRate(w.skillCast == 0 ? 0.0 : (double) u.getCastCount() / w.skillCast);
                w.topSkills.add(u);
            }
            return w;
        } catch (Exception e) {
            log.warn("战斗统计读取 Redis 失败 key={}", key, e);
            return null;
        }
    }

    private static long longField(Map<Object, Object> raw, String field) {
        return longValue(raw.get(field));
    }

    private static long longValue(Object v) {
        if (v == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static String dayKey() {
        return LocalDate.now(ZONE).format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    private static String weekKey() {
        LocalDate today = LocalDate.now(ZONE);
        WeekFields wf = WeekFields.ISO;
        int week = today.get(wf.weekOfWeekBasedYear());
        int year = today.get(wf.weekBasedYear());
        return year + "-W" + String.format(Locale.ROOT, "%02d", week);
    }

    private int recentSize() {
        synchronized (recentLock) {
            return recent.size();
        }
    }

    private static final class MonsterCounter {
        private final LongAdder total = new LongAdder();
        private final LongAdder wins = new LongAdder();
    }

    private static final class PlayerCounter {
        private final LongAdder ended = new LongAdder();
        private final LongAdder wins = new LongAdder();
        private final LongAdder losses = new LongAdder();
        private final LongAdder draws = new LongAdder();
        private final LongAdder skillCasts = new LongAdder();
    }

    private record RecentEnd(long playerId, long battleId, int result, int durationSec, int monsterTemplateId,
                             int playerLevel, int expReward, long epochMs) {
    }

    private static final class RedisWindow {
        long ended;
        long win;
        long lose;
        long draw;
        long durationSum;
        long skillCast;
        final Map<Integer, BattleStatsSnapshot.MonsterAgg> monsters = new java.util.LinkedHashMap<>();
        final List<SkillUsageAgg> skillList = new ArrayList<>();
        final List<SkillUsageAgg> topSkills = new ArrayList<>();

        double winRate() {
            return ended == 0 ? 0.0 : (double) win / ended;
        }
    }
}
