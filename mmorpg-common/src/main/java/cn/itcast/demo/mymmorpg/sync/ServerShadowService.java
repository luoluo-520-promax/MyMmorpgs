package cn.itcast.demo.mymmorpg.sync;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务器影子状态：为每位玩家维护约 500ms 位置快照，供延迟补偿与软回滚判定；
 * 断线超保护期时启用时间回溯仲裁 + 一次性快进和解。
 */
@Service
public class ServerShadowService {

    public static final long SHADOW_WINDOW_MS = 500L;
    public static final long HEARTBEAT_INTERVAL_MS = 50L;
    public static final long RECONNECT_PROTECT_MS = 30_000L;

    public record PredictedActionFrame(
            String actionId, String action, float x, float y, float z, float speed, long clientTs) {
    }

    public record ShadowRecoveryReport(
            long playerId,
            long disconnectDurationMs,
            boolean kicked,
            boolean fastForwardApplied,
            int framesReconciled,
            String note) {
    }

    private final ConcurrentHashMap<Long, SnapshotBuffer> shadows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> disconnectAt = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> logicFrameSeq = new ConcurrentHashMap<>();

    /** 每 50ms 下发服务器逻辑帧序号，客户端据此调整插值。 */
    public Map<String, Object> heartbeat(long playerId, long nowMs) {
        long seq = logicFrameSeq.merge(playerId, 1L, Long::sum);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("logicFrameSeq", seq);
        body.put("heartbeatIntervalMs", HEARTBEAT_INTERVAL_MS);
        body.put("serverTimeMs", nowMs);
        body.put("shadowWindowMs", SHADOW_WINDOW_MS);
        return body;
    }

    public void recordPosition(long playerId, long timestampMs, float x, float y, float z, float speed) {
        shadows.computeIfAbsent(playerId, id -> SnapshotBuffer.forLagCompensation())
                .push(timestampMs, x, y, z, speed);
    }

    public SnapshotBuffer.PosSnapshot rewind(long playerId, long clientTimestampMs) {
        SnapshotBuffer buf = shadows.get(playerId);
        return buf == null ? null : buf.rewindTo(clientTimestampMs);
    }

    public void markDisconnect(long playerId, long nowMs) {
        disconnectAt.put(playerId, nowMs);
    }

    /**
     * 软回滚策略：校验失败时仅回滚数值结算，保留位移/动画表现。
     */
    public Map<String, Object> softRollbackDecision(
            long playerId, long clientTimestampMs,
            float predictedX, float predictedY, float predictedZ,
            float maxSpeed, boolean hitValid, boolean cooldownValid) {
        SnapshotBuffer buf = shadows.get(playerId);
        boolean positionOk = buf == null || buf.validateWithLagCompensation(
                clientTimestampMs, predictedX, predictedY, predictedZ, maxSpeed, 2.5f);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("positionAccepted", positionOk);
        body.put("keepVisuals", true);
        body.put("rollbackDamage", !hitValid);
        body.put("rollbackCooldown", !cooldownValid);
        body.put("rubberBand", false);
        if (!positionOk) {
            body.put("note", "position_out_of_bounds_but_no_snap");
        } else if (!hitValid || !cooldownValid) {
            body.put("note", "soft_rollback_numeric_only");
        } else {
            body.put("note", "full_confirm");
        }
        return body;
    }

    /**
     * 断线重连：超过 30s 保护期不直接踢下线，而是对客户端缓存的最后 500ms 操作做一次性快进计算。
     */
    public Map<String, Object> recoverWithFastForward(
            long playerId, long nowMs, List<PredictedActionFrame> clientQueue) {
        Long disc = disconnectAt.remove(playerId);
        long duration = disc == null ? 0L : Math.max(0L, nowMs - disc);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("disconnectDurationMs", duration);
        body.put("protectWindowMs", RECONNECT_PROTECT_MS);

        if (duration <= RECONNECT_PROTECT_MS) {
            body.put("kicked", false);
            body.put("mode", "STATIC_RESUME");
            body.put("fastForwardApplied", false);
            body.put("report", new ShadowRecoveryReport(
                    playerId, duration, false, false, 0, "within_protect_static_resume"));
            return body;
        }

        List<PredictedActionFrame> queue = clientQueue == null ? List.of() : clientQueue;
        // 仅取最近 SHADOW_WINDOW_MS 内的帧
        List<PredictedActionFrame> window = new ArrayList<>();
        for (PredictedActionFrame f : queue) {
            if (f != null && nowMs - f.clientTs() <= SHADOW_WINDOW_MS) {
                window.add(f);
            }
        }
        for (PredictedActionFrame f : window) {
            recordPosition(playerId, f.clientTs(), f.x(), f.y(), f.z(), f.speed());
        }
        body.put("kicked", false);
        body.put("mode", "FAST_FORWARD_RECONCILE");
        body.put("fastForwardApplied", true);
        body.put("framesReconciled", window.size());
        body.put("note", "time_rewind_arbitration");
        body.put("report", Map.of(
                "playerId", playerId,
                "disconnectDurationMs", duration,
                "kicked", false,
                "fastForwardApplied", true,
                "framesReconciled", window.size(),
                "note", "fast_forward_reconciliation"));
        return body;
    }

    public void clearPlayer(long playerId) {
        shadows.remove(playerId);
        disconnectAt.remove(playerId);
    }

    // -------- 联机房间快照（主机迁移） --------

    public static final long ROOM_SNAPSHOT_TTL_MS = 5 * 60_000L;

    public record RoomSnapshot(
            String roomId,
            long hostPlayerId,
            long bossHpRemain,
            long bossHpMax,
            String gadgetBitmap,
            String tideState,
            long savedAtMs,
            byte[] protobufPayload) {
    }

    private final ConcurrentHashMap<String, RoomSnapshot> roomSnapshots = new ConcurrentHashMap<>();

    /**
     * 序列化房间战斗态（Boss 血量 / 机关 Bitmap / 潮汐）为轻量快照，TTL 5min。
     */
    public Map<String, Object> saveRoomSnapshot(
            String roomId, long hostPlayerId, long bossHpRemain, long bossHpMax,
            String gadgetBitmap, String tideState, long nowMs) {
        String rid = roomId == null ? "" : roomId.trim();
        // 轻量 Protobuf 占位：UTF-8 拼接关键字段，生产可替换为正式 proto
        String wire = rid + "|" + hostPlayerId + "|" + bossHpRemain + "|" + bossHpMax
                + "|" + (gadgetBitmap == null ? "" : gadgetBitmap)
                + "|" + (tideState == null ? "" : tideState) + "|" + nowMs;
        byte[] payload = wire.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        RoomSnapshot snap = new RoomSnapshot(
                rid, hostPlayerId, bossHpRemain, Math.max(1, bossHpMax),
                gadgetBitmap == null ? "" : gadgetBitmap,
                tideState == null ? "IDLE" : tideState,
                nowMs, payload);
        roomSnapshots.put(rid, snap);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("roomId", rid);
        body.put("ttlMs", ROOM_SNAPSHOT_TTL_MS);
        body.put("payloadBytes", payload.length);
        body.put("bossHpRemain", bossHpRemain);
        body.put("gadgetBitmap", snap.gadgetBitmap());
        body.put("tideState", snap.tideState());
        body.put("redisKey", "coop:room_snapshot:" + rid);
        return body;
    }

    public Map<String, Object> loadRoomSnapshot(String roomId) {
        String rid = roomId == null ? "" : roomId.trim();
        RoomSnapshot snap = roomSnapshots.get(rid);
        Map<String, Object> body = new LinkedHashMap<>();
        if (snap == null) {
            body.put("ok", false);
            body.put("error", "snapshot_not_found");
            body.put("roomId", rid);
            return body;
        }
        long age = System.currentTimeMillis() - snap.savedAtMs();
        if (age > ROOM_SNAPSHOT_TTL_MS) {
            roomSnapshots.remove(rid);
            body.put("ok", false);
            body.put("error", "snapshot_expired");
            body.put("ageMs", age);
            return body;
        }
        body.put("ok", true);
        body.put("roomId", rid);
        body.put("hostPlayerId", snap.hostPlayerId());
        body.put("bossHpRemain", snap.bossHpRemain());
        body.put("bossHpMax", snap.bossHpMax());
        body.put("gadgetBitmap", snap.gadgetBitmap());
        body.put("tideState", snap.tideState());
        body.put("savedAtMs", snap.savedAtMs());
        body.put("ageMs", age);
        body.put("payloadBytes", snap.protobufPayload().length);
        body.put("reloadScene", false);
        return body;
    }

    public void clearRoomSnapshot(String roomId) {
        roomSnapshots.remove(roomId == null ? "" : roomId.trim());
    }
}
