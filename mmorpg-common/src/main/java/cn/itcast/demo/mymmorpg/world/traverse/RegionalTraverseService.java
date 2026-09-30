package cn.itcast.demo.mymmorpg.world.traverse;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 区域特色移动：滑索、气流牵引、高速载具等，降低跑图门槛。
 */
@Service
public class RegionalTraverseService {

    public enum FacilityKind {
        ZIPLINE, WIND_CURRENT, SPEED_VEHICLE, STATUE_SLIDE
    }

    public record FacilityDef(
            String facilityId,
            String regionId,
            FacilityKind kind,
            float startX, float startY, float startZ,
            float endX, float endY, float endZ,
            float entryRadius,
            float speedMul,
            TraverseModeService.Mode requiredMode) {

        public FacilityDef {
            entryRadius = entryRadius <= 0f ? 6f : entryRadius;
            speedMul = speedMul <= 0f ? 1.5f : speedMul;
        }
    }

    private final TraverseModeService traverse;
    private final ConcurrentHashMap<String, FacilityDef> facilities = new ConcurrentHashMap<>();

    public RegionalTraverseService(TraverseModeService traverse) {
        this.traverse = traverse == null ? new TraverseModeService() : traverse;
    }

    public RegionalTraverseService() {
        this(new TraverseModeService());
    }

    public void register(FacilityDef def) {
        if (def != null && def.facilityId() != null) {
            facilities.put(def.facilityId(), def);
        }
    }

    public Map<String, Object> use(
            long playerId, String facilityId, float px, float py, float pz, long nowMs) {
        FacilityDef def = facilities.get(facilityId);
        if (def == null) {
            return Map.of("ok", false, "error", "facility_not_found");
        }
        if (def.requiredMode() != null && !traverse.has(playerId, def.requiredMode())) {
            return Map.of("ok", false, "error", "mode_required", "mode", def.requiredMode().name());
        }
        float dx = px - def.startX();
        float dy = py - def.startY();
        float dz = pz - def.startZ();
        if (Math.sqrt(dx * dx + dy * dy + dz * dz) > def.entryRadius()) {
            return Map.of("ok", false, "error", "out_of_entry_range");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("facilityId", facilityId);
        body.put("kind", def.kind().name());
        body.put("regionId", def.regionId());
        body.put("speedMul", def.speedMul());
        body.put("from", Map.of("x", def.startX(), "y", def.startY(), "z", def.startZ()));
        body.put("to", Map.of("x", def.endX(), "y", def.endY(), "z", def.endZ()));
        body.put("staminaCost", staminaCost(def.kind()));
        body.put("clientAnim", clientAnim(def.kind()));
        body.put("atMs", nowMs);
        return body;
    }

    public Map<String, Object> listRegion(String regionId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("regionId", regionId);
        body.put("facilities", facilities.values().stream()
                .filter(f -> f.regionId().equals(regionId))
                .map(f -> Map.of(
                        "facilityId", f.facilityId(),
                        "kind", f.kind().name(),
                        "start", Map.of("x", f.startX(), "y", f.startY(), "z", f.startZ()),
                        "end", Map.of("x", f.endX(), "y", f.endY(), "z", f.endZ())))
                .toList());
        return body;
    }

    private static float staminaCost(FacilityKind kind) {
        return switch (kind) {
            case ZIPLINE, STATUE_SLIDE -> 0f;
            case WIND_CURRENT -> 5f;
            case SPEED_VEHICLE -> 8f;
        };
    }

    private static String clientAnim(FacilityKind kind) {
        return switch (kind) {
            case ZIPLINE -> "ANIM_ZIPLINE_GLIDE";
            case WIND_CURRENT -> "ANIM_WIND_LIFT";
            case SPEED_VEHICLE -> "ANIM_VEHICLE_BOOST";
            case STATUE_SLIDE -> "ANIM_STATUE_SLIDE";
        };
    }
}
