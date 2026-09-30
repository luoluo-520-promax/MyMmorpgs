package cn.itcast.demo.mymmorpg.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 社交成就与荣誉：称号 / 头像框，按计数器阈值自动授予。
 */
@Service
public class SocialAchievementService {

    private static final String KEY_COUNTER = "social:achieve:counter:";
    private static final String KEY_UNLOCKED = "social:achieve:unlocked:";
    private static final String KEY_TITLE = "social:title:";
    private static final String KEY_FRAME = "social:frame:";
    private static final String KEY_EQUIP_TITLE = "social:equip:title:";
    private static final String KEY_EQUIP_FRAME = "social:equip:frame:";

    public record AchievementDef(String id, String counter, long threshold, String title, String frame, String name) {
    }

    private static final List<AchievementDef> DEFS = List.of(
            new AchievementDef("friends_10", "friend_count", 10, "社交新星", "frame_social_1", "拥有10个好友"),
            new AchievementDef("friends_50", "friend_count", 50, "人脉达人", "frame_social_2", "拥有50个好友"),
            new AchievementDef("assist_10", "assist_settle", 10, "热心助战", "frame_assist_1", "助战结算10次"),
            new AchievementDef("assist_100", "assist_settle", 100, "助战大师", "frame_assist_2", "助战结算100次"),
            new AchievementDef("home_visit_20", "home_visit", 20, "家园访客", "frame_home_1", "拜访家园20次"),
            new AchievementDef("party_5", "party_formed", 5, "组队先锋", "frame_party_1", "组队5次")
    );

    private final StringRedisTemplate stringRedisTemplate;

    public SocialAchievementService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public Map<String, Object> onCounter(long playerId, String counter, long absoluteValue) {
        if (playerId <= 0 || counter == null || counter.isBlank()) {
            return Map.of("ok", false, "error", "invalid_args");
        }
        stringRedisTemplate.opsForHash().put(KEY_COUNTER + playerId, counter, String.valueOf(Math.max(0, absoluteValue)));
        List<String> newly = new ArrayList<>();
        for (AchievementDef def : DEFS) {
            if (!def.counter().equals(counter)) {
                continue;
            }
            if (absoluteValue >= def.threshold()) {
                Long added = stringRedisTemplate.opsForSet().add(KEY_UNLOCKED + playerId, def.id());
                if (added != null && added > 0) {
                    stringRedisTemplate.opsForSet().add(KEY_TITLE + playerId, def.title());
                    stringRedisTemplate.opsForSet().add(KEY_FRAME + playerId, def.frame());
                    newly.add(def.id());
                }
            }
        }
        return Map.of("ok", true, "counter", counter, "value", absoluteValue, "newlyUnlocked", newly);
    }

    public Map<String, Object> increment(long playerId, String counter, long delta) {
        Long v = stringRedisTemplate.opsForHash().increment(KEY_COUNTER + playerId, counter, Math.max(1, delta));
        return onCounter(playerId, counter, v == null ? 0L : v);
    }

    public Map<String, Object> profile(long playerId) {
        Map<Object, Object> counters = stringRedisTemplate.opsForHash().entries(KEY_COUNTER + playerId);
        Set<String> unlocked = stringRedisTemplate.opsForSet().members(KEY_UNLOCKED + playerId);
        Set<String> titles = stringRedisTemplate.opsForSet().members(KEY_TITLE + playerId);
        Set<String> frames = stringRedisTemplate.opsForSet().members(KEY_FRAME + playerId);
        String equipTitle = stringRedisTemplate.opsForValue().get(KEY_EQUIP_TITLE + playerId);
        String equipFrame = stringRedisTemplate.opsForValue().get(KEY_EQUIP_FRAME + playerId);
        List<Map<String, Object>> catalog = new ArrayList<>();
        for (AchievementDef def : DEFS) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", def.id());
            row.put("name", def.name());
            row.put("counter", def.counter());
            row.put("threshold", def.threshold());
            row.put("title", def.title());
            row.put("frame", def.frame());
            row.put("unlocked", unlocked != null && unlocked.contains(def.id()));
            catalog.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("playerId", playerId);
        out.put("counters", counters);
        out.put("unlocked", unlocked == null ? List.of() : List.copyOf(unlocked));
        out.put("titles", titles == null ? List.of() : List.copyOf(titles));
        out.put("frames", frames == null ? List.of() : List.copyOf(frames));
        out.put("equippedTitle", equipTitle);
        out.put("equippedFrame", equipFrame);
        out.put("catalog", catalog);
        return out;
    }

    public Map<String, Object> equipTitle(long playerId, String title) {
        Set<String> titles = stringRedisTemplate.opsForSet().members(KEY_TITLE + playerId);
        if (titles == null || !titles.contains(title)) {
            return Map.of("ok", false, "error", "title_not_owned");
        }
        stringRedisTemplate.opsForValue().set(KEY_EQUIP_TITLE + playerId, title);
        return Map.of("ok", true, "equippedTitle", title);
    }

    public Map<String, Object> equipFrame(long playerId, String frame) {
        Set<String> frames = stringRedisTemplate.opsForSet().members(KEY_FRAME + playerId);
        if (frames == null || !frames.contains(frame)) {
            return Map.of("ok", false, "error", "frame_not_owned");
        }
        stringRedisTemplate.opsForValue().set(KEY_EQUIP_FRAME + playerId, frame);
        return Map.of("ok", true, "equippedFrame", frame);
    }

    public void applySocialEvent(String type, long actorId, long targetId) {
        if (type == null || actorId <= 0) {
            return;
        }
        switch (type.toUpperCase()) {
            case "ASSIST_SETTLED" -> {
                increment(actorId, "assist_settle", 1);
                if (targetId > 0) {
                    increment(targetId, "assist_settle", 1);
                }
            }
            case "HOME_VISITED" -> increment(actorId, "home_visit", 1);
            case "PARTY_FORMED" -> increment(actorId, "party_formed", 1);
            case "FRIEND_ADDED" -> {
                // friend_count 由图谱服务写绝对值
            }
            default -> {
                // ignore
            }
        }
    }
}
