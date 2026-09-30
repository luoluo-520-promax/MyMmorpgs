package cn.itcast.demo.mymmorpg.world.portal;

import cn.itcast.demo.mymmorpg.center.MigrationTicketService;
import cn.itcast.demo.mymmorpg.world.migrate.GatewayRoutingTable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 场景门预加载：接近 preloadRadius 时向目标 Scene 预租槽位并签发无缝票据，进入 triggerRadius 再提交切换。
 */
@Service
public class PortalPreloadService {

    public enum LeaseStatus { NONE, PRELOADING, READY, CONSUMED, EXPIRED }

    public record PortalLease(
            String leaseId,
            String portalId,
            long playerId,
            int toSceneId,
            int toLineId,
            String targetNodeId,
            String targetHost,
            int targetPort,
            String sessionTicket,
            LeaseStatus status,
            long expireAtMs) {
    }

    /**
     * 门户资源预加载清单：CDN 包体与预估体积，供客户端后台下载。
     */
    public record AssetPreloadManifest(
            String portalId,
            List<String> assetBundleIds,
            long estimatedBytes,
            String cdnBaseUrl) {
    }

    private final ConcurrentHashMap<String, PortalConfig> portals = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PortalLease> leases = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> playerLease = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AssetPreloadManifest> assetManifests = new ConcurrentHashMap<>();
    private final MigrationTicketService tickets;
    private final GatewayRoutingTable routingTable;
    private final long leaseTtlMs;

    public PortalPreloadService(
            ObjectProvider<MigrationTicketService> tickets,
            ObjectProvider<GatewayRoutingTable> routingTable) {
        this(tickets == null ? null : tickets.getIfAvailable(),
                routingTable == null ? null : routingTable.getIfAvailable(),
                15_000L);
    }

    public PortalPreloadService() {
        this(new MigrationTicketService(), new GatewayRoutingTable(), 15_000L);
    }

    public PortalPreloadService(MigrationTicketService tickets, GatewayRoutingTable routingTable, long leaseTtlMs) {
        this.tickets = tickets == null ? new MigrationTicketService() : tickets;
        this.routingTable = routingTable == null ? new GatewayRoutingTable() : routingTable;
        this.leaseTtlMs = Math.max(3_000L, leaseTtlMs);
    }

    public void register(PortalConfig portal) {
        portals.put(portal.portalId(), portal);
    }

    public void registerAssetManifest(AssetPreloadManifest manifest) {
        if (manifest == null || manifest.portalId() == null || manifest.portalId().isBlank()) {
            return;
        }
        List<String> bundles = manifest.assetBundleIds() == null
                ? List.of()
                : List.copyOf(manifest.assetBundleIds());
        assetManifests.put(manifest.portalId(), new AssetPreloadManifest(
                manifest.portalId(),
                bundles,
                Math.max(0L, manifest.estimatedBytes()),
                manifest.cdnBaseUrl() == null ? "" : manifest.cdnBaseUrl()));
    }

    public AssetPreloadManifest getAssetManifest(String portalId) {
        return portalId == null ? null : assetManifests.get(portalId);
    }

    public List<PortalConfig> listByFromScene(int fromSceneId) {
        List<PortalConfig> out = new ArrayList<>();
        for (PortalConfig p : portals.values()) {
            if (p.fromSceneId() == fromSceneId) {
                out.add(p);
            }
        }
        return out;
    }

    /**
     * 玩家移动时调用：在预加载半径内租用目标槽位。
     */
    public Map<String, Object> onPlayerMove(
            long playerId, int fromSceneId, float x, float y, float z,
            float velocityX, float velocityZ, float facingYaw, long nowMs) {
        purgeExpired(nowMs);
        PortalConfig nearest = null;
        double best = Double.MAX_VALUE;
        for (PortalConfig p : listByFromScene(fromSceneId)) {
            if (!p.inPreloadRange(x, y, z)) {
                continue;
            }
            double d = p.distanceSq(x, y, z);
            if (d < best) {
                best = d;
                nearest = p;
            }
        }
        if (nearest == null) {
            releasePlayerLease(playerId);
            return Map.of("ok", true, "status", LeaseStatus.NONE.name());
        }
        String existingId = playerLease.get(playerId);
        if (existingId != null) {
            PortalLease existing = leases.get(existingId);
            if (existing != null && existing.portalId().equals(nearest.portalId())
                    && existing.status() == LeaseStatus.READY && existing.expireAtMs() > nowMs) {
                Map<String, Object> ready = leaseView(existing);
                ready.put("inTrigger", nearest.inTriggerRange(x, y, z));
                return ready;
            }
        }
        return preload(playerId, nearest, velocityX, velocityZ, facingYaw, nowMs);
    }

    public Map<String, Object> preload(
            long playerId, PortalConfig portal,
            float velocityX, float velocityZ, float facingYaw, long nowMs) {
        GatewayRoutingTable.RouteLookup lookup = routingTable.lookup(
                portal.toSceneId(), portal.entryX(), portal.entryZ(), 100,
                portal.targetNodeHint() == null ? "" : portal.targetNodeHint());
        String nodeId = lookup.found() ? lookup.nodeId() : (portal.targetNodeHint() == null ? "scene-local" : portal.targetNodeHint());
        String host = lookup.found() ? lookup.host() : "127.0.0.1";
        int port = lookup.found() ? lookup.port() : 8082;
        String ticket = tickets.issueSeamless(
                playerId, portal.toSceneId(), portal.toLineId(), 0,
                portal.entryX(), portal.entryY(), portal.entryZ(),
                velocityX, velocityZ, facingYaw,
                lookup.found() ? lookup.zoneId() : 0);
        String leaseId = "lease-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        PortalLease lease = new PortalLease(
                leaseId, portal.portalId(), playerId, portal.toSceneId(), portal.toLineId(),
                nodeId, host, port, ticket, LeaseStatus.READY, nowMs + leaseTtlMs);
        releasePlayerLease(playerId);
        leases.put(leaseId, lease);
        playerLease.put(playerId, leaseId);
        Map<String, Object> body = leaseView(lease);
        body.put("preload", true);
        return body;
    }

    public Map<String, Object> consume(long playerId, String leaseId, long nowMs) {
        PortalLease lease = leases.get(leaseId);
        if (lease == null || lease.playerId() != playerId) {
            return Map.of("ok", false, "error", "lease_not_found");
        }
        if (lease.expireAtMs() <= nowMs) {
            leases.remove(leaseId);
            playerLease.remove(playerId, leaseId);
            return Map.of("ok", false, "error", "lease_expired");
        }
        PortalLease consumed = new PortalLease(
                lease.leaseId(), lease.portalId(), lease.playerId(), lease.toSceneId(), lease.toLineId(),
                lease.targetNodeId(), lease.targetHost(), lease.targetPort(), lease.sessionTicket(),
                LeaseStatus.CONSUMED, lease.expireAtMs());
        leases.put(leaseId, consumed);
        playerLease.remove(playerId, leaseId);
        return leaseView(consumed);
    }

    public PortalLease currentLease(long playerId) {
        String id = playerLease.get(playerId);
        return id == null ? null : leases.get(id);
    }

    /**
     * 无缝交接包：票据 + 目标节点 + 资源清单 + 预加载就绪标记。
     */
    public Map<String, Object> buildSeamlessHandoff(long playerId, String leaseId, long nowMs) {
        PortalLease lease = leases.get(leaseId);
        if (lease == null || lease.playerId() != playerId) {
            return Map.of("ok", false, "error", "lease_not_found");
        }
        if (lease.expireAtMs() <= nowMs) {
            return Map.of("ok", false, "error", "lease_expired");
        }
        boolean ready = lease.status() == LeaseStatus.READY || lease.status() == LeaseStatus.CONSUMED;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("ticket", lease.sessionTicket());
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("nodeId", lease.targetNodeId());
        target.put("host", lease.targetHost());
        target.put("port", lease.targetPort());
        target.put("toSceneId", lease.toSceneId());
        target.put("toLineId", lease.toLineId());
        m.put("target", target);
        AssetPreloadManifest manifest = assetManifests.get(lease.portalId());
        if (manifest != null) {
            m.put("assets", assetView(manifest));
        } else {
            m.put("assets", List.of());
        }
        m.put("preloadReady", ready);
        return m;
    }

    private void releasePlayerLease(long playerId) {
        String id = playerLease.remove(playerId);
        if (id != null) {
            leases.remove(id);
        }
    }

    private void purgeExpired(long nowMs) {
        for (Map.Entry<String, PortalLease> e : leases.entrySet()) {
            if (e.getValue().expireAtMs() <= nowMs && e.getValue().status() != LeaseStatus.CONSUMED) {
                leases.remove(e.getKey(), e.getValue());
                playerLease.remove(e.getValue().playerId(), e.getKey());
            }
        }
    }

    private Map<String, Object> leaseView(PortalLease lease) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("leaseId", lease.leaseId());
        m.put("portalId", lease.portalId());
        m.put("playerId", lease.playerId());
        m.put("toSceneId", lease.toSceneId());
        m.put("toLineId", lease.toLineId());
        m.put("targetNodeId", lease.targetNodeId());
        m.put("targetHost", lease.targetHost());
        m.put("targetPort", lease.targetPort());
        m.put("sessionTicket", lease.sessionTicket());
        m.put("status", lease.status().name());
        m.put("expireAtMs", lease.expireAtMs());
        AssetPreloadManifest manifest = assetManifests.get(lease.portalId());
        if (manifest != null) {
            m.put("assets", assetView(manifest));
            m.put("backgroundDownload", true);
        }
        return m;
    }

    private static Map<String, Object> assetView(AssetPreloadManifest manifest) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("portalId", manifest.portalId());
        a.put("assetBundleIds", manifest.assetBundleIds());
        a.put("estimatedBytes", manifest.estimatedBytes());
        a.put("cdnBaseUrl", manifest.cdnBaseUrl());
        return a;
    }
}
