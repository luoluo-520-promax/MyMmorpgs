package cn.itcast.demo.mymmorpg.ai.recommend;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家画像：离线标签 + 在线近期行为热更新。
 */
public final class PlayerProfileService {

    public record BehaviorEvent(String category, String itemId, double weight, long atMs) {
    }

    public record PlayerProfile(
            long playerId,
            Map<String, Double> tags,
            List<BehaviorEvent> recent,
            String skillTier,
            String payTendency,
            String socialTendency,
            long updatedAtMs) {

        public PlayerProfile {
            tags = tags == null ? Map.of() : Map.copyOf(tags);
            recent = recent == null ? List.of() : List.copyOf(recent);
        }
    }

    private static final int MAX_RECENT = 50;
    private final ConcurrentHashMap<Long, MutableProfile> store = new ConcurrentHashMap<>();

    public void record(long playerId, String category, String itemId, double weight) {
        if (playerId <= 0) {
            return;
        }
        MutableProfile p = store.computeIfAbsent(playerId, MutableProfile::new);
        synchronized (p) {
            p.recent.add(new BehaviorEvent(
                    category == null ? "unknown" : category,
                    itemId == null ? "" : itemId,
                    weight,
                    System.currentTimeMillis()));
            while (p.recent.size() > MAX_RECENT) {
                p.recent.remove(0);
            }
            String tag = (category == null ? "act" : category).toLowerCase(Locale.ROOT);
            p.tags.merge(tag, Math.max(0.01, weight), (a, b) -> Math.min(1.0, a * 0.85 + b * 0.15));
            p.updatedAtMs = System.currentTimeMillis();
            recomputeTendencies(p);
        }
    }

    public PlayerProfile get(long playerId) {
        MutableProfile p = store.get(playerId);
        if (p == null) {
            return new PlayerProfile(playerId, Map.of(), List.of(), "MID", "LOW", "LOW", System.currentTimeMillis());
        }
        synchronized (p) {
            return new PlayerProfile(playerId, new LinkedHashMap<>(p.tags), new ArrayList<>(p.recent),
                    p.skillTier, p.payTendency, p.socialTendency, p.updatedAtMs);
        }
    }

    public Map<String, Object> toMap(long playerId) {
        PlayerProfile p = get(playerId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("playerId", p.playerId());
        m.put("tags", p.tags());
        m.put("recentCount", p.recent().size());
        m.put("skillTier", p.skillTier());
        m.put("payTendency", p.payTendency());
        m.put("socialTendency", p.socialTendency());
        m.put("updatedAtMs", p.updatedAtMs());
        return m;
    }

    public void setOfflineTags(long playerId, Map<String, Double> offlineTags,
                               String skillTier, String payTendency, String socialTendency) {
        MutableProfile p = store.computeIfAbsent(playerId, MutableProfile::new);
        synchronized (p) {
            if (offlineTags != null) {
                offlineTags.forEach((k, v) -> p.tags.merge(k, v, (a, b) -> Math.min(1.0, (a + b) / 2)));
            }
            if (skillTier != null) {
                p.skillTier = skillTier;
            }
            if (payTendency != null) {
                p.payTendency = payTendency;
            }
            if (socialTendency != null) {
                p.socialTendency = socialTendency;
            }
            p.updatedAtMs = System.currentTimeMillis();
        }
    }

    private static void recomputeTendencies(MutableProfile p) {
        double combat = p.tags.getOrDefault("combat", 0.0) + p.tags.getOrDefault("battle", 0.0);
        double social = p.tags.getOrDefault("social", 0.0) + p.tags.getOrDefault("gift", 0.0);
        double pay = p.tags.getOrDefault("shop", 0.0) + p.tags.getOrDefault("gacha", 0.0);
        p.skillTier = combat >= 0.7 ? "HIGH" : combat >= 0.35 ? "MID" : "LOW";
        p.socialTendency = social >= 0.5 ? "HIGH" : social >= 0.2 ? "MID" : "LOW";
        p.payTendency = pay >= 0.5 ? "HIGH" : pay >= 0.2 ? "MID" : "LOW";
    }

    private static final class MutableProfile {
        final long playerId;
        final Map<String, Double> tags = new LinkedHashMap<>();
        final List<BehaviorEvent> recent = new ArrayList<>();
        String skillTier = "MID";
        String payTendency = "LOW";
        String socialTendency = "LOW";
        long updatedAtMs = System.currentTimeMillis();

        MutableProfile(long playerId) {
            this.playerId = playerId;
        }
    }
}
