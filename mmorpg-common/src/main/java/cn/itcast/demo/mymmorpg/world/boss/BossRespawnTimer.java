package cn.itcast.demo.mymmorpg.world.boss;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 世界 Boss 全局刷新锁：击杀后强制写入 Redis（TTL），并带选主（Leader Election），
 * 确保仅主节点执行刷新，避免多节点重复刷出。
 */
@Component
public class BossRespawnTimer {

    private static final Logger log = LoggerFactory.getLogger(BossRespawnTimer.class);
    private static final String KEY = "world:boss:respawn:";
    private static final String LOCK = "world:boss:kill-lock:";
    private static final String LEADER = "world:boss:leader:";

    public enum BossStatus { ALIVE, DEAD_COOLDOWN, READY }

    public record BossTimerState(
            String bossId,
            BossStatus status,
            long killedAtMs,
            long respawnAtMs,
            long killerPlayerId,
            int lineId) {
    }

    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final ConcurrentHashMap<String, BossTimerState> local = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> localLeaders = new ConcurrentHashMap<>();
    private volatile String localNodeId = "scene-local";
    private volatile long leaderLeaseMs = 15_000L;

    public BossRespawnTimer(ObjectProvider<StringRedisTemplate> redisProvider) {
        this.redisProvider = redisProvider;
    }

    public BossRespawnTimer() {
        this.redisProvider = null;
    }

    public void configureNode(String nodeId, long leaderLeaseMs) {
        if (nodeId != null && !nodeId.isBlank()) {
            this.localNodeId = nodeId.trim();
        }
        this.leaderLeaseMs = Math.max(3_000L, leaderLeaseMs);
    }

    /**
     * 击杀结算：SETNX 全局锁 + 写入刷新截止时间。多线并发仅首个成功。
     */
    public Map<String, Object> markKilled(String bossId, long killerPlayerId, int lineId, long respawnSeconds, long nowMs) {
        String id = normalize(bossId);
        long cd = Math.max(1L, respawnSeconds);
        Duration ttl = Duration.ofSeconds(cd);
        StringRedisTemplate redis = redis();
        if (redis != null) {
            try {
                Boolean ok = redis.opsForValue().setIfAbsent(LOCK + id, killerPlayerId + ":" + lineId, ttl);
                if (!Boolean.TRUE.equals(ok)) {
                    return Map.of("ok", false, "error", "already_killed", "bossId", id,
                            "status", status(id, nowMs).name());
                }
                long respawnAt = nowMs + ttl.toMillis();
                String payload = nowMs + "|" + respawnAt + "|" + killerPlayerId + "|" + lineId;
                redis.opsForValue().set(KEY + id, payload, ttl);
                BossTimerState state = new BossTimerState(id, BossStatus.DEAD_COOLDOWN, nowMs, respawnAt, killerPlayerId, lineId);
                local.put(id, state);
                return toView(state, true);
            } catch (Exception e) {
                log.warn("boss respawn mark redis failed: {}", e.getMessage());
            }
        }
        BossTimerState existing = local.get(id);
        if (existing != null && existing.respawnAtMs() > nowMs) {
            return Map.of("ok", false, "error", "already_killed", "bossId", id, "status", BossStatus.DEAD_COOLDOWN.name());
        }
        long respawnAt = nowMs + ttl.toMillis();
        BossTimerState state = new BossTimerState(id, BossStatus.DEAD_COOLDOWN, nowMs, respawnAt, killerPlayerId, lineId);
        local.put(id, state);
        return toView(state, true);
    }

    public BossStatus status(String bossId, long nowMs) {
        BossTimerState s = peek(bossId, nowMs);
        return s == null ? BossStatus.ALIVE : s.status();
    }

    public boolean canSpawn(String bossId, long nowMs) {
        BossStatus s = status(bossId, nowMs);
        return s == BossStatus.ALIVE || s == BossStatus.READY;
    }

    /**
     * 选主：SETNX + TTL 租约；仅 leader 可执行实际刷怪。续租成功同样视为持有主身份。
     */
    public boolean tryBecomeLeader(String bossId, long nowMs) {
        String id = normalize(bossId);
        StringRedisTemplate redis = redis();
        Duration lease = Duration.ofMillis(leaderLeaseMs);
        if (redis != null) {
            try {
                String key = LEADER + id;
                Boolean ok = redis.opsForValue().setIfAbsent(key, localNodeId, lease);
                if (Boolean.TRUE.equals(ok)) {
                    localLeaders.put(id, localNodeId);
                    return true;
                }
                String cur = redis.opsForValue().get(key);
                if (localNodeId.equals(cur)) {
                    redis.expire(key, lease);
                    localLeaders.put(id, localNodeId);
                    return true;
                }
                return false;
            } catch (Exception e) {
                log.warn("boss leader election redis failed: {}", e.getMessage());
            }
        }
        String existing = localLeaders.putIfAbsent(id, localNodeId);
        return existing == null || localNodeId.equals(existing);
    }

    public boolean isLeader(String bossId) {
        String id = normalize(bossId);
        StringRedisTemplate redis = redis();
        if (redis != null) {
            try {
                String cur = redis.opsForValue().get(LEADER + id);
                return localNodeId.equals(cur);
            } catch (Exception e) {
                log.warn("boss leader check redis failed: {}", e.getMessage());
            }
        }
        return localNodeId.equals(localLeaders.get(id));
    }

    /**
     * 仅主节点允许在 READY/ALIVE 时刷出 Boss；非主返回 false。
     */
    public boolean tryRespawnAsLeader(String bossId, long nowMs) {
        if (!canSpawn(bossId, nowMs)) {
            return false;
        }
        if (!tryBecomeLeader(bossId, nowMs)) {
            return false;
        }
        // 刷出后清冷却键，保持 ALIVE；选主租约仍保留至自然过期
        forceReset(bossId);
        tryBecomeLeader(bossId, nowMs);
        return true;
    }

    public String currentLeader(String bossId) {
        String id = normalize(bossId);
        StringRedisTemplate redis = redis();
        if (redis != null) {
            try {
                String cur = redis.opsForValue().get(LEADER + id);
                if (cur != null) {
                    return cur;
                }
            } catch (Exception ignored) {
            }
        }
        return localLeaders.getOrDefault(id, "");
    }

    public BossTimerState peek(String bossId, long nowMs) {
        String id = normalize(bossId);
        StringRedisTemplate redis = redis();
        if (redis != null) {
            try {
                String payload = redis.opsForValue().get(KEY + id);
                if (payload != null && !payload.isBlank()) {
                    BossTimerState parsed = parse(id, payload);
                    if (parsed.respawnAtMs() <= nowMs) {
                        redis.delete(KEY + id);
                        redis.delete(LOCK + id);
                        local.remove(id);
                        return new BossTimerState(id, BossStatus.READY, parsed.killedAtMs(), parsed.respawnAtMs(),
                                parsed.killerPlayerId(), parsed.lineId());
                    }
                    return parsed;
                }
            } catch (Exception e) {
                log.warn("boss respawn peek redis failed: {}", e.getMessage());
            }
        }
        BossTimerState localState = local.get(id);
        if (localState == null) {
            return null;
        }
        if (localState.respawnAtMs() <= nowMs) {
            local.remove(id, localState);
            return new BossTimerState(id, BossStatus.READY, localState.killedAtMs(), localState.respawnAtMs(),
                    localState.killerPlayerId(), localState.lineId());
        }
        return localState;
    }

    public Map<String, Object> forceReset(String bossId) {
        String id = normalize(bossId);
        StringRedisTemplate redis = redis();
        if (redis != null) {
            try {
                redis.delete(KEY + id);
                redis.delete(LOCK + id);
            } catch (Exception e) {
                log.warn("boss respawn reset redis failed: {}", e.getMessage());
            }
        }
        local.remove(id);
        return Map.of("ok", true, "bossId", id, "status", BossStatus.ALIVE.name());
    }

    private static Map<String, Object> toView(BossTimerState state, boolean ok) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", ok);
        m.put("bossId", state.bossId());
        m.put("status", state.status().name());
        m.put("killedAtMs", state.killedAtMs());
        m.put("respawnAtMs", state.respawnAtMs());
        m.put("remainMs", Math.max(0L, state.respawnAtMs() - System.currentTimeMillis()));
        m.put("killerPlayerId", state.killerPlayerId());
        m.put("lineId", state.lineId());
        return m;
    }

    private static BossTimerState parse(String id, String payload) {
        String[] parts = payload.split("\\|");
        long killed = Long.parseLong(parts[0]);
        long respawn = Long.parseLong(parts[1]);
        long killer = parts.length > 2 ? Long.parseLong(parts[2]) : 0L;
        int line = parts.length > 3 ? Integer.parseInt(parts[3]) : 0;
        return new BossTimerState(id, BossStatus.DEAD_COOLDOWN, killed, respawn, killer, line);
    }

    private static String normalize(String bossId) {
        return bossId == null || bossId.isBlank() ? "unknown" : bossId.trim();
    }

    private StringRedisTemplate redis() {
        return redisProvider == null ? null : redisProvider.getIfAvailable();
    }
}
