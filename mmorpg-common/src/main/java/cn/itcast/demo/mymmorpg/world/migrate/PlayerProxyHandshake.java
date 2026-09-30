package cn.itcast.demo.mymmorpg.world.migrate;

import cn.itcast.demo.mymmorpg.center.MigrationTicketService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 场景节点间 Player Proxy 迁移握手：PREPARE → COMMIT → ABORT。
 * 客户端保持连接时由网关按路由表切换后端，玩家无感知。
 */
@Service
public class PlayerProxyHandshake {

    public enum Phase {
        PREPARE,
        COMMITTED,
        ABORTED
    }

    public record MigrationSession(
            String migrationId,
            long playerId,
            String fromNodeId,
            String toNodeId,
            String toHost,
            int toPort,
            int sceneId,
            int lineId,
            int zoneId,
            float posX,
            float posY,
            float posZ,
            float velocityX,
            float velocityZ,
            float facingYaw,
            String sessionTicket,
            Phase phase,
            long createdAtMs) {
    }

    private final MigrationTicketService migrationTicketService;
    private final ConcurrentHashMap<String, MigrationSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> playerMigration = new ConcurrentHashMap<>();

    public PlayerProxyHandshake(MigrationTicketService migrationTicketService) {
        this.migrationTicketService = migrationTicketService == null
                ? new MigrationTicketService() : migrationTicketService;
    }

    public PlayerProxyHandshake() {
        this(new MigrationTicketService());
    }

    public Map<String, Object> prepare(
            long playerId,
            String fromNodeId,
            GatewayRoutingTable.RouteLookup target,
            int sceneId,
            int lineId,
            float posX, float posY, float posZ,
            float velocityX, float velocityZ, float facingYaw) {
        if (!target.found() || target.sameNode()) {
            return Map.of("ok", false, "error", target.sameNode() ? "same_node" : "route_miss");
        }
        String existing = playerMigration.get(playerId);
        if (existing != null) {
            MigrationSession cur = sessions.get(existing);
            if (cur != null && cur.phase() == Phase.PREPARE) {
                return toView(cur);
            }
        }
        String ticket = migrationTicketService.issueSeamless(
                playerId, sceneId, lineId, 0, posX, posY, posZ,
                velocityX, velocityZ, facingYaw, target.zoneId());
        String migrationId = UUID.randomUUID().toString().replace("-", "");
        MigrationSession session = new MigrationSession(
                migrationId, playerId, fromNodeId, target.nodeId(), target.host(), target.port(),
                sceneId, lineId, target.zoneId(), posX, posY, posZ,
                velocityX, velocityZ, facingYaw, ticket, Phase.PREPARE, System.currentTimeMillis());
        sessions.put(migrationId, session);
        playerMigration.put(playerId, migrationId);
        return toView(session);
    }

    public Map<String, Object> commit(String migrationId) {
        MigrationSession cur = sessions.get(migrationId);
        if (cur == null) {
            return Map.of("ok", false, "error", "not_found");
        }
        if (cur.phase() != Phase.PREPARE) {
            return Map.of("ok", false, "error", "invalid_phase", "phase", cur.phase().name());
        }
        MigrationSession next = withPhase(cur, Phase.COMMITTED);
        sessions.put(migrationId, next);
        return toView(next);
    }

    public Map<String, Object> abort(String migrationId) {
        MigrationSession cur = sessions.get(migrationId);
        if (cur == null) {
            return Map.of("ok", false, "error", "not_found");
        }
        MigrationSession next = withPhase(cur, Phase.ABORTED);
        sessions.put(migrationId, next);
        playerMigration.remove(cur.playerId(), migrationId);
        return toView(next);
    }

    public MigrationSession get(String migrationId) {
        return sessions.get(migrationId);
    }

    public MigrationSession findByPlayer(long playerId) {
        String id = playerMigration.get(playerId);
        return id == null ? null : sessions.get(id);
    }

    private static MigrationSession withPhase(MigrationSession cur, Phase phase) {
        return new MigrationSession(
                cur.migrationId(), cur.playerId(), cur.fromNodeId(), cur.toNodeId(),
                cur.toHost(), cur.toPort(), cur.sceneId(), cur.lineId(), cur.zoneId(),
                cur.posX(), cur.posY(), cur.posZ(), cur.velocityX(), cur.velocityZ(),
                cur.facingYaw(), cur.sessionTicket(), phase, cur.createdAtMs());
    }

    private Map<String, Object> toView(MigrationSession s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("migrationId", s.migrationId());
        m.put("playerId", s.playerId());
        m.put("fromNodeId", s.fromNodeId());
        m.put("toNodeId", s.toNodeId());
        m.put("redirectHost", s.toHost());
        m.put("redirectPort", s.toPort());
        m.put("sessionTicket", s.sessionTicket());
        m.put("zoneId", s.zoneId());
        m.put("seamless", true);
        m.put("phase", s.phase().name());
        m.put("posX", s.posX());
        m.put("posY", s.posY());
        m.put("posZ", s.posZ());
        return m;
    }
}
