package cn.itcast.demo.mymmorpg.world.traverse;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 载具同步：路径插值 + 关键帧；VehicleSyncCmd 每 200ms 纠偏（比步行更宽松）。
 */
@Service
public class VehicleSyncService {

    public static final long CORRECT_INTERVAL_MS = 200L;

    public record VehicleDef(
            String vehicleId,
            String kind,
            float maxSpeed,
            float accel,
            float fuelCap,
            float fuelPerSec,
            int durabilityCap) {
        public VehicleDef {
            vehicleId = vehicleId == null ? "" : vehicleId.trim();
            kind = kind == null ? "LAND" : kind.trim().toUpperCase();
            maxSpeed = maxSpeed <= 0f ? 18f : maxSpeed;
            accel = accel <= 0f ? 6f : accel;
            fuelCap = fuelCap <= 0f ? 100f : fuelCap;
            fuelPerSec = fuelPerSec <= 0f ? 2f : fuelPerSec;
            durabilityCap = durabilityCap <= 0 ? 100 : durabilityCap;
        }
    }

    public record VehicleState(
            String vehicleId,
            long riderId,
            float x, float y, float z,
            float yawDeg,
            float speed,
            float accel,
            float fuel,
            int durability,
            long lastCorrectMs) {
    }

    private final ConcurrentHashMap<String, VehicleDef> defs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, VehicleState> states = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> riderToVehicle = new ConcurrentHashMap<>();

    public void register(VehicleDef def) {
        if (def != null && !def.vehicleId().isBlank()) {
            defs.put(def.vehicleId(), def);
        }
    }

    public Map<String, Object> board(long playerId, String vehicleId, float x, float y, float z, long nowMs) {
        VehicleDef def = defs.get(vehicleId);
        if (def == null) {
            return Map.of("ok", false, "error", "vehicle_not_found");
        }
        if (riderToVehicle.containsKey(playerId)) {
            return Map.of("ok", false, "error", "already_riding");
        }
        VehicleState st = new VehicleState(
                vehicleId, playerId, x, y, z, 0f, 0f, def.accel(),
                def.fuelCap(), def.durabilityCap(), nowMs);
        states.put(vehicleId, st);
        riderToVehicle.put(playerId, vehicleId);
        return syncCmd(st, def, true);
    }

    /**
     * 客户端上报关键帧：服务端按 200ms 纠偏并扣燃油。
     */
    public Map<String, Object> sync(
            long playerId, float x, float y, float z,
            float yawDeg, float speed, float accel, long nowMs) {
        String vid = riderToVehicle.get(playerId);
        if (vid == null) {
            return Map.of("ok", false, "error", "not_riding");
        }
        VehicleDef def = defs.get(vid);
        VehicleState prev = states.get(vid);
        if (def == null || prev == null) {
            return Map.of("ok", false, "error", "vehicle_missing");
        }
        float dt = Math.max(0.05f, (nowMs - prev.lastCorrectMs()) / 1000f);
        float fuelCost = def.fuelPerSec() * dt;
        float fuel = Math.max(0f, prev.fuel() - fuelCost);
        if (fuel <= 0f) {
            return Map.of("ok", false, "error", "fuel_empty", "vehicleId", vid);
        }
        float cappedSpeed = Math.min(def.maxSpeed(), Math.max(0f, speed));
        boolean needCorrect = nowMs - prev.lastCorrectMs() >= CORRECT_INTERVAL_MS;
        float authX = x;
        float authY = y;
        float authZ = z;
        if (needCorrect) {
            float dx = x - prev.x();
            float dy = y - prev.y();
            float dz = z - prev.z();
            double moved = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double maxMove = def.maxSpeed() * dt * 1.35; // 比步行更宽松
            if (moved > maxMove) {
                double scale = maxMove / moved;
                authX = prev.x() + (float) (dx * scale);
                authY = prev.y() + (float) (dy * scale);
                authZ = prev.z() + (float) (dz * scale);
            }
        } else {
            authX = prev.x();
            authY = prev.y();
            authZ = prev.z();
        }
        VehicleState next = new VehicleState(
                vid, playerId, authX, authY, authZ, yawDeg, cappedSpeed,
                Math.min(def.accel(), Math.max(0f, accel)), fuel, prev.durability(),
                needCorrect ? nowMs : prev.lastCorrectMs());
        states.put(vid, next);
        Map<String, Object> body = syncCmd(next, def, false);
        body.put("corrected", needCorrect);
        body.put("fuelConsumed", Math.round(fuelCost * 100f) / 100f);
        return body;
    }

    public Map<String, Object> dismount(long playerId) {
        String vid = riderToVehicle.remove(playerId);
        if (vid == null) {
            return Map.of("ok", false, "error", "not_riding");
        }
        VehicleState st = states.get(vid);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("vehicleId", vid);
        if (st != null) {
            body.put("x", st.x());
            body.put("y", st.y());
            body.put("z", st.z());
        }
        return body;
    }

    private Map<String, Object> syncCmd(VehicleState st, VehicleDef def, boolean boarded) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("cmd", "VehicleSyncCmd");
        body.put("vehicleId", st.vehicleId());
        body.put("kind", def.kind());
        body.put("riderId", st.riderId());
        body.put("x", st.x());
        body.put("y", st.y());
        body.put("z", st.z());
        body.put("yawDeg", st.yawDeg());
        body.put("speed", st.speed());
        body.put("accel", st.accel());
        body.put("fuel", st.fuel());
        body.put("durability", st.durability());
        body.put("correctIntervalMs", CORRECT_INTERVAL_MS);
        body.put("boarded", boarded);
        return body;
    }
}
