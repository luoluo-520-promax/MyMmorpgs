package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.config.SessionLoginProperties;
import cn.itcast.demo.mymmorpg.entity.SessionBackup;
import cn.itcast.demo.mymmorpg.repository.SessionBackupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * 将 Redis 在线态关键字段异步备份到 MySQL，并在 Redis 空缺时恢复。
 */
@Service
@EnableConfigurationProperties(SessionLoginProperties.class)
public class SessionBackupService {

    private static final Logger log = LoggerFactory.getLogger(SessionBackupService.class);

    private final PlayerSessionService playerSessionService;
    private final SessionBackupRepository repository;
    private final SessionLoginProperties props;

    public SessionBackupService(PlayerSessionService playerSessionService,
                                SessionBackupRepository repository,
                                SessionLoginProperties props) {
        this.playerSessionService = playerSessionService;
        this.repository = repository;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${game.session.session-backup-interval-seconds:30}000")
    public void backupOnlineSessions() {
        try {
            Set<String> ids = playerSessionService.listOnlinePlayerIds();
            long expireAt = System.currentTimeMillis() + props.getTokenTtlHours() * 3_600_000L;
            for (String id : ids) {
                long playerId;
                try {
                    playerId = Long.parseLong(id);
                } catch (NumberFormatException e) {
                    continue;
                }
                Map<String, String> f = playerSessionService.getOnlineFields(playerId);
                if (f.isEmpty()) {
                    continue;
                }
                upsert(playerId, f, expireAt);
            }
        } catch (Exception e) {
            log.warn("session backup failed: {}", e.getMessage());
        }
    }

    @Transactional
    public void upsert(long playerId, Map<String, String> fields, long expireAt) {
        SessionBackup row = repository.findById(playerId).orElseGet(SessionBackup::new);
        row.setPlayerId(playerId);
        Long boundAccount = null;
        // account 绑定在 auth:account:player 反查不便，这里仅存字段内信息
        row.setSessionId(fields.get("session_id"));
        row.setNodeId(fields.get("node_id"));
        row.setSceneId(parseInt(fields.get("scene_id")));
        row.setDeviceId(fields.get("device_id"));
        row.setClientType(fields.get("client_type"));
        row.setClientIp(fields.get("client_ip"));
        row.setLoginTime(parseLong(fields.get("login_time")));
        row.setLastHeartbeat(parseLong(fields.get("last_heartbeat")));
        row.setExpireAt(expireAt);
        row.setUpdatedAt(LocalDateTime.now());
        row.setAccountId(boundAccount);
        repository.save(row);
    }

    /** Redis 恢复后调用：把未过期备份写回 Redis。 */
    @Transactional(readOnly = true)
    public int restoreActiveSessions() {
        long now = System.currentTimeMillis();
        int restored = 0;
        for (SessionBackup s : repository.findActive(now)) {
            if (playerSessionService.isOnline(s.getPlayerId())) {
                continue;
            }
            playerSessionService.markOnline(s.getPlayerId(), new PlayerSessionService.OnlinePresence(
                    s.getSessionId() == null ? "restored" : s.getSessionId(),
                    s.getNodeId() == null ? "local" : s.getNodeId(),
                    s.getSceneId() == null ? 0 : s.getSceneId(),
                    s.getLoginTime() == null ? now : s.getLoginTime(),
                    s.getClientIp(),
                    s.getDeviceId(),
                    s.getClientType()));
            restored++;
        }
        log.info("restored {} sessions from session_backup", restored);
        return restored;
    }

    private static Integer parseInt(String v) {
        if (v == null || v.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static Long parseLong(String v) {
        if (v == null || v.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
