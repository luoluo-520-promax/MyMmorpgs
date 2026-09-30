package cn.itcast.demo.mymmorpg.world.traverse;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家已解锁的探索移动手段（钩锁/滑翔/攀爬/游泳/载具）。
 */
@Service
public class TraverseModeService {

    public enum Mode {
        HOOK, GLIDE, CLIMB, SWIM, VEHICLE, WIND_FIELD, GRAPPLE, RIDE, AIR_DASH, WALL_RUN
    }

    private final ConcurrentHashMap<Long, Set<Mode>> unlocked = new ConcurrentHashMap<>();

    public Map<String, Object> unlock(long playerId, Mode mode) {
        if (playerId <= 0 || mode == null) {
            return Map.of("ok", false, "error", "invalid_args");
        }
        unlocked.computeIfAbsent(playerId, id -> ConcurrentHashMap.newKeySet()).add(mode);
        return snapshot(playerId);
    }

    public boolean has(long playerId, Mode mode) {
        return unlocked.getOrDefault(playerId, Set.of()).contains(mode);
    }

    public Set<String> modeNames(long playerId) {
        return unlocked.getOrDefault(playerId, Set.of()).stream().map(Enum::name)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public Map<String, Object> snapshot(long playerId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("modes", modeNames(playerId));
        return body;
    }
}
