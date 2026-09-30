package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.Player;
import cn.itcast.demo.mymmorpg.matchmaking.MatchCompatibilityScorer;
import cn.itcast.demo.mymmorpg.port.PlayerCachePort;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.protocol.protobuf.CancelMatchCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.CancelMatchScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.EnqueueMatchCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.EnqueueMatchScRsp;
import cn.itcast.demo.mymmorpg.repository.PlayerRepository;
import cn.itcast.demo.mymmorpg.support.MatchMetrics;
import cn.itcast.demo.mymmorpg.world.scene.SceneBackgroundPrecreator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 匹配服：队列与状态持久化到 Redis。
 * <p>
 * 支持：心跳幽灵清理、等待超时弹出、两阶段实例预留、战力分段确定性路由、可观测指标。
 */
@Service
public class MatchmakingService {

    public static final int MATCH_TYPE_DUNGEON = 1;
    public static final int MATCH_TYPE_PVP = 2;
    /** 跨服组队副本（4 人） */
    public static final int MATCH_TYPE_CROSS_DUNGEON = 3;

    public static final int STATUS_QUEUED = 1;
    public static final int STATUS_MATCHED = 2;
    public static final int STATUS_CANCELLED = 3;
    public static final int STATUS_TIMEOUT = 4;

    private static final int LEVEL_BAND = 5;
    /** 配对时允许的战力差 */
    private static final int POWER_BAND = 200;
    /** 确定性路由的战力区间宽度（大于配对带宽，减少边界拆分） */
    private static final int POWER_ROUTE_BAND = 1000;
    private static final int MIN_LEVEL_DUNGEON = 1;
    private static final int MIN_LEVEL_PVP = 5;
    private static final int MIN_LEVEL_CROSS = 10;
    private static final int PARTY_SIZE_DEFAULT = 2;
    private static final int PARTY_SIZE_CROSS = 4;
    private static final int ESTIMATED_WAIT_BASE_SEC = 8;
    private static final int ESTIMATED_WAIT_PER_MISSING_SEC = 12;
    private static final int ESTIMATED_WAIT_MAX_SEC = 180;
    private static final Duration TTL = Duration.ofMinutes(30);

    private static final String KEY_QUEUE = "match:queue:";
    private static final String KEY_PLAYER = "match:player:";
    private static final String KEY_STATUS = "match:status:";
    private static final String KEY_LOCK = "match:lock:";
    private static final String KEY_RESULT_IDEM = "match:result:idem:";
    private static final String KEY_HEARTBEAT = "match:heartbeat:";
    private static final String KEY_TIMEOUT_ZSET = "match:timeout:zset";
    private static final String KEY_ACTIVE_QUEUES = "match:active:queues";
    private static final Duration LOCK_TTL = Duration.ofSeconds(5);
    private static final Duration RESULT_IDEM_TTL = Duration.ofMinutes(30);
    private static final int LOCK_RETRY = 40;
    private static final long LOCK_RETRY_SLEEP_MS = 25L;
    /** 预留失败后重新入队时提前的等待补偿（毫秒），提高优先级 */
    private static final long RESERVE_FAIL_PRIORITY_BOOST_MS = 60_000L;
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final PlayerRepository playerRepository;
    private final ObjectProvider<PlayerCachePort> playerCachePort;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final SceneBackgroundPrecreator sceneBackgroundPrecreator;
    private final MatchMetrics matchMetrics;
    /** 同进程二次防护；跨实例以 Redis SET NX 为准 */
    private final ConcurrentHashMap<String, Object> queueLocks = new ConcurrentHashMap<>();
    /** 队列分片数；1 时 key 与历史兼容（match:queue:{type}:{mode}） */
    private int queueShards = 1;
    /** shards>1 时是否跨分片配对（按 shard 序加锁防死锁） */
    private boolean crossShardEnabled = true;
    /** 按战力区间确定性路由到固定分片队列 */
    private boolean routeByPowerBand = true;
    /** 本分片等待超过该毫秒后才触发跨分片广播 */
    private long crossShardWaitMs = 30_000L;
    private int heartbeatTtlSec = 30;
    private int queueTimeoutSec = 300;

    public MatchmakingService(
            PlayerRepository playerRepository,
            ObjectProvider<PlayerCachePort> playerCachePort,
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            ObjectProvider<SceneBackgroundPrecreator> sceneBackgroundPrecreator,
            ObjectProvider<MatchMetrics> matchMetrics) {
        this.playerRepository = playerRepository;
        this.playerCachePort = playerCachePort;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.sceneBackgroundPrecreator = sceneBackgroundPrecreator == null
                || sceneBackgroundPrecreator.getIfAvailable() == null
                ? new SceneBackgroundPrecreator()
                : sceneBackgroundPrecreator.getIfAvailable();
        this.matchMetrics = matchMetrics == null ? null : matchMetrics.getIfAvailable();
    }

    @Value("${game.match.queue-shards:1}")
    void setQueueShards(int queueShards) {
        this.queueShards = Math.max(1, queueShards);
    }

    @Value("${game.match.cross-shard-enabled:true}")
    void setCrossShardEnabled(boolean crossShardEnabled) {
        this.crossShardEnabled = crossShardEnabled;
    }

    @Value("${game.match.route-by-power-band:true}")
    void setRouteByPowerBand(boolean routeByPowerBand) {
        this.routeByPowerBand = routeByPowerBand;
    }

    @Value("${game.match.cross-shard-wait-ms:30000}")
    void setCrossShardWaitMs(long crossShardWaitMs) {
        this.crossShardWaitMs = Math.max(0L, crossShardWaitMs);
    }

    @Value("${game.match.heartbeat-ttl-sec:30}")
    void setHeartbeatTtlSec(int heartbeatTtlSec) {
        this.heartbeatTtlSec = Math.max(5, heartbeatTtlSec);
    }

    @Value("${game.match.queue-timeout-sec:300}")
    void setQueueTimeoutSec(int queueTimeoutSec) {
        this.queueTimeoutSec = Math.max(30, queueTimeoutSec);
    }

    public ProtocolMessage handleEnqueueMatch(long playerId, EnqueueMatchCsReq req) {
        if (playerId <= 0) {
            return enqueueRsp(RetCode.PLAYER_NOT_SELECTED, "", 0);
        }
        if (loadPlayerEntry(playerId) != null) {
            return enqueueRsp(RetCode.MATCH_ALREADY_QUEUED, "", 0);
        }
        int matchType = req.getMatchType();
        int modeId = req.getModeId();
        if (matchType != MATCH_TYPE_DUNGEON && matchType != MATCH_TYPE_PVP
                && matchType != MATCH_TYPE_CROSS_DUNGEON) {
            return enqueueRsp(RetCode.INTERNAL_ERROR, "", 0);
        }
        Player player = resolvePlayer(playerId);
        if (player == null) {
            return enqueueRsp(RetCode.PLAYER_NOT_FOUND, "", 0);
        }
        int level = player.getLevel() == null ? 1 : player.getLevel();
        int power = player.getPowerScore() == null ? 0 : player.getPowerScore();
        int minLevel = matchType == MATCH_TYPE_PVP ? MIN_LEVEL_PVP
                : (matchType == MATCH_TYPE_CROSS_DUNGEON ? MIN_LEVEL_CROSS : MIN_LEVEL_DUNGEON);
        if (level < minLevel) {
            return enqueueRsp(RetCode.MATCH_LEVEL_NOT_ENOUGH, "", 0);
        }

        String role = resolveRole(req.getPreferredRole(), player);
        double winRate = resolveWinRate(req.getWinRate());
        String style = req.getStylePreference() == null ? "" : req.getStylePreference().trim();

        String queueId = UUID.randomUUID().toString().replace("-", "");
        long now = System.currentTimeMillis();
        QueueEntry entry = new QueueEntry(queueId, playerId, matchType, modeId, level, power,
                now, role, winRate, style);
        String key = queueKey(matchType, modeId, playerId, power);
        int estimatedWaitSec;
        try {
            withQueueLock(key, () -> {
                appendQueue(key, entry);
                savePlayerEntry(entry);
                touchHeartbeat(playerId);
                scheduleTimeout(playerId, queueId, now);
                saveStatus(new MatchStatus(queueId, STATUS_QUEUED, 0, 0, List.of()));
            });
            if (matchMetrics != null) {
                matchMetrics.recordEnqueue();
            }
            estimatedWaitSec = estimateWaitSec(key, matchType, modeId, playerId, power, now);
            tryMatchMode(key, matchType, modeId, power, now);
        } catch (IllegalStateException e) {
            return enqueueRsp(RetCode.INTERNAL_ERROR, "", 0);
        }
        return enqueueRsp(RetCode.OK, queueId, estimatedWaitSec);
    }

    public ProtocolMessage handleCancelMatch(long playerId, CancelMatchCsReq req) {
        if (playerId <= 0) {
            return cancelRsp(RetCode.PLAYER_NOT_SELECTED);
        }
        QueueEntry entry = loadPlayerEntry(playerId);
        if (entry == null) {
            return cancelRsp(RetCode.MATCH_NOT_IN_QUEUE);
        }
        if (req.getQueueId() != null && !req.getQueueId().isEmpty() && !req.getQueueId().equals(entry.queueId)) {
            return cancelRsp(RetCode.MATCH_NOT_IN_QUEUE);
        }
        String key = queueKey(entry.matchType, entry.modeId, playerId, entry.power);
        try {
            withQueueLock(key, () -> {
                removeFromQueue(key, entry);
                deletePlayerEntry(playerId);
                clearHeartbeat(playerId);
                clearTimeout(playerId);
                MatchStatus status = loadStatus(entry.queueId);
                if (status != null && status.status == STATUS_QUEUED) {
                    status.status = STATUS_CANCELLED;
                    saveStatus(status);
                }
            });
            if (matchMetrics != null) {
                matchMetrics.recordCancel();
            }
        } catch (IllegalStateException e) {
            return cancelRsp(RetCode.INTERNAL_ERROR);
        }
        return cancelRsp(RetCode.OK);
    }

    /** 客户端在排队期间心跳，刷新 match:heartbeat:{playerId} TTL。 */
    public boolean touchHeartbeat(long playerId) {
        if (playerId <= 0 || loadPlayerEntry(playerId) == null) {
            return false;
        }
        stringRedisTemplate.opsForValue().set(
                KEY_HEARTBEAT + playerId, String.valueOf(System.currentTimeMillis()),
                Duration.ofSeconds(heartbeatTtlSec));
        return true;
    }

    MatchStatus getMatchStatus(String queueId) {
        return loadStatus(queueId);
    }

    /**
     * 剔除心跳超时的幽灵玩家；返回清理人数。
     */
    public int purgeGhostPlayers() {
        Set<String> queueKeys = stringRedisTemplate.opsForSet().members(KEY_ACTIVE_QUEUES);
        if (queueKeys == null || queueKeys.isEmpty()) {
            return 0;
        }
        int[] purged = {0};
        for (String key : queueKeys) {
            try {
                withQueueLock(key, () -> {
                    List<QueueEntry> queue = loadQueueRaw(key);
                    List<QueueEntry> alive = new ArrayList<>();
                    for (QueueEntry e : queue) {
                        if (isHeartbeatAlive(e.playerId)) {
                            alive.add(e);
                        } else {
                            purged[0]++;
                            deletePlayerEntry(e.playerId);
                            clearHeartbeat(e.playerId);
                            clearTimeout(e.playerId);
                            MatchStatus status = loadStatus(e.queueId);
                            if (status != null && status.status == STATUS_QUEUED) {
                                status.status = STATUS_TIMEOUT;
                                saveStatus(status);
                            }
                            if (matchMetrics != null) {
                                matchMetrics.recordTimeout();
                            }
                        }
                    }
                    if (alive.size() != queue.size()) {
                        saveQueue(key, alive);
                    }
                });
            } catch (Exception ignored) {
                // 单队列失败不影响其他分片
            }
        }
        int waiting = 0;
        for (String key : queueKeys) {
            waiting += loadQueueRaw(key).size();
        }
        if (matchMetrics != null) {
            matchMetrics.setWaitingCount(waiting);
        }
        return purged[0];
    }

    /**
     * 处理等待超时（默认 5 分钟）：弹出队列并标记 STATUS_TIMEOUT。
     */
    public int processQueueTimeouts() {
        long now = System.currentTimeMillis();
        Set<String> due = stringRedisTemplate.opsForZSet()
                .rangeByScore(KEY_TIMEOUT_ZSET, 0, now);
        if (due == null || due.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String member : due) {
            // member = playerId:queueId
            int sep = member.indexOf(':');
            if (sep <= 0) {
                stringRedisTemplate.opsForZSet().remove(KEY_TIMEOUT_ZSET, member);
                continue;
            }
            long playerId;
            try {
                playerId = Long.parseLong(member.substring(0, sep));
            } catch (NumberFormatException e) {
                stringRedisTemplate.opsForZSet().remove(KEY_TIMEOUT_ZSET, member);
                continue;
            }
            String queueId = member.substring(sep + 1);
            if (timeoutIfStillQueued(playerId, queueId)) {
                count++;
            }
            stringRedisTemplate.opsForZSet().remove(KEY_TIMEOUT_ZSET, member);
        }
        return count;
    }

    public boolean timeoutIfStillQueued(long playerId, String queueId) {
        QueueEntry entry = loadPlayerEntry(playerId);
        if (entry == null || (queueId != null && !queueId.isEmpty() && !queueId.equals(entry.queueId))) {
            return false;
        }
        String key = queueKey(entry.matchType, entry.modeId, playerId, entry.power);
        try {
            final boolean[] timedOut = {false};
            withQueueLock(key, () -> {
                QueueEntry current = loadPlayerEntry(playerId);
                if (current == null || !current.queueId.equals(entry.queueId)) {
                    return;
                }
                MatchStatus status = loadStatus(entry.queueId);
                if (status == null || status.status != STATUS_QUEUED) {
                    return;
                }
                removeFromQueue(key, current);
                deletePlayerEntry(playerId);
                clearHeartbeat(playerId);
                status.status = STATUS_TIMEOUT;
                saveStatus(status);
                timedOut[0] = true;
            });
            if (timedOut[0] && matchMetrics != null) {
                matchMetrics.recordTimeout();
            }
            return timedOut[0];
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /**
     * 配对入口：优先本战力分片；本分片凑不齐或等待过久才跨分片广播。
     */
    private void tryMatchMode(String preferredKey, int matchType, int modeId, int power, long nowMs) {
        // 先扫本分片
        withQueueLock(preferredKey, () -> tryMatchInQueues(List.of(preferredKey), matchType, modeId));
        if (queueShards <= 1 || !crossShardEnabled) {
            return;
        }
        List<QueueEntry> local = loadQueueRaw(preferredKey);
        boolean localTooSmall = local.size() < partySize(matchType);
        if (localTooSmall || shouldCrossShard(preferredKey, nowMs)) {
            List<String> keys = allShardKeys(matchType, modeId, power);
            withOrderedLocks(keys, () -> tryMatchInQueues(keys, matchType, modeId));
        }
    }

    private boolean shouldCrossShard(String preferredKey, long nowMs) {
        if (queueShards <= 1 || !crossShardEnabled) {
            return false;
        }
        List<QueueEntry> local = loadQueueRaw(preferredKey);
        if (local.isEmpty()) {
            return false;
        }
        long oldest = local.stream().mapToLong(e -> e.enqueueTimeMs).min().orElse(nowMs);
        return nowMs - oldest >= crossShardWaitMs;
    }

    private void tryMatchInQueues(List<String> keys, int matchType, int modeId) {
        List<Queued> pooled = new ArrayList<>();
        for (String key : keys) {
            for (QueueEntry e : loadQueue(key)) {
                pooled.add(new Queued(e, key));
            }
        }
        int partySize = partySize(matchType);
        if (pooled.size() < partySize) {
            return;
        }
        List<Queued> party = partySize == PARTY_SIZE_CROSS
                ? findBestPartyOfFour(pooled)
                : findBestPair(pooled);
        if (party == null || party.size() < partySize) {
            return;
        }
        List<Long> ids = new ArrayList<>(party.size());
        for (Queued q : party) {
            ids.add(q.entry.playerId);
        }
        ids.sort(Long::compareTo);
        StringBuilder idem = new StringBuilder(KEY_RESULT_IDEM).append(matchType).append(':').append(modeId);
        for (Long id : ids) {
            idem.append(':').append(id);
        }
        Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(idem.toString(), "1", RESULT_IDEM_TTL);
        if (Boolean.FALSE.equals(first)) {
            return;
        }

        // 两阶段预留：先 PreCreate，成功后再出队
        int sceneId = (matchType == MATCH_TYPE_CROSS_DUNGEON ? 9100 : 9000) + modeId;
        int lineId = 1;
        boolean crossServer = matchType == MATCH_TYPE_CROSS_DUNGEON;
        List<Long> teammates = List.copyOf(ids);
        ReservationResult reservation = reserveInstance(matchType, sceneId, lineId, partySize, teammates);
        if (!reservation.ok) {
            stringRedisTemplate.delete(idem.toString());
            if (matchMetrics != null) {
                matchMetrics.recordReserveFail();
            }
            requeueWithPriorityBoost(party);
            return;
        }

        for (Queued q : party) {
            removeFromQueue(q.shardKey, q.entry);
            deletePlayerEntry(q.entry.playerId);
            clearHeartbeat(q.entry.playerId);
            clearTimeout(q.entry.playerId);
        }
        for (Queued q : party) {
            String ticket = reservation.tickets.getOrDefault(q.entry.playerId, "");
            saveStatus(new MatchStatus(
                    q.entry.queueId, STATUS_MATCHED, sceneId, lineId, teammates, crossServer,
                    reservation.prepId, reservation.instanceId, reservation.host, reservation.port,
                    ticket, reservation.preloadReady));
        }
        if (matchMetrics != null) {
            matchMetrics.recordSuccessParty(party.size());
        }
    }

    private ReservationResult reserveInstance(
            int matchType, int sceneId, int lineId, int capacity, List<Long> teammates) {
        if (matchType != MATCH_TYPE_CROSS_DUNGEON) {
            // 非跨服：轻量预留成功（本地场景资源默认可用）
            return ReservationResult.ok("", "", "127.0.0.1", 0, Map.of(), true);
        }
        var prep = sceneBackgroundPrecreator.prepareCrossDungeon(
                sceneId, lineId, capacity, teammates, System.currentTimeMillis());
        if (prep.status() == SceneBackgroundPrecreator.PrepStatus.FAILED) {
            return ReservationResult.fail();
        }
        return ReservationResult.ok(
                prep.prepId(), prep.instanceId(), prep.host(), prep.port(),
                prep.tickets(), prep.status() == SceneBackgroundPrecreator.PrepStatus.READY);
    }

    /** 预留失败：放回队列并提高等待补偿权重（提前 enqueueTime）。 */
    private void requeueWithPriorityBoost(List<Queued> party) {
        long boostBase = System.currentTimeMillis() - RESERVE_FAIL_PRIORITY_BOOST_MS;
        for (Queued q : party) {
            QueueEntry boosted = q.entry;
            boosted.enqueueTimeMs = Math.min(boosted.enqueueTimeMs, boostBase);
            boosted.priorityBoost++;
            // 仍在队列中则只刷新条目；已被误删时补回
            List<QueueEntry> queue = loadQueueRaw(q.shardKey);
            boolean present = queue.stream().anyMatch(e -> e.queueId.equals(boosted.queueId));
            if (!present) {
                queue.add(boosted);
            } else {
                for (int i = 0; i < queue.size(); i++) {
                    if (queue.get(i).queueId.equals(boosted.queueId)) {
                        queue.set(i, boosted);
                        break;
                    }
                }
            }
            saveQueue(q.shardKey, queue);
            savePlayerEntry(boosted);
            touchHeartbeat(boosted.playerId);
        }
    }

    private static int partySize(int matchType) {
        return matchType == MATCH_TYPE_CROSS_DUNGEON ? PARTY_SIZE_CROSS : PARTY_SIZE_DEFAULT;
    }

    private List<Queued> findBestPair(List<Queued> pooled) {
        Queued bestA = null;
        Queued bestB = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < pooled.size(); i++) {
            Queued a = pooled.get(i);
            if (a.entry.playerId == 0 || loadPlayerEntry(a.entry.playerId) == null) {
                continue;
            }
            for (int j = i + 1; j < pooled.size(); j++) {
                Queued b = pooled.get(j);
                if (b.entry.playerId == 0 || a.entry.playerId == b.entry.playerId
                        || loadPlayerEntry(b.entry.playerId) == null) {
                    continue;
                }
                if (!withinBand(a.entry, b.entry)) {
                    continue;
                }
                double score = MatchCompatibilityScorer.score(toProfile(a.entry), toProfile(b.entry));
                if (score > bestScore) {
                    bestScore = score;
                    bestA = a;
                    bestB = b;
                }
            }
        }
        if (bestA == null || bestB == null) {
            return null;
        }
        return List.of(bestA, bestB);
    }

    /** 跨服 4 人：在带宽内选互补总分最高的四人组。 */
    private List<Queued> findBestPartyOfFour(List<Queued> pooled) {
        List<Queued> valid = new ArrayList<>();
        for (Queued q : pooled) {
            if (q.entry.playerId != 0 && loadPlayerEntry(q.entry.playerId) != null) {
                valid.add(q);
            }
        }
        if (valid.size() < PARTY_SIZE_CROSS) {
            return null;
        }
        List<Queued> best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        valid.sort((a, b) -> Long.compare(a.entry.enqueueTimeMs, b.entry.enqueueTimeMs));
        for (int i = 0; i <= valid.size() - PARTY_SIZE_CROSS; i++) {
            List<Queued> cand = new ArrayList<>(PARTY_SIZE_CROSS);
            Queued seed = valid.get(i);
            cand.add(seed);
            for (int j = i + 1; j < valid.size() && cand.size() < PARTY_SIZE_CROSS; j++) {
                Queued other = valid.get(j);
                boolean ok = true;
                for (Queued m : cand) {
                    if (m.entry.playerId == other.entry.playerId || !withinBand(m.entry, other.entry)) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    cand.add(other);
                }
            }
            if (cand.size() == PARTY_SIZE_CROSS) {
                double score = partyCompatibilityScore(cand);
                if (score > bestScore) {
                    bestScore = score;
                    best = List.copyOf(cand);
                }
            }
        }
        return best;
    }

    private static double partyCompatibilityScore(List<Queued> party) {
        double sum = 0;
        int pairs = 0;
        for (int i = 0; i < party.size(); i++) {
            for (int j = i + 1; j < party.size(); j++) {
                sum += MatchCompatibilityScorer.score(toProfile(party.get(i).entry), toProfile(party.get(j).entry));
                pairs++;
            }
        }
        return pairs == 0 ? Double.NEGATIVE_INFINITY : sum / pairs;
    }

    private static MatchCompatibilityScorer.Profile toProfile(QueueEntry e) {
        return new MatchCompatibilityScorer.Profile(
                e.level,
                e.power,
                e.enqueueTimeMs,
                e.role,
                e.winRate,
                blankToNull(e.stylePreference));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private int estimateWaitSec(String preferredKey, int matchType, int modeId, long playerId, int power, long nowMs) {
        int need = partySize(matchType) - 1;
        int peers = 0;
        List<String> keys = shouldCrossShard(preferredKey, nowMs)
                ? allShardKeys(matchType, modeId, power) : List.of(preferredKey);
        for (String key : keys) {
            for (QueueEntry e : loadQueueRaw(key)) {
                if (e.playerId != playerId) {
                    peers++;
                }
            }
        }
        if (peers >= need) {
            return Math.min(ESTIMATED_WAIT_BASE_SEC, 5);
        }
        int missing = need - peers;
        return Math.min(ESTIMATED_WAIT_MAX_SEC, ESTIMATED_WAIT_BASE_SEC + missing * ESTIMATED_WAIT_PER_MISSING_SEC);
    }

    private static String resolveRole(String preferredRole, Player player) {
        if (preferredRole != null && !preferredRole.isBlank()) {
            return preferredRole.trim().toLowerCase();
        }
        return roleFromTalent(player == null ? null : player.getTalentJson());
    }

    private static String roleFromTalent(String talentJson) {
        if (talentJson == null || talentJson.isBlank() || "{}".equals(talentJson.trim())) {
            return "dps";
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root =
                    new ObjectMapper().readTree(talentJson);
            int bestId = 0;
            int bestLv = -1;
            var fields = root.fields();
            while (fields.hasNext()) {
                var e = fields.next();
                int id;
                try {
                    id = Integer.parseInt(e.getKey());
                } catch (NumberFormatException ex) {
                    continue;
                }
                int lv = e.getValue().isNumber() ? e.getValue().asInt() : 0;
                if (lv > bestLv) {
                    bestLv = lv;
                    bestId = id;
                }
            }
            return switch (bestId) {
                case 1 -> "tank";
                case 2 -> "heal";
                default -> "dps";
            };
        } catch (Exception e) {
            return "dps";
        }
    }

    /** 协议默认 0 表示未上报，映射为 -1 让评分器忽略胜率项。 */
    private static double resolveWinRate(double reported) {
        if (reported < 0) {
            return -1;
        }
        if (reported == 0.0) {
            return -1;
        }
        return Math.min(1.0, reported);
    }

    private record Queued(QueueEntry entry, String shardKey) {
    }

    private record ReservationResult(
            boolean ok, String prepId, String instanceId, String host, int port,
            Map<Long, String> tickets, boolean preloadReady) {
        static ReservationResult fail() {
            return new ReservationResult(false, "", "", "", 0, Map.of(), false);
        }

        static ReservationResult ok(String prepId, String instanceId, String host, int port,
                                    Map<Long, String> tickets, boolean preloadReady) {
            return new ReservationResult(true, prepId, instanceId, host, port, tickets, preloadReady);
        }
    }

    private static boolean withinBand(QueueEntry a, QueueEntry b) {
        return Math.abs(a.level - b.level) <= LEVEL_BAND
                && Math.abs(a.power - b.power) <= POWER_BAND;
    }

    private Object lockFor(String key) {
        return queueLocks.computeIfAbsent(key, k -> new Object());
    }

    private void withQueueLock(String key, Runnable action) {
        withOrderedLocks(List.of(key), action);
    }

    private void withOrderedLocks(List<String> keys, Runnable action) {
        List<String> tokens = new ArrayList<>(keys.size());
        try {
            for (String key : keys) {
                tokens.add(acquireRedisLock(key));
            }
            runWithLocalLocks(keys, 0, action);
        } finally {
            for (int i = keys.size() - 1; i >= 0; i--) {
                if (i < tokens.size()) {
                    releaseRedisLock(keys.get(i), tokens.get(i));
                }
            }
        }
    }

    private void runWithLocalLocks(List<String> keys, int idx, Runnable action) {
        if (idx >= keys.size()) {
            action.run();
            return;
        }
        synchronized (lockFor(keys.get(idx))) {
            runWithLocalLocks(keys, idx + 1, action);
        }
    }

    private String acquireRedisLock(String key) {
        String lockKey = KEY_LOCK + key;
        String token = UUID.randomUUID().toString();
        for (int i = 0; i < LOCK_RETRY; i++) {
            Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, token, LOCK_TTL);
            if (Boolean.TRUE.equals(ok)) {
                return token;
            }
            try {
                Thread.sleep(LOCK_RETRY_SLEEP_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("匹配抢锁被中断", ie);
            }
        }
        throw new IllegalStateException("匹配队列繁忙，请稍后重试: " + key);
    }

    private void releaseRedisLock(String key, String token) {
        stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(KEY_LOCK + key), token);
    }

    private void appendQueue(String key, QueueEntry entry) {
        List<QueueEntry> queue = loadQueueRaw(key);
        queue.add(entry);
        saveQueue(key, queue);
    }

    private void removeFromQueue(String key, QueueEntry entry) {
        List<QueueEntry> queue = loadQueueRaw(key);
        queue.removeIf(e -> e.queueId.equals(entry.queueId));
        saveQueue(key, queue);
    }

    /** 加载队列并剔除心跳已失效的幽灵条目。 */
    private List<QueueEntry> loadQueue(String key) {
        List<QueueEntry> queue = loadQueueRaw(key);
        List<QueueEntry> alive = new ArrayList<>(queue.size());
        boolean changed = false;
        for (QueueEntry e : queue) {
            if (isHeartbeatAlive(e.playerId)) {
                alive.add(e);
            } else {
                changed = true;
                deletePlayerEntry(e.playerId);
                clearHeartbeat(e.playerId);
                clearTimeout(e.playerId);
            }
        }
        if (changed) {
            saveQueue(key, alive);
        }
        return alive;
    }

    private List<QueueEntry> loadQueueRaw(String key) {
        try {
            String json = stringRedisTemplate.opsForValue().get(KEY_QUEUE + key);
            if (json == null || json.isBlank()) {
                return new ArrayList<>();
            }
            return objectMapper.readValue(json, new TypeReference<List<QueueEntry>>() {
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void saveQueue(String key, List<QueueEntry> queue) {
        try {
            stringRedisTemplate.opsForValue().set(KEY_QUEUE + key, objectMapper.writeValueAsString(queue), TTL);
            stringRedisTemplate.opsForSet().add(KEY_ACTIVE_QUEUES, key);
        } catch (Exception e) {
            throw new IllegalStateException("匹配队列写入 Redis 失败", e);
        }
    }

    private boolean isHeartbeatAlive(long playerId) {
        String hb = stringRedisTemplate.opsForValue().get(KEY_HEARTBEAT + playerId);
        return hb != null && !hb.isBlank();
    }

    private void clearHeartbeat(long playerId) {
        stringRedisTemplate.delete(KEY_HEARTBEAT + playerId);
    }

    private void scheduleTimeout(long playerId, String queueId, long enqueueMs) {
        double score = enqueueMs + queueTimeoutSec * 1000L;
        stringRedisTemplate.opsForZSet().add(KEY_TIMEOUT_ZSET, playerId + ":" + queueId, score);
    }

    private void clearTimeout(long playerId) {
        Set<String> members = stringRedisTemplate.opsForZSet().range(KEY_TIMEOUT_ZSET, 0, -1);
        if (members == null) {
            return;
        }
        String prefix = playerId + ":";
        for (String m : members) {
            if (m.startsWith(prefix)) {
                stringRedisTemplate.opsForZSet().remove(KEY_TIMEOUT_ZSET, m);
            }
        }
    }

    private QueueEntry loadPlayerEntry(long playerId) {
        try {
            String json = stringRedisTemplate.opsForValue().get(KEY_PLAYER + playerId);
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, QueueEntry.class);
        } catch (Exception e) {
            return null;
        }
    }

    private void savePlayerEntry(QueueEntry entry) {
        try {
            stringRedisTemplate.opsForValue().set(
                    KEY_PLAYER + entry.playerId, objectMapper.writeValueAsString(entry), TTL);
        } catch (Exception e) {
            throw new IllegalStateException("匹配玩家状态写入 Redis 失败", e);
        }
    }

    private void deletePlayerEntry(long playerId) {
        stringRedisTemplate.delete(KEY_PLAYER + playerId);
    }

    private MatchStatus loadStatus(String queueId) {
        try {
            String json = stringRedisTemplate.opsForValue().get(KEY_STATUS + queueId);
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, MatchStatus.class);
        } catch (Exception e) {
            return null;
        }
    }

    private void saveStatus(MatchStatus status) {
        try {
            stringRedisTemplate.opsForValue().set(
                    KEY_STATUS + status.queueId, objectMapper.writeValueAsString(status), TTL);
        } catch (Exception e) {
            throw new IllegalStateException("匹配状态写入 Redis 失败", e);
        }
    }

    private Player resolvePlayer(long playerId) {
        PlayerCachePort cache = playerCachePort.getIfAvailable();
        if (cache != null) {
            Player cached = cache.findById(playerId);
            if (cached != null) {
                return cached;
            }
        }
        return playerRepository.findById(playerId).orElse(null);
    }

    /**
     * 确定性哈希路由：优先按战力区间（0-1000, 1001-2000...）固定到分片；
     * 再按 playerId 做子分片，降低热点。
     */
    private String queueKey(int matchType, int modeId, long playerId, int power) {
        int shards = Math.max(1, queueShards);
        if (!routeByPowerBand) {
            if (shards == 1) {
                return matchType + ":" + modeId;
            }
            int shard = (int) Math.floorMod(playerId, shards);
            return matchType + ":" + modeId + ":s" + shard;
        }
        int powerBucket = Math.max(0, power) / POWER_ROUTE_BAND;
        if (shards == 1) {
            return matchType + ":" + modeId + ":pb" + powerBucket;
        }
        int shard = (int) Math.floorMod(playerId, shards);
        return matchType + ":" + modeId + ":pb" + powerBucket + ":s" + shard;
    }

    private List<String> allShardKeys(int matchType, int modeId, int power) {
        int shards = Math.max(1, queueShards);
        int powerBucket = Math.max(0, power) / POWER_ROUTE_BAND;
        // 跨分片：同战力桶的所有子分片 + 相邻战力桶（±1），减少无关跨机房查询
        Set<Integer> buckets = new HashSet<>();
        buckets.add(powerBucket);
        if (powerBucket > 0) {
            buckets.add(powerBucket - 1);
        }
        buckets.add(powerBucket + 1);
        List<String> keys = new ArrayList<>();
        for (int pb : buckets) {
            if (shards == 1) {
                keys.add(matchType + ":" + modeId + (routeByPowerBand ? ":pb" + pb : ""));
            } else {
                for (int i = 0; i < shards; i++) {
                    if (routeByPowerBand) {
                        keys.add(matchType + ":" + modeId + ":pb" + pb + ":s" + i);
                    } else {
                        keys.add(matchType + ":" + modeId + ":s" + i);
                    }
                }
            }
        }
        if (!routeByPowerBand && shards == 1) {
            return List.of(matchType + ":" + modeId);
        }
        Collections.sort(keys);
        return keys;
    }

    private static ProtocolMessage enqueueRsp(int retcode, String queueId, int estimatedWaitSec) {
        EnqueueMatchScRsp body = EnqueueMatchScRsp.newBuilder()
                .setRetcode(retcode)
                .setQueueId(queueId == null ? "" : queueId)
                .setEstimatedWaitSec(estimatedWaitSec)
                .build();
        return new ProtocolMessage(MessageId.ENQUEUE_MATCH_SC_RSP, body.toByteArray());
    }

    private static ProtocolMessage cancelRsp(int retcode) {
        CancelMatchScRsp body = CancelMatchScRsp.newBuilder().setRetcode(retcode).build();
        return new ProtocolMessage(MessageId.CANCEL_MATCH_SC_RSP, body.toByteArray());
    }

    static final class QueueEntry {
        public String queueId;
        public long playerId;
        public int matchType;
        public int modeId;
        public int level;
        public int power;
        /** 入队时间（毫秒），用于互补评分等待加成 */
        public long enqueueTimeMs;
        /** 职业定位：tank / heal / dps */
        public String role = "dps";
        /** 胜率 [0,1]；-1 表示未知 */
        public double winRate = -1;
        public String stylePreference = "";
        /** 预留失败后的优先级提升次数 */
        public int priorityBoost;

        public QueueEntry() {
        }

        QueueEntry(String queueId, long playerId, int matchType, int modeId, int level, int power,
                   long enqueueTimeMs) {
            this(queueId, playerId, matchType, modeId, level, power, enqueueTimeMs, "dps", -1, "");
        }

        QueueEntry(String queueId, long playerId, int matchType, int modeId, int level, int power,
                   long enqueueTimeMs, String role, double winRate, String stylePreference) {
            this.queueId = queueId;
            this.playerId = playerId;
            this.matchType = matchType;
            this.modeId = modeId;
            this.level = level;
            this.power = power;
            this.enqueueTimeMs = enqueueTimeMs;
            this.role = role == null || role.isBlank() ? "dps" : role;
            this.winRate = winRate;
            this.stylePreference = stylePreference == null ? "" : stylePreference;
        }
    }

    static final class MatchStatus {
        public String queueId;
        public int status;
        public int sceneId;
        public int lineId;
        public List<Long> teammateIds;
        public boolean crossServer;
        public String prepId;
        public String instanceId;
        public String host;
        public int port;
        public String sessionTicket;
        public boolean preloadReady;

        public MatchStatus() {
        }

        MatchStatus(String queueId, int status, int sceneId, int lineId, List<Long> teammateIds) {
            this(queueId, status, sceneId, lineId, teammateIds, false);
        }

        MatchStatus(String queueId, int status, int sceneId, int lineId, List<Long> teammateIds,
                    boolean crossServer) {
            this(queueId, status, sceneId, lineId, teammateIds, crossServer,
                    "", "", "", 0, "", false);
        }

        MatchStatus(String queueId, int status, int sceneId, int lineId, List<Long> teammateIds,
                    boolean crossServer, String prepId, String instanceId, String host, int port,
                    String sessionTicket, boolean preloadReady) {
            this.queueId = queueId;
            this.status = status;
            this.sceneId = sceneId;
            this.lineId = lineId;
            this.teammateIds = teammateIds;
            this.crossServer = crossServer;
            this.prepId = prepId;
            this.instanceId = instanceId;
            this.host = host;
            this.port = port;
            this.sessionTicket = sessionTicket;
            this.preloadReady = preloadReady;
        }
    }
}
