package cn.itcast.demo.mymmorpg.world.feel;

import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.sync.SceneMoveCmd;
import cn.itcast.demo.mymmorpg.sync.SurfaceType;
import cn.itcast.demo.mymmorpg.world.puzzle.TerrainStateVector;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * P19 微观物理反馈：植被压弯、动态脚印、投射物留存。
 */
@Service
public class PhysicalDetailService {

    public static final long BEND_COOLDOWN_MS = 500L;

    private final TerrainDetailTracker tracker;
    private final ConcurrentHashMap<Long, Long> lastBendMs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> lastFootprintMs = new ConcurrentHashMap<>();
    private TerrainStateVector terrainStateVector;

    public PhysicalDetailService() {
        this(new TerrainDetailTracker());
    }

    public PhysicalDetailService(TerrainDetailTracker tracker) {
        this.tracker = tracker == null ? new TerrainDetailTracker() : tracker;
    }

    public void bindTerrainStateVector(TerrainStateVector tsv) {
        this.terrainStateVector = tsv;
    }

    public TerrainDetailTracker tracker() {
        return tracker;
    }

    /**
     * 玩家移动：广播 BEND_VEGETATION + 可选 FOOTPRINT_SPAWN。
     */
    public Map<String, Object> onPlayerMove(
            long playerId, SceneMoveCmd cmd, String zone,
            float fromX, float fromY, float fromZ, long nowMs) {
        if (cmd == null) {
            return Map.of("ok", false, "error", "cmd_required");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("zone", zone == null ? "default" : zone.trim());

        float dirX = cmd.targetX() - fromX;
        float dirZ = cmd.targetZ() - fromZ;
        double len = Math.hypot(dirX, dirZ);
        if (len > 0.001) {
            dirX = (float) (dirX / len);
            dirZ = (float) (dirZ / len);
        }

        long lastBend = lastBendMs.getOrDefault(playerId, 0L);
        if (nowMs - lastBend >= BEND_COOLDOWN_MS) {
            lastBendMs.put(playerId, nowMs);
            Map<String, Object> bend = new LinkedHashMap<>();
            bend.put("event", "BEND_VEGETATION");
            bend.put("msgId", MessageId.BEND_VEGETATION_SC_NOTIFY);
            bend.put("x", cmd.targetX());
            bend.put("y", cmd.targetY());
            bend.put("z", cmd.targetZ());
            bend.put("dirX", dirX);
            bend.put("dirZ", dirZ);
            bend.put("radiusM", 1.2f);
            bend.put("clientVertexShaderOffset", true);
            bend.put("serverCooldownMs", BEND_COOLDOWN_MS);
            body.put("bendVegetation", bend);
            body.put("aoiBroadcast", bend);
        } else {
            body.put("bendSkipped", true);
            body.put("bendCooldownRemainMs", BEND_COOLDOWN_MS - (nowMs - lastBend));
        }

        SurfaceType surface = cmd.surfaceType();
        if (surface == SurfaceType.GRASS || surface == SurfaceType.SNOW || surface == SurfaceType.MUD) {
            long lastFp = lastFootprintMs.getOrDefault(playerId, 0L);
            if (nowMs - lastFp >= 200L) {
                lastFootprintMs.put(playerId, nowMs);
                float yaw = (float) Math.toDegrees(Math.atan2(dirX, dirZ));
                TerrainDetailTracker.FootprintEntry fp = tracker.addFootprint(
                        zone, playerId, cmd.targetX(), cmd.targetY(), cmd.targetZ(),
                        yaw, surface.name(), nowMs);
                long revision = 0L;
                if (terrainStateVector != null && zone != null) {
                    int gx = (int) Math.floor(cmd.targetX() / 4f);
                    int gz = (int) Math.floor(cmd.targetZ() / 4f);
                    revision = terrainStateVector.bump(zone, gx, gz, "FOOTPRINT", nowMs);
                }
                Map<String, Object> footprint = new LinkedHashMap<>();
                footprint.put("event", "FOOTPRINT_SPAWN");
                footprint.put("msgId", MessageId.FOOTPRINT_SPAWN_SC_NOTIFY);
                footprint.put("footprintId", fp.id());
                footprint.put("x", fp.x());
                footprint.put("y", fp.y());
                footprint.put("z", fp.z());
                footprint.put("yaw", fp.yaw());
                footprint.put("surfaceType", surface.name());
                footprint.put("stateRevision", revision);
                footprint.put("ttlMs", TerrainDetailTracker.FOOTPRINT_TTL_MS);
                body.put("footprintSpawn", footprint);
                if (!body.containsKey("aoiBroadcast")) {
                    body.put("aoiBroadcast", footprint);
                }
            }
        }
        return body;
    }

    /**
     * 投射物命中：写入 debris 残留，不立刻销毁。
     */
    public Map<String, Object> onProjectileHit(
            long ownerId, String zone, String debrisType,
            float x, float y, float z,
            float nx, float ny, float nz, float yaw, long nowMs) {
        TerrainDetailTracker.DebrisEntry entry = tracker.addDebris(
                zone, debrisType, ownerId, x, y, z, nx, ny, nz, yaw, nowMs);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("event", "DEBRIS_SPAWN");
        body.put("msgId", MessageId.DEBRIS_SPAWN_SC_NOTIFY);
        body.put("debrisId", entry.id());
        body.put("debrisType", entry.debrisType());
        body.put("x", entry.x());
        body.put("y", entry.y());
        body.put("z", entry.z());
        body.put("normalX", entry.normalX());
        body.put("normalY", entry.normalY());
        body.put("normalZ", entry.normalZ());
        body.put("yaw", entry.yaw());
        body.put("ttlMs", TerrainDetailTracker.DEBRIS_TTL_MS);
        body.put("persist", true);
        body.put("aoiBroadcast", Map.copyOf(body));
        return body;
    }

    /** 玩家进入 AOI 时同步 zone 内全部微观细节。 */
    public Map<String, Object> syncAoiEnter(String zone, long nowMs) {
        return tracker.snapshotZone(zone, nowMs);
    }
}
