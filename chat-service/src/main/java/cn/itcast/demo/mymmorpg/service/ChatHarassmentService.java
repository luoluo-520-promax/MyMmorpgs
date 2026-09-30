package cn.itcast.demo.mymmorpg.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 聊天反骚扰：举报积分、自动禁言阈值、玩家拉黑。
 */
@Service
public class ChatHarassmentService {

    private static final String KEY_SCORE = "chat:harass:score:";
    private static final String KEY_BLOCK = "chat:block:";
    private static final String KEY_MUTE = "chat:mute:until:";
    private static final int AUTO_MUTE_THRESHOLD = 20;
    private static final Duration MUTE_DURATION = Duration.ofHours(2);

    private final StringRedisTemplate stringRedisTemplate;

    public ChatHarassmentService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public Map<String, Object> recordReportHit(long targetPlayerId, String reason) {
        if (targetPlayerId <= 0) {
            return Map.of("ok", false, "error", "invalid_target");
        }
        int weight = weightOf(reason);
        Long score = stringRedisTemplate.opsForValue().increment(KEY_SCORE + targetPlayerId, weight);
        stringRedisTemplate.expire(KEY_SCORE + targetPlayerId, Duration.ofDays(14));
        boolean muted = false;
        if (score != null && score >= AUTO_MUTE_THRESHOLD) {
            long until = System.currentTimeMillis() + MUTE_DURATION.toMillis();
            stringRedisTemplate.opsForValue().set(KEY_MUTE + targetPlayerId, String.valueOf(until), MUTE_DURATION);
            muted = true;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("targetPlayerId", targetPlayerId);
        out.put("score", score == null ? 0 : score);
        out.put("threshold", AUTO_MUTE_THRESHOLD);
        out.put("autoMuted", muted);
        return out;
    }

    public Map<String, Object> block(long playerId, long targetId) {
        if (playerId <= 0 || targetId <= 0 || playerId == targetId) {
            return Map.of("ok", false, "error", "invalid_args");
        }
        stringRedisTemplate.opsForSet().add(KEY_BLOCK + playerId, String.valueOf(targetId));
        return Map.of("ok", true, "blocked", targetId);
    }

    public Map<String, Object> unblock(long playerId, long targetId) {
        stringRedisTemplate.opsForSet().remove(KEY_BLOCK + playerId, String.valueOf(targetId));
        return Map.of("ok", true, "unblocked", targetId);
    }

    public boolean isBlocked(long viewerId, long senderId) {
        return Boolean.TRUE.equals(
                stringRedisTemplate.opsForSet().isMember(KEY_BLOCK + viewerId, String.valueOf(senderId)));
    }

    public Map<String, Object> listBlocked(long playerId) {
        Set<String> raw = stringRedisTemplate.opsForSet().members(KEY_BLOCK + playerId);
        return Map.of("ok", true, "blocked", raw == null ? Set.of() : raw);
    }

    public Map<String, Object> scoreOf(long playerId) {
        String raw = stringRedisTemplate.opsForValue().get(KEY_SCORE + playerId);
        long score = 0L;
        if (raw != null) {
            try {
                score = Long.parseLong(raw);
            } catch (NumberFormatException ignored) {
                score = 0L;
            }
        }
        return Map.of("ok", true, "playerId", playerId, "score", score, "threshold", AUTO_MUTE_THRESHOLD);
    }

    private static int weightOf(String reason) {
        if (reason == null) {
            return 3;
        }
        return switch (reason.trim().toLowerCase()) {
            case "spam", "广告" -> 4;
            case "abuse", "辱骂", "harassment" -> 5;
            case "scam", "诈骗" -> 8;
            default -> 3;
        };
    }
}
