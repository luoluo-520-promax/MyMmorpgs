package cn.itcast.demo.mymmorpg.world.puzzle;

import cn.itcast.demo.mymmorpg.physics.AsyncPhysicsThreadPool;
import cn.itcast.demo.mymmorpg.physics.LitePhysicsEngine;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * 物理层权威校验：对比客户端 PhysicsStateHash 与服务端预期推演，偏差超阈值则软拉回。
 * 校验间隔默认 500ms；主 Tick 不阻塞，审计走 AsyncPhysicsThreadPool。
 */
@Service
public class PhysicsAuthorityService {

    public static final long CHECK_INTERVAL_MS = 500L;
    public static final long GRAPPLE_AUDIT_DELAY_MS = 200L;
    public static final double GRAPPLE_DEVIATION_METERS = 3.0;
    public static final double GROUND_PULLBACK_METERS = 0.5;
    public static final double ICE_PULLBACK_METERS = 2.0;
    /** 速度矢量 / 重力系数相对偏差阈值 */
    public static final double DEVIATION_THRESHOLD = 0.18;

    private final AsyncPhysicsThreadPool asyncPool;
    private final LitePhysicsEngine litePhysics;

    public PhysicsAuthorityService() {
        this.asyncPool = new AsyncPhysicsThreadPool();
        this.litePhysics = new LitePhysicsEngine();
    }

    public PhysicsAuthorityService(
            ObjectProvider<AsyncPhysicsThreadPool> asyncPool,
            ObjectProvider<LitePhysicsEngine> litePhysics) {
        this.asyncPool = asyncPool != null
                ? asyncPool.getIfAvailable(AsyncPhysicsThreadPool::new)
                : new AsyncPhysicsThreadPool();
        this.litePhysics = litePhysics != null
                ? litePhysics.getIfAvailable(LitePhysicsEngine::new)
                : new LitePhysicsEngine();
    }

    public record GrappleAudit(
            long playerId, String auditToken,
            float predictedX, float predictedY, float predictedZ,
            float actualX, float actualY, float actualZ,
            long scheduledAtMs) {
    }

    private final ConcurrentHashMap<String, GrappleAudit> pendingGrappleAudit = new ConcurrentHashMap<>();

    public record ExpectedPhysics(
            float vx, float vy, float vz,
            float gravityScale,
            float contactNormalX, float contactNormalY, float contactNormalZ,
            long atMs) {
    }

    private final ConcurrentHashMap<Long, ExpectedPhysics> expected = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> lastCheckMs = new ConcurrentHashMap<>();

    public void recordExpected(long playerId, ExpectedPhysics state) {
        if (playerId > 0 && state != null) {
            expected.put(playerId, state);
        }
    }

    /**
     * 客户端上报物理哈希；服务端用预期参数重算 MD5 并对比。
     */
    public Map<String, Object> validateHash(
            long playerId, String clientHash,
            float clientVx, float clientVy, float clientVz,
            float clientGravity, float nx, float ny, float nz, long nowMs) {
        return validateHash(playerId, clientHash, clientVx, clientVy, clientVz,
                clientGravity, nx, ny, nz, nowMs, false);
    }

    public Map<String, Object> validateHash(
            long playerId, String clientHash,
            float clientVx, float clientVy, float clientVz,
            float clientGravity, float nx, float ny, float nz, long nowMs, boolean onIceSurface) {
        return validateHashInternal(playerId, clientHash, clientVx, clientVy, clientVz,
                clientGravity, nx, ny, nz, nowMs, onIceSurface, 0f, 0f, 0f);
    }

    /**
     * 异步校验：不阻塞主 Tick，结果通过 CompletableFuture 或 SOFT_ROLLBACK 消息下发。
     */
    public CompletableFuture<Map<String, Object>> validateHashAsyncFuture(
            long playerId, String clientHash,
            float clientVx, float clientVy, float clientVz,
            float clientGravity, float nx, float ny, float nz, boolean onIceSurface) {
        long nowMs = System.currentTimeMillis();
        return asyncPool.submit(() -> {
            Map<String, Object> result = validateHashInternal(
                    playerId, clientHash, clientVx, clientVy, clientVz,
                    clientGravity, nx, ny, nz, nowMs, onIceSurface, 0f, 0f, 0f);
            result.put("async", true);
            if (Boolean.TRUE.equals(result.get("softPullback"))) {
                asyncPool.onSoftRollback();
                result.put("note", "SOFT_ROLLBACK");
            }
            return result;
        });
    }

    public Map<String, Object> validateHashAsync(
            long playerId, String clientHash,
            float clientVx, float clientVy, float clientVz,
            float clientGravity, float nx, float ny, float nz, boolean onIceSurface) {
        try {
            return validateHashAsyncFuture(
                    playerId, clientHash, clientVx, clientVy, clientVz,
                    clientGravity, nx, ny, nz, onIceSurface).get();
        } catch (Exception e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("ok", false);
            err.put("error", e.getMessage());
            return err;
        }
    }

    private Map<String, Object> validateHashInternal(
            long playerId, String clientHash,
            float clientVx, float clientVy, float clientVz,
            float clientGravity, float nx, float ny, float nz, long nowMs, boolean onIceSurface,
            float posX, float posY, float posZ) {
        double pullbackThreshold = onIceSurface ? ICE_PULLBACK_METERS : GROUND_PULLBACK_METERS;
        ExpectedPhysics exp = expected.get(playerId);
        if (exp == null) {
            ExpectedPhysics seed = new ExpectedPhysics(
                    clientVx, clientVy, clientVz, clientGravity, nx, ny, nz, nowMs);
            expected.put(playerId, seed);
            lastCheckMs.put(playerId, nowMs);
            Map<String, Object> seeded = new LinkedHashMap<>();
            seeded.put("ok", true);
            seeded.put("seeded", true);
            seeded.put("softPullback", false);
            seeded.put("onIceSurface", onIceSurface);
            seeded.put("pullbackThresholdM", pullbackThreshold);
            return seeded;
        }
        long last = lastCheckMs.getOrDefault(playerId, 0L);
        if (nowMs - last < CHECK_INTERVAL_MS && last > 0) {
            Map<String, Object> skipped = new LinkedHashMap<>();
            skipped.put("ok", true);
            skipped.put("skipped", true);
            skipped.put("reason", "interval");
            skipped.put("remainMs", CHECK_INTERVAL_MS - (nowMs - last));
            skipped.put("onIceSurface", onIceSurface);
            skipped.put("pullbackThresholdM", pullbackThreshold);
            return skipped;
        }
        lastCheckMs.put(playerId, nowMs);

        String serverHash = computeHash(
                exp.vx(), exp.vy(), exp.vz(), exp.gravityScale(),
                exp.contactNormalX(), exp.contactNormalY(), exp.contactNormalZ());
        double speedDev = relativeDeviation(
                Math.hypot(Math.hypot(clientVx, clientVy), clientVz),
                Math.hypot(Math.hypot(exp.vx(), exp.vy()), exp.vz()));
        double gravDev = relativeDeviation(clientGravity, exp.gravityScale());
        boolean hashMismatch = clientHash != null && !clientHash.isBlank()
                && !serverHash.equalsIgnoreCase(clientHash.trim());
        boolean overThreshold = speedDev > DEVIATION_THRESHOLD || gravDev > DEVIATION_THRESHOLD || hashMismatch;

        /* LitePhysics 粗筛：位置与高度图偏差过大则软拉回 */
        if (!overThreshold && posX != 0f && posZ != 0f) {
            overThreshold = !litePhysics.capsuleOnGround(posX, posY, posZ, 3f);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("serverHash", serverHash);
        body.put("clientHash", clientHash == null ? "" : clientHash);
        body.put("speedDeviation", round4(speedDev));
        body.put("gravityDeviation", round4(gravDev));
        body.put("hashMismatch", hashMismatch);
        body.put("onIceSurface", onIceSurface);
        body.put("pullbackThresholdM", pullbackThreshold);
        body.put("softPullback", overThreshold);
        if (overThreshold) {
            body.put("correctVx", exp.vx());
            body.put("correctVy", exp.vy());
            body.put("correctVz", exp.vz());
            body.put("correctGravity", exp.gravityScale());
            body.put("note", "physics_soft_pullback");
        }
        return body;
    }

    /** 登记钩锁预测路径，200ms 后审计实际落点偏差。 */
    public Map<String, Object> scheduleGrappleAudit(
            long playerId, String auditToken,
            float predictedX, float predictedY, float predictedZ,
            float actualX, float actualY, float actualZ, long nowMs) {
        String token = auditToken == null ? "" : auditToken.trim();
        GrappleAudit audit = new GrappleAudit(
                playerId, token, predictedX, predictedY, predictedZ,
                actualX, actualY, actualZ, nowMs + GRAPPLE_AUDIT_DELAY_MS);
        pendingGrappleAudit.put(token, audit);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("auditToken", token);
        body.put("auditAtMs", audit.scheduledAtMs());
        body.put("auditDelayMs", GRAPPLE_AUDIT_DELAY_MS);
        return body;
    }

    /** 执行钩锁异步审计：偏差 > 3m 则软拉回。 */
    public Map<String, Object> runGrappleAudit(String auditToken, long nowMs) {
        GrappleAudit audit = pendingGrappleAudit.get(auditToken == null ? "" : auditToken.trim());
        if (audit == null) {
            return Map.of("ok", false, "error", "audit_not_found");
        }
        if (nowMs < audit.scheduledAtMs()) {
            return Map.of("ok", true, "pending", true, "remainMs", audit.scheduledAtMs() - nowMs);
        }
        double dx = audit.actualX() - audit.predictedX();
        double dy = audit.actualY() - audit.predictedY();
        double dz = audit.actualZ() - audit.predictedZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        boolean cheat = dist > GRAPPLE_DEVIATION_METERS;
        pendingGrappleAudit.remove(audit.auditToken());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("auditToken", audit.auditToken());
        body.put("deviationM", Math.round(dist * 100d) / 100d);
        body.put("softPullback", cheat);
        body.put("thresholdM", GRAPPLE_DEVIATION_METERS);
        if (cheat) {
            body.put("correctX", audit.predictedX());
            body.put("correctY", audit.predictedY());
            body.put("correctZ", audit.predictedZ());
            body.put("note", "grapple_cheat_soft_pullback");
        } else {
            body.put("note", "grapple_predict_confirmed");
        }
        return body;
    }

    public static String computeHash(
            float vx, float vy, float vz, float gravity,
            float nx, float ny, float nz) {
        String raw = String.format("%.3f|%.3f|%.3f|%.3f|%.3f|%.3f|%.3f",
                vx, vy, vz, gravity, nx, ny, nz);
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] dig = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig);
        } catch (Exception e) {
            return Integer.toHexString(raw.hashCode());
        }
    }

    private static double relativeDeviation(double actual, double expected) {
        double base = Math.max(0.001, Math.abs(expected));
        return Math.abs(actual - expected) / base;
    }

    private static double round4(double v) {
        return Math.round(v * 10_000.0) / 10_000.0;
    }
}
