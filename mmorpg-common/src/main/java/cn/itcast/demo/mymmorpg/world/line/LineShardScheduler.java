package cn.itcast.demo.mymmorpg.world.line;

import cn.itcast.demo.mymmorpg.center.MigrationTicketService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 分线智能调度：线作为独立 Zone 对象存 Redis；软顶引导 / 硬顶拒绝；同线组队强制拉入。
 */
@Component
public class LineShardScheduler {

    public enum AdmitResult {
        OK, SOFT_CAP_BUFF, HARD_CAP_REJECT, INVALID_LINE
    }

    public record LineAdmit(
            AdmitResult result,
            int sceneId,
            int lineId,
            int playerCount,
            int softCap,
            int hardCap,
            String buffId,
            String reason) {
    }

    public record PartyPullTicket(
            long memberId,
            int sceneId,
            int targetLineId,
            float x, float y, float z,
            String sessionTicket) {
    }

    private static final String KEY_PLAYERS = "zone:";
    private static final String KEY_SUFFIX = ":players";

    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final MigrationTicketService tickets;
    private final ConcurrentHashMap<String, Set<Long>> localLines = new ConcurrentHashMap<>();
    private final AtomicLong softCapHits = new AtomicLong();
    private final AtomicLong hardCapHits = new AtomicLong();
    private final AtomicLong partyPulls = new AtomicLong();

    private volatile int softCap = 80;
    private volatile int hardCap = 100;
    private volatile String softCapBuffId = "line_overflow_drop_bonus_5";

    public LineShardScheduler(
            ObjectProvider<StringRedisTemplate> redisProvider,
            ObjectProvider<MigrationTicketService> tickets) {
        this.redisProvider = redisProvider;
        this.tickets = tickets == null || tickets.getIfAvailable() == null
                ? new MigrationTicketService()
                : tickets.getIfAvailable();
    }

    public LineShardScheduler() {
        this(null, null);
    }

    public void configure(int softCap, int hardCap, String softCapBuffId) {
        this.softCap = Math.max(1, softCap);
        this.hardCap = Math.max(this.softCap, hardCap);
        if (softCapBuffId != null && !softCapBuffId.isBlank()) {
            this.softCapBuffId = softCapBuffId.trim();
        }
    }

    public static String redisKey(int sceneId, int lineId) {
        return KEY_PLAYERS + sceneId + ":" + lineId + KEY_SUFFIX;
    }

    public int playerCount(int sceneId, int lineId) {
        StringRedisTemplate redis = redis();
        if (redis != null) {
            try {
                Long n = redis.opsForSet().size(redisKey(sceneId, lineId));
                return n == null ? 0 : n.intValue();
            } catch (Exception ignored) {
            }
        }
        Set<Long> set = localLines.get(lineKey(sceneId, lineId));
        return set == null ? 0 : set.size();
    }

    public void join(int sceneId, int lineId, long playerId) {
        StringRedisTemplate redis = redis();
        if (redis != null) {
            try {
                String key = redisKey(sceneId, lineId);
                redis.opsForSet().add(key, String.valueOf(playerId));
                redis.expire(key, Duration.ofHours(6));
                return;
            } catch (Exception ignored) {
            }
        }
        localLines.computeIfAbsent(lineKey(sceneId, lineId), k -> ConcurrentHashMap.newKeySet()).add(playerId);
    }

    public void leave(int sceneId, int lineId, long playerId) {
        StringRedisTemplate redis = redis();
        if (redis != null) {
            try {
                redis.opsForSet().remove(redisKey(sceneId, lineId), String.valueOf(playerId));
                return;
            } catch (Exception ignored) {
            }
        }
        Set<Long> set = localLines.get(lineKey(sceneId, lineId));
        if (set != null) {
            set.remove(playerId);
        }
    }

    /**
     * 进线裁决：硬顶禁止；软顶放行并附掉落 Buff 引导去新线。
     */
    public LineAdmit admit(int sceneId, int lineId, int maxLines) {
        if (lineId < 1 || (maxLines > 0 && lineId > maxLines)) {
            return new LineAdmit(AdmitResult.INVALID_LINE, sceneId, lineId, 0, softCap, hardCap, "", "invalid_line");
        }
        int count = playerCount(sceneId, lineId);
        if (count >= hardCap) {
            hardCapHits.incrementAndGet();
            return new LineAdmit(AdmitResult.HARD_CAP_REJECT, sceneId, lineId, count, softCap, hardCap,
                    "", "hard_cap");
        }
        if (count >= softCap) {
            softCapHits.incrementAndGet();
            return new LineAdmit(AdmitResult.SOFT_CAP_BUFF, sceneId, lineId, count, softCap, hardCap,
                    softCapBuffId, "soft_cap_buff");
        }
        return new LineAdmit(AdmitResult.OK, sceneId, lineId, count, softCap, hardCap, "", "ok");
    }

    /**
     * 选择推荐线：优先未达软顶；否则未达硬顶；全满返回 -1。
     */
    public int recommendLine(int sceneId, int maxLines) {
        int max = Math.max(1, maxLines);
        for (int i = 1; i <= max; i++) {
            if (playerCount(sceneId, i) < softCap) {
                return i;
            }
        }
        for (int i = 1; i <= max; i++) {
            if (playerCount(sceneId, i) < hardCap) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 同线组队强制拉入：为其他线队友签发换线 Session Ticket。
     */
    public List<PartyPullTicket> pullPartyToLeaderLine(
            long leaderId,
            int sceneId,
            int leaderLineId,
            float x, float y, float z,
            List<Long> memberIds,
            Map<Long, Integer> memberCurrentLines) {
        List<PartyPullTicket> out = new ArrayList<>();
        if (memberIds == null) {
            return out;
        }
        for (Long mid : memberIds) {
            if (mid == null || mid == leaderId) {
                continue;
            }
            Integer curLine = memberCurrentLines == null ? null : memberCurrentLines.get(mid);
            if (curLine != null && curLine == leaderLineId) {
                continue;
            }
            String ticket = tickets.issueSeamless(
                    mid, sceneId, leaderLineId, 0, x, y, z, 0f, 0f, 0f, leaderLineId);
            out.add(new PartyPullTicket(mid, sceneId, leaderLineId, x, y, z, ticket));
            partyPulls.incrementAndGet();
        }
        return out;
    }

    public Map<String, Object> admitView(LineAdmit admit) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("result", admit.result().name());
        m.put("sceneId", admit.sceneId());
        m.put("lineId", admit.lineId());
        m.put("playerCount", admit.playerCount());
        m.put("softCap", admit.softCap());
        m.put("hardCap", admit.hardCap());
        m.put("buffId", admit.buffId());
        m.put("reason", admit.reason());
        m.put("ok", admit.result() != AdmitResult.HARD_CAP_REJECT
                && admit.result() != AdmitResult.INVALID_LINE);
        return m;
    }

    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("softCap", softCap);
        m.put("hardCap", hardCap);
        m.put("softCapBuffId", softCapBuffId);
        m.put("softCapHits", softCapHits.get());
        m.put("hardCapHits", hardCapHits.get());
        m.put("partyPulls", partyPulls.get());
        m.put("localLines", localLines.size());
        return m;
    }

    private static String lineKey(int sceneId, int lineId) {
        return sceneId + ":" + lineId;
    }

    private StringRedisTemplate redis() {
        return redisProvider == null ? null : redisProvider.getIfAvailable();
    }
}
