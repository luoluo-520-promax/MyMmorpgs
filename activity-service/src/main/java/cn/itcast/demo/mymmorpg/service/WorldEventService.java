package cn.itcast.demo.mymmorpg.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 大世界玩法：世界 BOSS / 动态事件。伤害写入 Redis ZSET，含截断与反挂校验；
 * settle 产出 grantPlans 并可经 Outbox 履约。
 */
@Service
public class WorldEventService {

    public enum EventType {
        WORLD_BOSS, DYNAMIC_EVENT, REGION_PUZZLE
    }

    public enum EventState {
        SCHEDULED, ACTIVE, SETTLING, CLOSED
    }

    public record WorldEvent(
            String eventId,
            EventType type,
            int sceneId,
            float posX,
            float posZ,
            EventState state,
            long startAtMs,
            long endAtMs,
            long bossHp,
            long bossHpMax) {
    }

    public record Contribution(
            long playerId,
            long damage,
            long assistScore,
            long updatedAtMs,
            boolean flagged) {

        /** 伤害 + 治疗/护盾折算后的有效贡献 */
        public long effectiveScore() {
            return Math.max(0L, damage) + Math.max(0L, assistScore);
        }
    }

    /** 治疗折算系数、护盾折算系数 */
    public static final double HEAL_WEIGHT = 0.35;
    public static final double SHIELD_WEIGHT = 0.50;

    private static final String ZSET_KEY = "world:boss:dmg:";
    private static final String FLAG_KEY = "world:boss:flag:";
    private static final String ASSIST_KEY = "world:boss:assist:";

    private final ConcurrentHashMap<String, WorldEvent> events = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, AtomicLong>> damageBoard =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, AtomicLong>> assistBoard =
            new ConcurrentHashMap<>();
    private final ObjectProvider<StringRedisTemplate> redisTemplate;
    private final ObjectProvider<MqOutboxService> mqOutboxService;
    private final long maxHitDamage;
    private java.util.function.Consumer<Map<String, Object>> mvpBroadcastListener;

    public WorldEventService() {
        this(null, null, 500_000L);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public WorldEventService(ObjectProvider<StringRedisTemplate> redisTemplate,
                             ObjectProvider<MqOutboxService> mqOutboxService,
                             @Value("${activity.world-boss.max-hit-damage:500000}") long maxHitDamage) {
        this.redisTemplate = redisTemplate;
        this.mqOutboxService = mqOutboxService;
        this.maxHitDamage = Math.max(1L, maxHitDamage);
    }

    public WorldEvent schedule(String eventId, EventType type, int sceneId,
                               float posX, float posZ, long startAtMs, long endAtMs, long bossHpMax) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId required");
        }
        long hp = Math.max(1L, bossHpMax);
        WorldEvent ev = new WorldEvent(eventId.trim(), type == null ? EventType.WORLD_BOSS : type,
                sceneId, posX, posZ, EventState.SCHEDULED, startAtMs, endAtMs, hp, hp);
        events.put(ev.eventId(), ev);
        damageBoard.put(ev.eventId(), new ConcurrentHashMap<>());
        assistBoard.put(ev.eventId(), new ConcurrentHashMap<>());
        return ev;
    }

    public void setMvpBroadcastListener(java.util.function.Consumer<Map<String, Object>> listener) {
        this.mvpBroadcastListener = listener;
    }

    /**
     * 上报治疗/护盾辅助贡献（折算进 DamageContribution）。
     */
    public synchronized long reportAssist(String eventId, long playerId, long healAmount, long shieldAmount) {
        require(eventId);
        if (playerId <= 0) {
            throw new IllegalArgumentException("invalid player");
        }
        long score = Math.round(Math.max(0, healAmount) * HEAL_WEIGHT
                + Math.max(0, shieldAmount) * SHIELD_WEIGHT);
        if (score <= 0) {
            return assistBoard.getOrDefault(eventId, new ConcurrentHashMap<>())
                    .getOrDefault(playerId, new AtomicLong()).get();
        }
        return assistBoard.computeIfAbsent(eventId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(playerId, id -> new AtomicLong())
                .addAndGet(score);
    }

    public WorldEvent activate(String eventId) {
        WorldEvent ev = require(eventId);
        WorldEvent active = new WorldEvent(ev.eventId(), ev.type(), ev.sceneId(), ev.posX(), ev.posZ(),
                EventState.ACTIVE, ev.startAtMs(), ev.endAtMs(), ev.bossHp(), ev.bossHpMax());
        events.put(eventId, active);
        return active;
    }

    public synchronized WorldEvent reportDamage(String eventId, long playerId, long damage) {
        WorldEvent ev = require(eventId);
        if (ev.state() != EventState.ACTIVE) {
            throw new IllegalStateException("event_not_active");
        }
        if (playerId <= 0 || damage <= 0) {
            throw new IllegalArgumentException("invalid damage");
        }
        long accepted = damage;
        boolean flagged = false;
        if (damage > maxHitDamage) {
            // 单次伤害超过理论最大值：标红并剔除本次排名贡献（不累计）
            flagged = true;
            accepted = 0L;
            markFlagged(eventId, playerId, damage);
        }
        if (accepted > 0) {
            long total = damageBoard.computeIfAbsent(eventId, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(playerId, id -> new AtomicLong())
                    .addAndGet(accepted);
            writeZset(eventId, playerId, total);
        }
        // 作弊伤害不扣 BOSS 血，避免刷伤秒杀
        long newHp = flagged ? ev.bossHp() : Math.max(0L, ev.bossHp() - accepted);
        EventState state = newHp <= 0 ? EventState.SETTLING : EventState.ACTIVE;
        WorldEvent updated = new WorldEvent(ev.eventId(), ev.type(), ev.sceneId(), ev.posX(), ev.posZ(),
                state, ev.startAtMs(), ev.endAtMs(), newHp, ev.bossHpMax());
        events.put(eventId, updated);
        return updated;
    }

    /**
     * 结算：按有效贡献（伤害+辅助折算）百分位分档发放 RegionReward，并广播 MVP。
     * 档位：前 10%→S / 前 30%→A / 前 50%→B / 其余→C。
     */
    public Map<String, Object> settle(String eventId, int topN) {
        WorldEvent ev = require(eventId);
        WorldEvent settling = new WorldEvent(ev.eventId(), ev.type(), ev.sceneId(), ev.posX(), ev.posZ(),
                EventState.SETTLING, ev.startAtMs(), ev.endAtMs(), ev.bossHp(), ev.bossHpMax());
        events.put(eventId, settling);
        List<Contribution> board = contributions(eventId);
        int n = Math.max(1, topN);
        List<Map<String, Object>> top = new ArrayList<>();
        List<Map<String, Object>> grantPlans = new ArrayList<>();
        MqOutboxService outbox = mqOutboxService == null ? null : mqOutboxService.getIfAvailable();
        int eligible = (int) board.stream().filter(c -> !c.flagged()).count();
        int rankAmongEligible = 0;
        for (int i = 0; i < board.size(); i++) {
            Contribution c = board.get(i);
            if (c.flagged()) {
                continue;
            }
            String tier = rewardTierByPercentile(rankAmongEligible, eligible);
            rankAmongEligible++;
            int[] reward = rewardForTier(tier);
            String idem = "world-boss:" + eventId + ":" + c.playerId();
            Map<String, Object> plan = new LinkedHashMap<>();
            plan.put("playerId", c.playerId());
            plan.put("rank", rankAmongEligible);
            plan.put("rewardTier", tier);
            plan.put("damage", c.damage());
            plan.put("assistScore", c.assistScore());
            plan.put("effectiveScore", c.effectiveScore());
            plan.put("itemId", reward[0]);
            plan.put("count", reward[1]);
            plan.put("idempotencyKey", idem);
            grantPlans.add(plan);
            if (outbox != null) {
                outbox.enqueueActivityRewardCommand(
                        c.playerId(), 0L, idem, reward[0] + ":" + reward[1]);
            }
            if (top.size() < n) {
                top.add(Map.of(
                        "rank", rankAmongEligible,
                        "playerId", c.playerId(),
                        "damage", c.damage(),
                        "assistScore", c.assistScore(),
                        "effectiveScore", c.effectiveScore(),
                        "rewardTier", tier));
            }
        }
        Map<String, Object> mvp = Map.of();
        if (!board.isEmpty() && !board.get(0).flagged()) {
            Contribution best = board.get(0);
            mvp = new LinkedHashMap<>();
            mvp.put("playerId", best.playerId());
            mvp.put("damage", best.damage());
            mvp.put("assistScore", best.assistScore());
            mvp.put("effectiveScore", best.effectiveScore());
            mvp.put("message", "MVP " + best.playerId() + " 最高伤害 " + best.damage());
            if (mvpBroadcastListener != null) {
                mvpBroadcastListener.accept(Map.of(
                        "type", "BOSS_MVP",
                        "eventId", eventId,
                        "mvp", mvp));
            }
        }
        WorldEvent closed = new WorldEvent(ev.eventId(), ev.type(), ev.sceneId(), ev.posX(), ev.posZ(),
                EventState.CLOSED, ev.startAtMs(), System.currentTimeMillis(), 0, ev.bossHpMax());
        events.put(eventId, closed);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("eventId", eventId);
        result.put("top", top);
        result.put("mvp", mvp);
        result.put("grantPlans", grantPlans);
        result.put("participantCount", board.size());
        result.put("flaggedCount", flaggedPlayers(eventId).size());
        result.put("message", outbox != null
                ? "grantPlans enqueued to activity reward outbox"
                : "grantPlans ready for bag/mail outbox");
        return result;
    }

    /**
     * 按贡献百分位分档：前 10% / 30% / 50%。
     */
    static String rewardTierByPercentile(int zeroBasedRank, int totalEligible) {
        if (totalEligible <= 0) {
            return "C";
        }
        double pct = (zeroBasedRank + 1.0) / totalEligible;
        if (pct <= 0.10 || zeroBasedRank == 0) {
            return "S";
        }
        if (pct <= 0.30) {
            return "A";
        }
        if (pct <= 0.50) {
            return "B";
        }
        return "C";
    }

    /** @deprecated 保留兼容旧测试名；内部改走百分位。 */
    static String rewardTier(int zeroBasedRank) {
        // 旧语义：0→S, &lt;3→A, &lt;10→B；单测若仍调此方法保持原行为
        if (zeroBasedRank == 0) {
            return "S";
        }
        if (zeroBasedRank < 3) {
            return "A";
        }
        if (zeroBasedRank < 10) {
            return "B";
        }
        return "C";
    }

    static int[] rewardForTier(String tier) {
        return switch (tier == null ? "C" : tier) {
            case "S" -> new int[]{50001, 5};
            case "A" -> new int[]{50002, 3};
            case "B" -> new int[]{50003, 1};
            default -> new int[]{50004, 1};
        };
    }

    public List<Contribution> contributions(String eventId) {
        Set<String> flagged = flaggedPlayers(eventId);
        ConcurrentHashMap<Long, AtomicLong> board = damageBoard.get(eventId);
        ConcurrentHashMap<Long, AtomicLong> assists = assistBoard.get(eventId);
        long now = System.currentTimeMillis();
        List<Contribution> list = new ArrayList<>();
        Map<Long, Long> dmgMap = new LinkedHashMap<>();
        if (board != null) {
            for (Map.Entry<Long, AtomicLong> e : board.entrySet()) {
                dmgMap.put(e.getKey(), e.getValue().get());
            }
        }
        // 合并 Redis ZSET（若有）
        StringRedisTemplate redis = redisTemplate == null ? null : redisTemplate.getIfAvailable();
        if (redis != null) {
            Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<String>> tuples =
                    redis.opsForZSet().reverseRangeWithScores(ZSET_KEY + eventId, 0, -1);
            if (tuples != null && !tuples.isEmpty()) {
                for (var t : tuples) {
                    if (t.getValue() == null) {
                        continue;
                    }
                    long pid = Long.parseLong(t.getValue());
                    long dmg = t.getScore() == null ? 0L : t.getScore().longValue();
                    dmgMap.put(pid, dmg);
                }
            }
        }
        for (Map.Entry<Long, Long> e : dmgMap.entrySet()) {
            long assist = assists == null ? 0L
                    : assists.getOrDefault(e.getKey(), new AtomicLong()).get();
            boolean f = flagged.contains(String.valueOf(e.getKey()));
            list.add(new Contribution(e.getKey(), e.getValue(), assist, now, f));
        }
        if (assists != null) {
            for (Map.Entry<Long, AtomicLong> e : assists.entrySet()) {
                if (dmgMap.containsKey(e.getKey())) {
                    continue;
                }
                boolean f = flagged.contains(String.valueOf(e.getKey()));
                list.add(new Contribution(e.getKey(), 0L, e.getValue().get(), now, f));
            }
        }
        list.sort(Comparator.comparingLong(Contribution::effectiveScore).reversed());
        return list;
    }

    public WorldEvent get(String eventId) {
        return events.get(eventId);
    }

    public List<WorldEvent> listActive() {
        List<WorldEvent> out = new ArrayList<>();
        for (WorldEvent e : events.values()) {
            if (e.state() == EventState.ACTIVE || e.state() == EventState.SCHEDULED) {
                out.add(e);
            }
        }
        return out;
    }

    public Map<String, Object> toView(WorldEvent e) {
        if (e == null) {
            return Map.of("ok", false);
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("ok", true);
        view.put("eventId", e.eventId());
        view.put("type", e.type().name());
        view.put("sceneId", e.sceneId());
        view.put("posX", e.posX());
        view.put("posZ", e.posZ());
        view.put("state", e.state().name());
        view.put("bossHp", e.bossHp());
        view.put("bossHpMax", e.bossHpMax());
        view.put("startAtMs", e.startAtMs());
        view.put("endAtMs", e.endAtMs());
        view.put("maxHitDamage", maxHitDamage);
        return view;
    }

    private void writeZset(String eventId, long playerId, long totalDamage) {
        StringRedisTemplate redis = redisTemplate == null ? null : redisTemplate.getIfAvailable();
        if (redis == null) {
            return;
        }
        String key = ZSET_KEY + eventId;
        redis.opsForZSet().add(key, String.valueOf(playerId), totalDamage);
        redis.expire(key, 14, TimeUnit.DAYS);
    }

    private void markFlagged(String eventId, long playerId, long rawDamage) {
        StringRedisTemplate redis = redisTemplate == null ? null : redisTemplate.getIfAvailable();
        if (redis != null) {
            redis.opsForHash().put(FLAG_KEY + eventId, String.valueOf(playerId), String.valueOf(rawDamage));
            redis.expire(FLAG_KEY + eventId, 14, TimeUnit.DAYS);
        }
    }

    private Set<String> flaggedPlayers(String eventId) {
        StringRedisTemplate redis = redisTemplate == null ? null : redisTemplate.getIfAvailable();
        if (redis == null) {
            return Set.of();
        }
        Map<Object, Object> all = redis.opsForHash().entries(FLAG_KEY + eventId);
        if (all == null || all.isEmpty()) {
            return Set.of();
        }
        return all.keySet().stream().map(String::valueOf).collect(java.util.stream.Collectors.toSet());
    }

    private WorldEvent require(String eventId) {
        WorldEvent ev = events.get(eventId);
        if (ev == null) {
            throw new IllegalStateException("event_not_found");
        }
        return ev;
    }
}
