package cn.itcast.demo.mymmorpg.abyss;

import cn.itcast.demo.mymmorpg.client.HallMailClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 深渊：3×4 间连打；会话态 Redis {@code abyss:session:{playerId}}；
 * 星级 Redis {@code abyss:stars:{playerId}}；里程碑邮件 9/12/15。
 */
@Service
public class AbyssService {

    private static final Logger log = LoggerFactory.getLogger(AbyssService.class);

    public static final String REDIS_SESSION = "abyss:session:";
    public static final String REDIS_STARS = "abyss:stars:";
    public static final int ITEM_PRIMOGEM = 10002;
    public static final int ITEM_ARTIFACT_SHARD = 30001;

    private final AbyssFloorConfigLoader configLoader;
    private final ObjectProvider<StringRedisTemplate> redisTemplate;
    private final ObjectProvider<HallMailClient> hallMailClient;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<Long, AbyssSession> localSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Integer> localStars = new ConcurrentHashMap<>();

    public AbyssService(AbyssFloorConfigLoader configLoader,
                        ObjectProvider<StringRedisTemplate> redisTemplate,
                        ObjectProvider<HallMailClient> hallMailClient,
                        ObjectMapper objectMapper) {
        this.configLoader = configLoader;
        this.redisTemplate = redisTemplate;
        this.hallMailClient = hallMailClient;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> start(long playerId) {
        if (playerId <= 0) {
            return Map.of("ok", false, "error", "INVALID_PLAYER");
        }
        AbyssFloorConfigDocument cfg = configLoader.current();
        AbyssSession session = loadSession(playerId);
        if (session == null) {
            session = new AbyssSession();
            session.setPlayerId(playerId);
            session.setFloorIndex(1);
            session.setChamberIndex(1);
            session.setHp(10000);
            session.setEnergy(0);
            session.setSkillCdMs(new ArrayList<>());
            session.setStarsEarned(starsOf(playerId));
            session.setClaimedMilestones(new ArrayList<>());
        }
        session.setBlessing(cfg.getBlessing().toMap());
        session.setChamberStartedAtMs(System.currentTimeMillis());
        saveSession(session);
        return chamberView(session, true);
    }

    /**
     * 结算当前间：cleared=true 计星；剩余时间 ≥60→3星 / ≥30→2星 / 通关→1星。
     * 连打时保留 hp/energy/skillCd。
     */
    public Map<String, Object> finishChamber(long playerId, boolean cleared, int remainHp, int energy,
                                             List<Integer> skillCdMs, long remainTimeSec) {
        AbyssSession session = loadSession(playerId);
        if (session == null) {
            return Map.of("ok", false, "error", "NO_SESSION");
        }
        int gained = 0;
        if (cleared) {
            gained = starsForRemain(remainTimeSec);
            session.setStarsEarned(session.getStarsEarned() + gained);
            saveStars(playerId, session.getStarsEarned());
            List<Map<String, Object>> mails = claimMilestones(session);
            session.setHp(Math.max(1, remainHp));
            session.setEnergy(Math.max(0, energy));
            if (skillCdMs != null) {
                session.setSkillCdMs(new ArrayList<>(skillCdMs));
            }
            advanceChamber(session);
            saveSession(session);
            Map<String, Object> out = chamberView(session, false);
            out.put("ok", true);
            out.put("starsGained", gained);
            out.put("totalStars", session.getStarsEarned());
            out.put("mails", mails);
            return out;
        }
        // 失败：保留会话但不推进
        session.setHp(Math.max(0, remainHp));
        session.setEnergy(Math.max(0, energy));
        if (skillCdMs != null) {
            session.setSkillCdMs(new ArrayList<>(skillCdMs));
        }
        saveSession(session);
        Map<String, Object> out = chamberView(session, false);
        out.put("ok", true);
        out.put("starsGained", 0);
        out.put("totalStars", session.getStarsEarned());
        out.put("failed", true);
        return out;
    }

    public Map<String, Object> progress(long playerId) {
        AbyssSession session = loadSession(playerId);
        if (session == null) {
            return Map.of("ok", true, "active", false, "totalStars", starsOf(playerId),
                    "blessing", configLoader.current().getBlessing().toMap());
        }
        Map<String, Object> out = chamberView(session, false);
        out.put("ok", true);
        out.put("active", true);
        out.put("totalStars", starsOf(playerId));
        return out;
    }

    public void resetAllStars() {
        localStars.clear();
        localSessions.clear();
        StringRedisTemplate redis = redisTemplate.getIfAvailable();
        if (redis == null) {
            return;
        }
        // 生产环境可用 SCAN；此处清本地 + 约定运维对 abyss:stars:* / abyss:session:* 批量清理
        log.info("abyss biweekly reset: local caches cleared");
    }

    public void resetPlayer(long playerId) {
        localStars.remove(playerId);
        localSessions.remove(playerId);
        StringRedisTemplate redis = redisTemplate.getIfAvailable();
        if (redis != null) {
            redis.delete(REDIS_STARS + playerId);
            redis.delete(REDIS_SESSION + playerId);
        }
    }

    /** 剩余时间满星条件。 */
    public static int starsForRemain(long remainTimeSec) {
        if (remainTimeSec >= 60) {
            return 3;
        }
        if (remainTimeSec >= 30) {
            return 2;
        }
        return 1;
    }

    private List<Map<String, Object>> claimMilestones(AbyssSession session) {
        List<Map<String, Object>> sent = new ArrayList<>();
        int stars = session.getStarsEarned();
        for (int milestone : List.of(9, 12, 15)) {
            if (stars < milestone || session.getClaimedMilestones().contains(milestone)) {
                continue;
            }
            String attachments = attachmentsFor(milestone);
            sendMail(session.getPlayerId(), "深渊里程碑 " + milestone + " 星",
                    "本期深渊累计达到 " + milestone + " 星奖励。", attachments);
            session.getClaimedMilestones().add(milestone);
            sent.add(Map.of("milestone", milestone, "attachmentsJson", attachments));
        }
        return sent;
    }

    static String attachmentsFor(int milestone) {
        return switch (milestone) {
            case 9 -> "[{\"itemId\":10002,\"count\":50}]";
            case 12 -> "[{\"itemId\":10002,\"count\":100}]";
            case 15 -> "[{\"itemId\":10002,\"count\":200},{\"itemId\":30001,\"count\":5}]";
            default -> "[]";
        };
    }

    private void advanceChamber(AbyssSession session) {
        AbyssFloorConfigDocument cfg = configLoader.current();
        int floorIdx = session.getFloorIndex();
        int chamberIdx = session.getChamberIndex();
        AbyssFloorConfigDocument.Floor floor = cfg.getFloors().stream()
                .filter(f -> f.getFloorIndex() == floorIdx)
                .findFirst()
                .orElse(null);
        if (floor == null) {
            return;
        }
        if (chamberIdx < floor.getChambers().size()) {
            session.setChamberIndex(chamberIdx + 1);
            session.setChamberStartedAtMs(System.currentTimeMillis());
            return;
        }
        AbyssFloorConfigDocument.Floor nextFloor = cfg.getFloors().stream()
                .filter(f -> f.getFloorIndex() == floorIdx + 1)
                .findFirst()
                .orElse(null);
        if (nextFloor == null) {
            // 全部打完，保留会话标记为完成
            session.setChamberIndex(chamberIdx);
            return;
        }
        session.setFloorIndex(nextFloor.getFloorIndex());
        session.setChamberIndex(1);
        session.setChamberStartedAtMs(System.currentTimeMillis());
    }

    private Map<String, Object> chamberView(AbyssSession session, boolean started) {
        AbyssFloorConfigDocument cfg = configLoader.current();
        AbyssFloorConfigDocument.Chamber chamber = resolveChamber(cfg, session.getFloorIndex(), session.getChamberIndex());
        Map<String, Object> out = new LinkedHashMap<>();
        if (started) {
            out.put("ok", true);
        }
        out.put("playerId", session.getPlayerId());
        out.put("floorIndex", session.getFloorIndex());
        out.put("chamberIndex", session.getChamberIndex());
        out.put("hp", session.getHp());
        out.put("energy", session.getEnergy());
        out.put("skillCdMs", session.getSkillCdMs());
        out.put("starsEarned", session.getStarsEarned());
        out.put("blessing", session.getBlessing());
        out.put("seasonId", cfg.getSeasonId());
        if (chamber != null) {
            out.put("monsterGroup", chamber.getMonsterGroup());
            out.put("timeLimitSec", chamber.getTimeLimitSec());
        }
        return out;
    }

    private static AbyssFloorConfigDocument.Chamber resolveChamber(AbyssFloorConfigDocument cfg,
                                                                  int floorIndex, int chamberIndex) {
        return cfg.getFloors().stream()
                .filter(f -> f.getFloorIndex() == floorIndex)
                .flatMap(f -> f.getChambers().stream())
                .filter(c -> c.getChamberIndex() == chamberIndex)
                .findFirst()
                .orElse(null);
    }

    private AbyssSession loadSession(long playerId) {
        AbyssSession local = localSessions.get(playerId);
        if (local != null) {
            return local;
        }
        StringRedisTemplate redis = redisTemplate.getIfAvailable();
        if (redis == null) {
            return null;
        }
        try {
            String json = redis.opsForValue().get(REDIS_SESSION + playerId);
            if (json == null || json.isBlank()) {
                return null;
            }
            AbyssSession s = objectMapper.readValue(json, AbyssSession.class);
            localSessions.put(playerId, s);
            return s;
        } catch (Exception e) {
            log.warn("load abyss session failed playerId={}", playerId, e);
            return null;
        }
    }

    private void saveSession(AbyssSession session) {
        localSessions.put(session.getPlayerId(), session);
        StringRedisTemplate redis = redisTemplate.getIfAvailable();
        if (redis == null) {
            return;
        }
        try {
            redis.opsForValue().set(REDIS_SESSION + session.getPlayerId(),
                    objectMapper.writeValueAsString(session), Duration.ofDays(20));
        } catch (Exception e) {
            log.warn("save abyss session failed playerId={}", session.getPlayerId(), e);
        }
    }

    private int starsOf(long playerId) {
        Integer local = localStars.get(playerId);
        if (local != null) {
            return local;
        }
        StringRedisTemplate redis = redisTemplate.getIfAvailable();
        if (redis != null) {
            String v = redis.opsForValue().get(REDIS_STARS + playerId);
            if (v != null) {
                try {
                    int s = Integer.parseInt(v);
                    localStars.put(playerId, s);
                    return s;
                } catch (NumberFormatException ignore) {
                    // fallthrough
                }
            }
        }
        return 0;
    }

    private void saveStars(long playerId, int stars) {
        localStars.put(playerId, stars);
        StringRedisTemplate redis = redisTemplate.getIfAvailable();
        if (redis != null) {
            redis.opsForValue().set(REDIS_STARS + playerId, Integer.toString(stars), Duration.ofDays(40));
        }
    }

    private void sendMail(long playerId, String title, String body, String attachmentsJson) {
        HallMailClient client = hallMailClient.getIfAvailable();
        if (client == null) {
            return;
        }
        try {
            client.sendMail(Map.of(
                    "playerId", playerId,
                    "title", title,
                    "body", body,
                    "attachmentsJson", attachmentsJson));
        } catch (Exception e) {
            log.warn("abyss mail send failed playerId={}", playerId, e);
        }
    }
}
