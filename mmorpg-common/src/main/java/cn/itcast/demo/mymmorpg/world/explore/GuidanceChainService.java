package cn.itcast.demo.mymmorpg.world.explore;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 收集物视觉引导链：起点 → 仙灵/风圈节点 → 终点宝箱，AOI 广播路径粒子。
 */
@Service
public class GuidanceChainService {

    public enum NodeKind {
        START, SEELIE, WIND_RING, CHEST
    }

    public record GuideNode(
            String nodeId,
            NodeKind kind,
            float x, float y, float z,
            float triggerRadius) {

        public GuideNode {
            kind = kind == null ? NodeKind.SEELIE : kind;
            triggerRadius = triggerRadius <= 0f ? 5f : triggerRadius;
        }
    }

    public record GuideChainDef(
            String chainId,
            String regionId,
            String chestCollectibleId,
            List<GuideNode> nodes) {

        public GuideChainDef {
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
        }
    }

    public record GuideChainProgress(
            String chainId,
            int nextNodeIndex,
            boolean completed,
            long startedAtMs,
            long lastParticleAtMs) {
    }

    private final ConcurrentHashMap<String, GuideChainDef> chains = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, GuideChainProgress> progress = new ConcurrentHashMap<>();

    public void register(GuideChainDef def) {
        if (def != null && def.chainId() != null) {
            chains.put(def.chainId(), def);
        }
    }

    private static String progressKey(long playerId, String chainId) {
        return playerId + ":" + chainId;
    }

    /** 触发起点：开始引导链。 */
    public Map<String, Object> triggerStart(long playerId, String chainId, float px, float py, float pz, long nowMs) {
        GuideChainDef def = chains.get(chainId);
        if (def == null || def.nodes().isEmpty()) {
            return Map.of("ok", false, "error", "chain_not_found");
        }
        GuideNode start = def.nodes().get(0);
        if (dist(px, py, pz, start) > start.triggerRadius()) {
            return Map.of("ok", false, "error", "out_of_start_range");
        }
        String key = progressKey(playerId, chainId);
        GuideChainProgress existing = progress.get(key);
        if (existing != null && existing.completed()) {
            return Map.of("ok", false, "error", "already_completed", "chainId", chainId);
        }
        GuideChainProgress p = new GuideChainProgress(chainId, 1, false, nowMs, 0L);
        progress.put(key, p);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("chainId", chainId);
        body.put("started", true);
        body.put("nextNode", nodeView(nextNode(def, p)));
        body.put("particles", List.of(particleAt(start, nowMs)));
        return body;
    }

    /** 靠近当前引导节点推进。 */
    public Map<String, Object> advance(long playerId, String chainId, float px, float py, float pz, long nowMs) {
        GuideChainDef def = chains.get(chainId);
        if (def == null) {
            return Map.of("ok", false, "error", "chain_not_found");
        }
        String key = progressKey(playerId, chainId);
        GuideChainProgress p = progress.get(key);
        if (p == null) {
            return Map.of("ok", false, "error", "not_started");
        }
        if (p.completed()) {
            return Map.of("ok", true, "completed", true, "chainId", chainId);
        }
        GuideNode cur = nextNode(def, p);
        if (cur == null) {
            GuideChainProgress done = new GuideChainProgress(chainId, def.nodes().size(), true, p.startedAtMs(), nowMs);
            progress.put(key, done);
            return Map.of("ok", true, "completed", true, "chainId", chainId,
                    "chestCollectibleId", def.chestCollectibleId());
        }
        if (dist(px, py, pz, cur) > cur.triggerRadius()) {
            return Map.of("ok", false, "error", "out_of_node_range", "nodeId", cur.nodeId());
        }
        int nextIdx = p.nextNodeIndex() + 1;
        boolean completed = nextIdx >= def.nodes().size();
        GuideChainProgress updated = new GuideChainProgress(
                chainId, nextIdx, completed, p.startedAtMs(), nowMs);
        progress.put(key, updated);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("chainId", chainId);
        body.put("reachedNode", nodeView(cur));
        body.put("completed", completed);
        if (completed) {
            body.put("chestCollectibleId", def.chestCollectibleId());
        } else {
            body.put("nextNode", nodeView(nextNode(def, updated)));
        }
        return body;
    }

    /**
     * 定时刷新路径粒子，供 AOI 广播（已启动且未完成的引导链）。
     */
    public Map<String, Object> refreshParticles(long playerId, String chainId, long nowMs) {
        GuideChainDef def = chains.get(chainId);
        if (def == null) {
            return Map.of("ok", false, "error", "chain_not_found");
        }
        String key = progressKey(playerId, chainId);
        GuideChainProgress p = progress.get(key);
        if (p == null || p.completed()) {
            return Map.of("ok", false, "error", "inactive");
        }
        GuideNode cur = nextNode(def, p);
        List<Map<String, Object>> particles = new ArrayList<>();
        // 从当前节点向前画一段路径粒子
        for (int i = Math.max(0, p.nextNodeIndex() - 1); i < Math.min(def.nodes().size(), p.nextNodeIndex() + 2); i++) {
            particles.add(particleAt(def.nodes().get(i), nowMs));
        }
        progress.put(key, new GuideChainProgress(
                chainId, p.nextNodeIndex(), false, p.startedAtMs(), nowMs));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("chainId", chainId);
        body.put("aoiBroadcast", true);
        body.put("guideParticles", particles);
        body.put("focusNode", cur == null ? Map.of() : nodeView(cur));
        body.put("atMs", nowMs);
        return body;
    }

    public Map<String, Object> progressOf(long playerId, String chainId) {
        GuideChainProgress p = progress.get(progressKey(playerId, chainId));
        if (p == null) {
            return Map.of("ok", true, "started", false, "chainId", chainId);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("started", true);
        body.put("chainId", chainId);
        body.put("nextNodeIndex", p.nextNodeIndex());
        body.put("completed", p.completed());
        body.put("startedAtMs", p.startedAtMs());
        return body;
    }

    private static GuideNode nextNode(GuideChainDef def, GuideChainProgress p) {
        if (p.nextNodeIndex() < 0 || p.nextNodeIndex() >= def.nodes().size()) {
            return null;
        }
        return def.nodes().get(p.nextNodeIndex());
    }

    private static double dist(float px, float py, float pz, GuideNode n) {
        float dx = px - n.x();
        float dy = py - n.y();
        float dz = pz - n.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static Map<String, Object> nodeView(GuideNode n) {
        if (n == null) {
            return Map.of();
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nodeId", n.nodeId());
        m.put("kind", n.kind().name());
        m.put("x", n.x());
        m.put("y", n.y());
        m.put("z", n.z());
        return m;
    }

    private static Map<String, Object> particleAt(GuideNode n, long nowMs) {
        Map<String, Object> m = new LinkedHashMap<>(nodeView(n));
        m.put("particle", "GUIDE_TRAIL");
        m.put("atMs", nowMs);
        return m;
    }
}
