package cn.itcast.demo.mymmorpg.world.resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 大世界生态：采集/宝箱/机关刷新与全局 CD（Redis），无 Redis 时回退本地。
 */
@Service
public class WorldResourceService {

    private static final Logger log = LoggerFactory.getLogger(WorldResourceService.class);
    private static final String CD_KEY = "world:resource:cd:";
    private static final String STATE_KEY = "world:resource:state:";

    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final MechanismStateMachine mechanisms = new MechanismStateMachine();
    private final ConcurrentHashMap<String, RespawnPoint> points = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> localCdUntil = new ConcurrentHashMap<>();

    public WorldResourceService(ObjectProvider<StringRedisTemplate> redisProvider) {
        this.redisProvider = redisProvider;
    }

    public WorldResourceService() {
        this.redisProvider = null;
    }

    public void registerPoint(RespawnPoint point) {
        points.put(point.pointId(), point);
    }

    public List<RespawnPoint> listPoints(int worldId) {
        List<RespawnPoint> out = new ArrayList<>();
        for (RespawnPoint p : points.values()) {
            if (p.worldId() == worldId) {
                out.add(p);
            }
        }
        return out;
    }

    /**
     * 采集/开箱：按 syncMode 写入全局或玩家维度 CD。
     * WORLD_SHARED 成功后其它客户端应通过 AOI 资源状态同步 / 进场 snapshot 刷新。
     */
    public Map<String, Object> collect(String pointId, long playerId, long nowMs) {
        RespawnPoint point = points.get(pointId);
        if (point == null) {
            return Map.of("ok", false, "error", "point_not_found");
        }
        RespawnPoint.SyncMode mode = point.syncMode() == null
                ? RespawnPoint.SyncMode.WORLD_SHARED : point.syncMode();
        String cdKey = mode == RespawnPoint.SyncMode.PER_PLAYER
                ? pointId + ":p:" + playerId : pointId;
        if (isOnCooldown(cdKey, nowMs)) {
            return Map.of("ok", false, "error", "on_cooldown", "pointId", pointId,
                    "syncMode", mode.name(), "remainMs", remainCdMs(cdKey, nowMs));
        }
        if (point.oneShot()) {
            markCooldown(cdKey, Duration.ofDays(3650), nowMs);
        } else {
            markCooldown(cdKey, Duration.ofSeconds(Math.max(1, point.respawnSeconds())), nowMs);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("pointId", pointId);
        body.put("playerId", playerId);
        body.put("kind", point.kind().name());
        body.put("templateId", point.templateId());
        body.put("oneShot", point.oneShot());
        body.put("respawnSeconds", point.respawnSeconds());
        body.put("syncMode", mode.name());
        body.put("clientRefresh", mode == RespawnPoint.SyncMode.WORLD_SHARED
                ? "broadcast_aoi_resource_state" : "local_only");
        return body;
    }

    /** 查询资源点对某玩家是否仍可采（用于客户端刷新判定）。 */
    public boolean isAvailable(String pointId, long playerId, long nowMs) {
        RespawnPoint point = points.get(pointId);
        if (point == null) {
            return false;
        }
        RespawnPoint.SyncMode mode = point.syncMode() == null
                ? RespawnPoint.SyncMode.WORLD_SHARED : point.syncMode();
        String cdKey = mode == RespawnPoint.SyncMode.PER_PLAYER
                ? pointId + ":p:" + playerId : pointId;
        return !isOnCooldown(cdKey, nowMs);
    }

    public MechanismStateMachine.TransitionResult activateMechanism(
            String mechanismId, boolean oneShot, long cooldownMs, long nowMs) {
        MechanismStateMachine.TransitionResult r =
                mechanisms.activate(mechanismId, oneShot, cooldownMs, nowMs);
        if (r.ok()) {
            persistMechanismState(mechanismId, r.to());
        }
        return r;
    }

    public MechanismStateMachine.TransitionResult completeMechanism(
            String mechanismId, long cooldownMs, long nowMs) {
        MechanismStateMachine.TransitionResult r = mechanisms.complete(mechanismId, cooldownMs, nowMs);
        if (r.ok()) {
            persistMechanismState(mechanismId, r.to());
            if (r.to() == MechanismStateMachine.State.COOLDOWN) {
                markCooldown("mech:" + mechanismId, Duration.ofMillis(cooldownMs), nowMs);
            }
        }
        return r;
    }

    public void tick(long nowMs) {
        mechanisms.tick(nowMs);
        localCdUntil.entrySet().removeIf(e -> e.getValue() <= nowMs);
    }

    public Map<String, Object> snapshot(int worldId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("worldId", worldId);
        body.put("points", listPoints(worldId));
        body.put("mechanisms", mechanisms.snapshot());
        return body;
    }

    public MechanismStateMachine mechanisms() {
        return mechanisms;
    }

    private boolean isOnCooldown(String pointId, long nowMs) {
        StringRedisTemplate redis = redis();
        if (redis != null) {
            try {
                Boolean has = redis.hasKey(CD_KEY + pointId);
                if (Boolean.TRUE.equals(has)) {
                    return true;
                }
            } catch (Exception e) {
                log.warn("resource cd check redis failed: {}", e.getMessage());
            }
        }
        Long until = localCdUntil.get(pointId);
        return until != null && until > nowMs;
    }

    private long remainCdMs(String pointId, long nowMs) {
        StringRedisTemplate redis = redis();
        if (redis != null) {
            try {
                Long ttl = redis.getExpire(CD_KEY + pointId);
                if (ttl != null && ttl > 0) {
                    return ttl * 1000L;
                }
            } catch (Exception ignored) {
                // fallback local
            }
        }
        Long until = localCdUntil.get(pointId);
        return until == null ? 0L : Math.max(0L, until - nowMs);
    }

    private void markCooldown(String pointId, Duration ttl, long nowMs) {
        StringRedisTemplate redis = redis();
        if (redis != null) {
            try {
                redis.opsForValue().set(CD_KEY + pointId, String.valueOf(nowMs), ttl);
                return;
            } catch (Exception e) {
                log.warn("resource cd write redis failed: {}", e.getMessage());
            }
        }
        localCdUntil.put(pointId, nowMs + ttl.toMillis());
    }

    private void persistMechanismState(String mechanismId, MechanismStateMachine.State state) {
        StringRedisTemplate redis = redis();
        if (redis == null) {
            return;
        }
        try {
            redis.opsForValue().set(STATE_KEY + mechanismId, state.name(), Duration.ofDays(7));
        } catch (Exception e) {
            log.warn("mechanism state persist failed: {}", e.getMessage());
        }
    }

    private StringRedisTemplate redis() {
        return redisProvider == null ? null : redisProvider.getIfAvailable();
    }
}
