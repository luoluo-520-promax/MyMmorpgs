package cn.itcast.demo.mymmorpg.rpc;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 中心服维护的场景/Zone 节点目录：跨场景传送时由 GAME 向 CENTRE 查询目标场景所在节点。
 * 单机或单体模式下可不注册节点，{@link #isSceneReachable(int)} 默认放行。
 */
@Component
public class CenterSceneRegistry {

    private final Map<Integer, SceneServerNode> scenes = new ConcurrentHashMap<>();
    private volatile boolean enforceRegistry;

    public void register(SceneServerNode node) {
        if (node != null) {
            scenes.put(node.getSceneId(), node);
            enforceRegistry = true;
        }
    }

    /**
     * 批量注册本节点承载的场景，并刷新心跳时间。
     */
    public void registerNode(String nodeId, String host, int port, List<Integer> sceneIds) {
        if (sceneIds == null || sceneIds.isEmpty() || host == null || host.isBlank() || port <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        int serverId = parseServerId(nodeId);
        for (Integer sceneId : sceneIds) {
            if (sceneId == null || sceneId <= 0) {
                continue;
            }
            scenes.put(sceneId, new SceneServerNode(sceneId, serverId, nodeId, host, port, sceneId, now));
            enforceRegistry = true;
        }
    }

    public void heartbeat(String nodeId, List<Integer> sceneIds) {
        if (sceneIds == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Integer sceneId : sceneIds) {
            if (sceneId == null) {
                continue;
            }
            SceneServerNode old = scenes.get(sceneId);
            if (old != null && (nodeId == null || nodeId.equals(old.getNodeId())
                    || String.valueOf(old.getServerId()).equals(nodeId))) {
                scenes.put(sceneId, old.withHeartbeat(now));
            }
        }
    }

    public void unregister(int sceneId) {
        scenes.remove(sceneId);
        if (scenes.isEmpty()) {
            enforceRegistry = false;
        }
    }

    public void unregisterNode(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return;
        }
        scenes.entrySet().removeIf(e -> nodeId.equals(e.getValue().getNodeId())
                || nodeId.equals(String.valueOf(e.getValue().getServerId())));
        if (scenes.isEmpty()) {
            enforceRegistry = false;
        }
    }

    /** 清理超过 staleMs 未心跳的节点条目。 */
    public int purgeStale(long staleMs) {
        long cutoff = System.currentTimeMillis() - Math.max(1_000L, staleMs);
        int removed = 0;
        for (Map.Entry<Integer, SceneServerNode> e : scenes.entrySet()) {
            if (e.getValue().getLastHeartbeatMillis() > 0 && e.getValue().getLastHeartbeatMillis() < cutoff) {
                if (scenes.remove(e.getKey(), e.getValue())) {
                    removed++;
                }
            }
        }
        if (scenes.isEmpty()) {
            enforceRegistry = false;
        }
        return removed;
    }

    public SceneServerNode find(int sceneId) {
        return scenes.get(sceneId);
    }

    public List<SceneServerNode> listAll() {
        return new ArrayList<>(scenes.values());
    }

    /**
     * 未注册任何场景节点时视为本地可达；注册后仅目录中的 sceneId 可传送。
     */
    public boolean isSceneReachable(int sceneId) {
        if (!enforceRegistry) {
            return true;
        }
        return scenes.containsKey(sceneId);
    }

    private static int parseServerId(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(nodeId.trim());
        } catch (NumberFormatException e) {
            return Math.floorMod(nodeId.hashCode(), 1_000_000);
        }
    }

    public static final class SceneServerNode {
        private final int sceneId;
        private final int serverId;
        private final String nodeId;
        private final String ip;
        private final int port;
        private final int zoneId;
        private final long lastHeartbeatMillis;

        public SceneServerNode(int sceneId, int serverId, String ip, int port) {
            this(sceneId, serverId, String.valueOf(serverId), ip, port, sceneId, System.currentTimeMillis());
        }

        public SceneServerNode(int sceneId, int serverId, String nodeId, String ip, int port,
                               int zoneId, long lastHeartbeatMillis) {
            this.sceneId = sceneId;
            this.serverId = serverId;
            this.nodeId = nodeId == null ? String.valueOf(serverId) : nodeId;
            this.ip = ip;
            this.port = port;
            this.zoneId = zoneId;
            this.lastHeartbeatMillis = lastHeartbeatMillis;
        }

        public SceneServerNode withHeartbeat(long atMillis) {
            return new SceneServerNode(sceneId, serverId, nodeId, ip, port, zoneId, atMillis);
        }

        public int getSceneId() {
            return sceneId;
        }

        public int getServerId() {
            return serverId;
        }

        public String getNodeId() {
            return nodeId;
        }

        public String getIp() {
            return ip;
        }

        public int getPort() {
            return port;
        }

        public int getZoneId() {
            return zoneId;
        }

        public long getLastHeartbeatMillis() {
            return lastHeartbeatMillis;
        }
    }
}
