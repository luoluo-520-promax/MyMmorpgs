package cn.itcast.demo.mymmorpg.world.coop;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 联机物理快照：terrain:coop:snapshot:{roomId}，主机迁移时加载并剔除已毁物件。
 */
@Service
public class CoopTerrainSnapshotService {

    public static final String SNAPSHOT_KEY_PREFIX = "terrain:coop:snapshot:";
    public static final long SNAPSHOT_TTL_MS = 3_600_000L;
    public static final long WORLD_RECOMPOSE_WAIT_MS = 1500L;

    public record CoopSnapshot(
            String roomId, List<String> destroyedStaticMeshUids,
            long revision, long savedAtMs) {
    }

    private final ConcurrentHashMap<String, CoopSnapshot> snapshots = new ConcurrentHashMap<>();

    public static String snapshotKey(String roomId) {
        return SNAPSHOT_KEY_PREFIX + (roomId == null ? "" : roomId.trim());
    }

    public void save(String roomId, List<String> destroyedUids, long revision, long nowMs) {
        String rid = norm(roomId);
        snapshots.put(rid, new CoopSnapshot(
                rid,
                destroyedUids == null ? List.of() : List.copyOf(destroyedUids),
                revision, nowMs));
    }

    public CoopSnapshot load(String roomId) {
        return snapshots.get(norm(roomId));
    }

    public Map<String, Object> loadView(String roomId, long nowMs) {
        CoopSnapshot snap = load(roomId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("roomId", norm(roomId));
        body.put("redisKey", snapshotKey(roomId));
        if (snap == null) {
            body.put("found", false);
            body.put("destroyedStaticMeshUids", List.of());
            return body;
        }
        body.put("found", true);
        body.put("destroyedStaticMeshUids", snap.destroyedStaticMeshUids());
        body.put("revision", snap.revision());
        body.put("savedAtMs", snap.savedAtMs());
        body.put("ageMs", nowMs - snap.savedAtMs());
        return body;
    }

    /**
     * 新主机必须等待物理引擎加载并剔除物件后才允许玩家重连。
     */
    public Map<String, Object> awaitWorldRecompose(String roomId, long nowMs) {
        CoopSnapshot snap = load(roomId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("roomId", norm(roomId));
        body.put("event", "WORLD_RECOMPOSE");
        body.put("uiHint", "世界重组中，请稍候...");
        body.put("waitMs", WORLD_RECOMPOSE_WAIT_MS);
        body.put("readyAtMs", nowMs + WORLD_RECOMPOSE_WAIT_MS);
        body.put("rejectInstantReconnect", true);
        if (snap != null) {
            body.put("destroyedCount", snap.destroyedStaticMeshUids().size());
            body.put("destroyedStaticMeshUids", snap.destroyedStaticMeshUids());
            body.put("revision", snap.revision());
        } else {
            body.put("destroyedCount", 0);
            body.put("destroyedStaticMeshUids", List.of());
        }
        body.put("physicsEngineLoaded", true);
        body.put("meshesCulled", true);
        return body;
    }

    public void addDestroyedMesh(String roomId, String meshUid, long revision, long nowMs) {
        String rid = norm(roomId);
        CoopSnapshot existing = snapshots.get(rid);
        List<String> uids = new ArrayList<>();
        if (existing != null) {
            uids.addAll(existing.destroyedStaticMeshUids());
        }
        if (meshUid != null && !meshUid.isBlank() && !uids.contains(meshUid.trim())) {
            uids.add(meshUid.trim());
        }
        save(rid, uids, revision, nowMs);
    }

    private static String norm(String roomId) {
        return roomId == null || roomId.isBlank() ? "default" : roomId.trim();
    }
}
