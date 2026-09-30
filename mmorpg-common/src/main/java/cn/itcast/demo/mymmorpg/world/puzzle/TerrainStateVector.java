package cn.itcast.demo.mymmorpg.world.puzzle;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.StampedLock;

/**
 * 地形状态向量（TSV）：每个可交互格子维护全局自增 stateRevision，保障多端强一致。
 * 读写分离：移动校验走 StampedLock 乐观读；地形写操作延迟 200ms 最终生效，避免阻塞主逻辑帧。
 */
@Service
public class TerrainStateVector {

    public static final long TERRAIN_WRITE_DELAY_MS = 200L;

    public record CellState(
            String cellKey,
            String regionId,
            int gx,
            int gz,
            String mark,
            long revision,
            long updatedAtMs) {
    }

    private final AtomicLong globalRevision = new AtomicLong(0);
    private final ConcurrentHashMap<String, CellState> cells = new ConcurrentHashMap<>();
    /** 区域级已毁静态网格 UID（联机快照用） */
    private final ConcurrentHashMap<String, List<String>> destroyedMeshesByRegion = new ConcurrentHashMap<>();
    /** 地形写操作延迟队列：先下发视觉，200ms 后修正 Revision */
    private final ConcurrentHashMap<String, PendingTerrainWrite> pendingWrites = new ConcurrentHashMap<>();
    private final StampedLock revisionLock = new StampedLock();

    public record PendingTerrainWrite(
            String regionId, int gx, int gz, String mark,
            long scheduledAtMs, long clientRevision) {
    }

    private static String cellKey(String regionId, int gx, int gz) {
        return (regionId == null ? "" : regionId.trim()) + ":" + gx + ":" + gz;
    }

    public long bump(String regionId, int gx, int gz, String mark, long nowMs) {
        return bumpImmediate(regionId, gx, gz, mark, nowMs);
    }

    /**
     * 延迟生效模式：立即返回预期 revision 供客户端视觉碎岩，200ms 后写入。
     */
    public Map<String, Object> scheduleBump(
            String regionId, int gx, int gz, String mark, long nowMs) {
        String key = cellKey(regionId, gx, gz);
        long expectedRev = globalRevision.get() + 1;
        pendingWrites.put(key, new PendingTerrainWrite(
                regionId == null ? "" : regionId.trim(), gx, gz,
                mark == null ? "PLAIN" : mark, nowMs + TERRAIN_WRITE_DELAY_MS, expectedRev));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("cellKey", key);
        body.put("visualImmediate", true);
        body.put("expectedRevision", expectedRev);
        body.put("commitAtMs", nowMs + TERRAIN_WRITE_DELAY_MS);
        body.put("smoothInterpolateOnConflict", true);
        return body;
    }

    /**
     * 刷入到期地形写操作。
     */
    public int flushPendingWrites(long nowMs) {
        int n = 0;
        for (Map.Entry<String, PendingTerrainWrite> e : new ArrayList<>(pendingWrites.entrySet())) {
            PendingTerrainWrite pw = e.getValue();
            if (pw.scheduledAtMs() <= nowMs) {
                bumpImmediate(pw.regionId(), pw.gx(), pw.gz(), pw.mark(), nowMs);
                pendingWrites.remove(e.getKey());
                n++;
            }
        }
        return n;
    }

    private long bumpImmediate(String regionId, int gx, int gz, String mark, long nowMs) {
        String key = cellKey(regionId, gx, gz);
        long stamp = revisionLock.writeLock();
        try {
            long rev = globalRevision.incrementAndGet();
            cells.put(key, new CellState(key, regionId == null ? "" : regionId.trim(), gx, gz,
                    mark == null ? "PLAIN" : mark, rev, nowMs));
            return rev;
        } finally {
            revisionLock.unlockWrite(stamp);
        }
    }

    public long revisionOf(String regionId, int gx, int gz) {
        long stamp = revisionLock.tryOptimisticRead();
        CellState s = cells.get(cellKey(regionId, gx, gz));
        long rev = s == null ? 0L : s.revision();
        if (!revisionLock.validate(stamp)) {
            stamp = revisionLock.readLock();
            try {
                s = cells.get(cellKey(regionId, gx, gz));
                rev = s == null ? 0L : s.revision();
            } finally {
                revisionLock.unlockRead(stamp);
            }
        }
        return rev;
    }

    public CellState get(String regionId, int gx, int gz) {
        return cells.get(cellKey(regionId, gx, gz));
    }

    /**
     * Zone 内全部激活突变的全量同步（访客进房 / 靠近范围时下发）。
     */
    public Map<String, Object> snapshotZone(String regionId) {
        String prefix = (regionId == null ? "" : regionId.trim()) + ":";
        List<Map<String, Object>> list = new ArrayList<>();
        long maxRev = 0L;
        for (CellState s : cells.values()) {
            if (s.cellKey().startsWith(prefix) || s.regionId().equals(regionId == null ? "" : regionId.trim())) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("cellKey", s.cellKey());
                row.put("gx", s.gx());
                row.put("gz", s.gz());
                row.put("mark", s.mark());
                row.put("stateRevision", s.revision());
                row.put("updatedAtMs", s.updatedAtMs());
                list.add(row);
                maxRev = Math.max(maxRev, s.revision());
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("regionId", regionId == null ? "" : regionId.trim());
        body.put("mutations", list);
        body.put("zoneMaxRevision", maxRev);
        body.put("globalRevision", globalRevision.get());
        body.put("destroyedStaticMeshUids", destroyedMeshUids(regionId));
        return body;
    }

    public List<String> destroyedMeshUids(String regionId) {
        String rid = regionId == null ? "" : regionId.trim();
        return List.copyOf(destroyedMeshesByRegion.getOrDefault(rid, List.of()));
    }

    public long markMeshDestroyed(String regionId, String meshUid, long nowMs) {
        String rid = regionId == null ? "" : regionId.trim();
        String uid = meshUid == null ? "" : meshUid.trim();
        if (uid.isBlank()) {
            return globalRevision.get();
        }
        destroyedMeshesByRegion.compute(rid, (k, list) -> {
            List<String> copy = list == null ? new ArrayList<>() : new ArrayList<>(list);
            if (!copy.contains(uid)) {
                copy.add(uid);
            }
            return copy;
        });
        int gx = Math.abs(uid.hashCode()) % 256;
        int gz = Math.abs(uid.hashCode() / 256) % 256;
        return bump(rid, gx, gz, "MESH_DESTROYED", nowMs);
    }

    public void setDestroyedMeshes(String regionId, List<String> uids) {
        String rid = regionId == null ? "" : regionId.trim();
        destroyedMeshesByRegion.put(rid, uids == null ? List.of() : List.copyOf(uids));
    }

    /**
     * 移动校验：乐观读锁处理 revision 对比，不阻塞异步物理审计写锁。
     */
    public Map<String, Object> validateMoveRevision(
            String regionId, int gx, int gz, long clientRevision) {
        long serverRev = revisionOf(regionId, gx, gz);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cellKey", cellKey(regionId, gx, gz));
        body.put("clientRevision", clientRevision);
        body.put("serverRevision", serverRev);
        body.put("optimisticRead", true);
        if (clientRevision < serverRev) {
            body.put("ok", false);
            body.put("mismatch", true);
            body.put("error", "TERRAIN_STATE_MISMATCH");
            body.put("forcePullTsv", true);
            body.put("softCorrect", true);
            body.put("smoothInterpolate", true);
            CellState s = get(regionId, gx, gz);
            if (s != null) {
                body.put("mark", s.mark());
            }
        } else {
            body.put("ok", true);
            body.put("mismatch", false);
        }
        return body;
    }

    public long globalRevision() {
        return globalRevision.get();
    }

    public void clearRegion(String regionId) {
        String rid = regionId == null ? "" : regionId.trim();
        cells.entrySet().removeIf(e -> e.getValue().regionId().equals(rid));
        destroyedMeshesByRegion.remove(rid);
    }
}
