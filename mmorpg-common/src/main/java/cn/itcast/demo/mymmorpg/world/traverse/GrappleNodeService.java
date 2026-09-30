package cn.itcast.demo.mymmorpg.world.traverse;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 钩锁锚点：map_grapple_nodes 语义（坐标 + CD）；射线粗筛无障碍后 admit。
 */
@Service
public class GrappleNodeService {

    public record GrappleNode(
            String nodeId,
            int worldId,
            float x, float y, float z,
            float captureRadius,
            long cooldownMs) {
        public GrappleNode {
            nodeId = nodeId == null ? "" : nodeId.trim();
            captureRadius = captureRadius <= 0f ? 3f : captureRadius;
            cooldownMs = cooldownMs <= 0 ? 1500L : cooldownMs;
        }
    }

    private final ConcurrentHashMap<String, GrappleNode> nodes = new ConcurrentHashMap<>();
    /** playerId:nodeId → 上次抓取时间 */
    private final ConcurrentHashMap<String, Long> lastUse = new ConcurrentHashMap<>();
    /** 空间哈希粗筛：阻塞体素 */
    private final ConcurrentHashMap<Long, Boolean> blockedCells = new ConcurrentHashMap<>();
    private static final float CELL = 4f;

    public void register(GrappleNode node) {
        if (node != null && !node.nodeId().isBlank()) {
            nodes.put(node.nodeId(), node);
        }
    }

    public void markBlocked(float x, float y, float z) {
        blockedCells.put(cellKey(x, y, z), true);
    }

    public void clearBlocked(float x, float y, float z) {
        blockedCells.remove(cellKey(x, y, z));
    }

    /** 预测-回滚钩锁：立即 ACK，异步审计落点偏差。 */
    public Map<String, Object> predictGrapple(
            long playerId, String nodeId, String targetPointHash,
            float velX, float velY, float velZ, long nowMs) {
        String auditToken = playerId + ":" + (targetPointHash == null ? "" : targetPointHash.trim())
                + ":" + nowMs;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("retcode", cn.itcast.demo.mymmorpg.protocol.RetCode.GRAPPLE_PREDICT_ACK);
        body.put("ack", true);
        body.put("auditToken", auditToken);
        body.put("clientPlayImmediately", true);
        body.put("predictedVel", Map.of("x", velX, "y", velY, "z", velZ));
        body.put("nodeId", nodeId == null ? "" : nodeId.trim());
        body.put("auditDelayMs", 200);
        return body;
    }

    /**
     * 校验 from→to 是否可达指定锚点：距离、CD、射线粗筛。
     */
    public Map<String, Object> admitGrapple(
            long playerId, String nodeId,
            float fromX, float fromY, float fromZ,
            float toX, float toY, float toZ,
            long nowMs) {
        GrappleNode node = nodes.get(nodeId == null ? "" : nodeId.trim());
        if (node == null) {
            return Map.of("ok", false, "error", "grapple_node_not_found");
        }
        float ndx = toX - node.x();
        float ndy = toY - node.y();
        float ndz = toZ - node.z();
        if (Math.sqrt(ndx * ndx + ndy * ndy + ndz * ndz) > node.captureRadius()) {
            return Map.of("ok", false, "error", "target_not_on_node", "nodeId", node.nodeId());
        }
        String cdKey = playerId + ":" + node.nodeId();
        Long last = lastUse.get(cdKey);
        if (last != null && nowMs - last < node.cooldownMs()) {
            return Map.of("ok", false, "error", "grapple_cooldown",
                    "remainMs", node.cooldownMs() - (nowMs - last));
        }
        if (rayBlocked(fromX, fromY, fromZ, toX, toY, toZ)) {
            return Map.of("ok", false, "error", "line_of_sight_blocked", "nodeId", node.nodeId());
        }
        double dist = Math.sqrt(
                (toX - fromX) * (toX - fromX)
                        + (toY - fromY) * (toY - fromY)
                        + (toZ - fromZ) * (toZ - fromZ));
        if (dist > 48.0) {
            return Map.of("ok", false, "error", "grapple_too_far", "dist", dist);
        }
        lastUse.put(cdKey, nowMs);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("nodeId", node.nodeId());
        body.put("dist", Math.round(dist * 100d) / 100d);
        body.put("cooldownMs", node.cooldownMs());
        body.put("targetX", node.x());
        body.put("targetY", node.y());
        body.put("targetZ", node.z());
        return body;
    }

    public GrappleNode get(String nodeId) {
        return nodes.get(nodeId);
    }

    private boolean rayBlocked(float x0, float y0, float z0, float x1, float y1, float z1) {
        int steps = 8;
        for (int i = 1; i < steps; i++) {
            float t = i / (float) steps;
            float x = x0 + (x1 - x0) * t;
            float y = y0 + (y1 - y0) * t;
            float z = z0 + (z1 - z0) * t;
            if (Boolean.TRUE.equals(blockedCells.get(cellKey(x, y, z)))) {
                return true;
            }
        }
        return false;
    }

    private static long cellKey(float x, float y, float z) {
        int cx = (int) Math.floor(x / CELL);
        int cy = (int) Math.floor(y / CELL);
        int cz = (int) Math.floor(z / CELL);
        return (((long) cx & 0xfffff) << 40) | (((long) cy & 0xfffff) << 20) | ((long) cz & 0xfffff);
    }
}
