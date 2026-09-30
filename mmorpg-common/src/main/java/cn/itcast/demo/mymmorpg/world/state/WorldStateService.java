package cn.itcast.demo.mymmorpg.world.state;

import cn.itcast.demo.mymmorpg.world.resource.MechanismStateMachine;
import cn.itcast.demo.mymmorpg.world.resource.WorldResourceService;
import cn.itcast.demo.mymmorpg.world.time.WorldTimeService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 大世界权威状态聚合：天气/时间 + 资源 CD + 机关 FSM + Redis Bitmap 解谜位 + 房主世界。
 * <p>
 * 可独立部署为 WorldState-Service；当前默认内置于 Scene，由 {@code OpenWorldRuntimeService} 驱动 tick。
 */
@Service
public class WorldStateService {

    private final WorldTimeService timeService;
    private final WorldResourceService resourceService;
    private final WorldStateBitmap bitmap;
    private final ConcurrentHashMap<Long, HostWorldContext> hostWorlds = new ConcurrentHashMap<>();

    public WorldStateService(
            ObjectProvider<WorldTimeService> timeService,
            ObjectProvider<WorldResourceService> resourceService,
            ObjectProvider<StringRedisTemplate> redisProvider) {
        this.timeService = resolve(timeService, WorldTimeService::new);
        this.resourceService = resolve(resourceService, WorldResourceService::new);
        StringRedisTemplate redis = redisProvider == null ? null : redisProvider.getIfAvailable();
        this.bitmap = new WorldStateBitmap(redis);
    }

    public WorldStateService() {
        this.timeService = new WorldTimeService();
        this.resourceService = new WorldResourceService();
        this.bitmap = new WorldStateBitmap();
    }

    public WorldStateService(WorldTimeService timeService, WorldResourceService resourceService) {
        this.timeService = timeService == null ? new WorldTimeService() : timeService;
        this.resourceService = resourceService == null ? new WorldResourceService() : resourceService;
        this.bitmap = new WorldStateBitmap();
    }

    private static <T> T resolve(ObjectProvider<T> provider, java.util.function.Supplier<T> fallback) {
        if (provider == null) {
            return fallback.get();
        }
        T bean = provider.getIfAvailable();
        return bean != null ? bean : fallback.get();
    }

    public void tick(long nowMs) {
        timeService.tick(nowMs);
        resourceService.tick(nowMs);
    }

    public HostWorldContext bindHostWorld(HostWorldContext ctx) {
        hostWorlds.put(ctx.hostPlayerId(), ctx);
        return ctx;
    }

    public HostWorldContext hostWorldOf(long hostPlayerId) {
        return hostWorlds.get(hostPlayerId);
    }

    public HostWorldContext clearHostWorld(long hostPlayerId) {
        return hostWorlds.remove(hostPlayerId);
    }

    /**
     * 解谜位翻转（压力板 / 方碑等）；scope 按房主世界隔离。
     */
    public Map<String, Object> setPuzzleBit(int worldId, long hostPlayerId, int bitIndex, boolean value) {
        String scope = WorldStateBitmap.scopeKey(worldId, hostPlayerId);
        boolean ok = bitmap.set(scope, bitIndex, value);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", ok);
        body.put("scope", scope);
        body.put("bitIndex", bitIndex);
        body.put("value", value);
        body.put("cardinality", bitmap.cardinality(scope));
        return body;
    }

    public boolean getPuzzleBit(int worldId, long hostPlayerId, int bitIndex) {
        return bitmap.get(WorldStateBitmap.scopeKey(worldId, hostPlayerId), bitIndex);
    }

    /**
     * 多步解谜：激活机关并可选写入 bitmap。
     */
    public Map<String, Object> advancePuzzle(
            String mechanismId, int worldId, long hostPlayerId,
            int bitIndex, boolean oneShot, long cooldownMs, long nowMs) {
        MechanismStateMachine.TransitionResult r =
                resourceService.activateMechanism(mechanismId, oneShot, cooldownMs, nowMs);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", r.ok());
        body.put("from", r.from().name());
        body.put("to", r.to().name());
        body.put("reason", r.reason());
        if (r.ok() && bitIndex >= 0) {
            body.putAll(setPuzzleBit(worldId, hostPlayerId, bitIndex, true));
        }
        return body;
    }

    /**
     * 权威快照：客户端进场 / 重连 / 房主世界切换时一次性下发。
     */
    public Map<String, Object> snapshot(int worldId, long hostPlayerId) {
        long now = System.currentTimeMillis();
        String scope = WorldStateBitmap.scopeKey(worldId, hostPlayerId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("worldId", worldId);
        body.put("hostPlayerId", hostPlayerId);
        body.put("time", timeService.toView(now));
        body.put("resources", resourceService.snapshot(worldId));
        body.put("puzzleBitCardinality", bitmap.cardinality(scope));
        HostWorldContext host = hostPlayerId > 0 ? hostWorlds.get(hostPlayerId) : null;
        body.put("hostWorld", host == null ? Map.of() : host.toView());
        return body;
    }

    public WorldTimeService time() {
        return timeService;
    }

    public WorldResourceService resources() {
        return resourceService;
    }

    public WorldStateBitmap bitmap() {
        return bitmap;
    }

    public List<HostWorldContext> listHostWorlds() {
        return List.copyOf(hostWorlds.values());
    }
}
