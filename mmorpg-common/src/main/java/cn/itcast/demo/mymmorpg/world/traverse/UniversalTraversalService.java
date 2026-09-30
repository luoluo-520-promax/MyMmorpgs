package cn.itcast.demo.mymmorpg.world.traverse;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 探索能力去角色绑定：核心移动手段账号级解锁，所有角色均可流畅探索。
 */
@Service
public class UniversalTraversalService {

    /** 账号级核心探索套件 */
    public static final Set<TraverseModeService.Mode> CORE_KIT = Set.of(
            TraverseModeService.Mode.CLIMB,
            TraverseModeService.Mode.GLIDE,
            TraverseModeService.Mode.HOOK,
            TraverseModeService.Mode.GRAPPLE,
            TraverseModeService.Mode.SWIM);

    private final TraverseModeService traverse;
    private final ConcurrentHashMap<Long, Set<TraverseModeService.Mode>> universalModes =
            new ConcurrentHashMap<>();

    public UniversalTraversalService(TraverseModeService traverse) {
        this.traverse = traverse == null ? new TraverseModeService() : traverse;
    }

    public UniversalTraversalService() {
        this(new TraverseModeService());
    }

    public Map<String, Object> grantUniversal(long playerId, TraverseModeService.Mode mode) {
        if (playerId <= 0 || mode == null) {
            return Map.of("ok", false, "error", "invalid_args");
        }
        universalModes.computeIfAbsent(playerId, id -> ConcurrentHashMap.newKeySet()).add(mode);
        traverse.unlock(playerId, mode);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("grantedMode", mode.name());
        body.put("characterBound", false);
        body.put("universalModes", snapshotModes(playerId));
        return body;
    }

    public Map<String, Object> ensureCoreExplorationKit(long playerId) {
        for (TraverseModeService.Mode mode : CORE_KIT) {
            grantUniversal(playerId, mode);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("coreKitGranted", true);
        body.put("universalModes", snapshotModes(playerId));
        body.put("note", "核心探索能力已账号级解锁，不绑定特定角色");
        return body;
    }

    public boolean isUniversal(long playerId, TraverseModeService.Mode mode) {
        return universalModes.getOrDefault(playerId, Set.of()).contains(mode);
    }

    public boolean canUseMode(long playerId, TraverseModeService.Mode mode) {
        return isUniversal(playerId, mode) || traverse.has(playerId, mode);
    }

    public Map<String, Object> snapshot(long playerId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("universalModes", snapshotModes(playerId));
        body.put("allTraverseModes", traverse.modeNames(playerId));
        body.put("coreKitComplete", snapshotModes(playerId).containsAll(
                CORE_KIT.stream().map(Enum::name).toList()));
        return body;
    }

    private java.util.List<String> snapshotModes(long playerId) {
        return universalModes.getOrDefault(playerId, Set.of()).stream()
                .map(Enum::name)
                .sorted()
                .toList();
    }
}
