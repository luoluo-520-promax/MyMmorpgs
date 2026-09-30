package cn.itcast.demo.mymmorpg.ai.recommend;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 个性化推荐：协同过滤（简化物品共现）+ 内容属性打分。
 */
public final class RecommendationEngine {

    public record CatalogItem(
            String itemId,
            String category,
            Map<String, Double> attributes,
            List<String> tags) {

        public CatalogItem {
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
            tags = tags == null ? List.of() : List.copyOf(tags);
        }
    }

    public record ScoredItem(String itemId, String category, double score, String reason) {
    }

    private final ConcurrentHashMap<String, CatalogItem> catalog = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Double>> coOccur = new ConcurrentHashMap<>();
    private final PlayerProfileService profiles;

    public RecommendationEngine(PlayerProfileService profiles) {
        this.profiles = profiles == null ? new PlayerProfileService() : profiles;
    }

    public RecommendationEngine() {
        this(new PlayerProfileService());
    }

    public void upsertItem(CatalogItem item) {
        if (item != null && item.itemId() != null) {
            catalog.put(item.itemId(), item);
        }
    }

    public void recordInteraction(long playerId, String itemId, String category) {
        profiles.record(playerId, category == null ? "item" : category, itemId, 1.0);
        PlayerProfileService.PlayerProfile p = profiles.get(playerId);
        for (PlayerProfileService.BehaviorEvent e : p.recent()) {
            if (e.itemId() == null || e.itemId().isBlank() || e.itemId().equals(itemId)) {
                continue;
            }
            bump(itemId, e.itemId());
            bump(e.itemId(), itemId);
        }
    }

    public List<ScoredItem> recommend(long playerId, String category, int topN) {
        int n = Math.max(1, Math.min(50, topN));
        PlayerProfileService.PlayerProfile profile = profiles.get(playerId);
        List<ScoredItem> scored = new ArrayList<>();
        for (CatalogItem item : catalog.values()) {
            if (category != null && !category.isBlank()
                    && !category.equalsIgnoreCase(item.category())) {
                continue;
            }
            double content = contentScore(profile, item);
            double cf = collaborativeScore(profile, item.itemId());
            double score = 0.55 * content + 0.45 * cf;
            String reason = content >= cf ? "content_match" : "similar_players";
            if ("HIGH".equals(profile.payTendency()) && "shop".equalsIgnoreCase(item.category())) {
                score *= 1.08;
                reason = "pay_affinity";
            }
            scored.add(new ScoredItem(item.itemId(), item.category(), round3(score), reason));
        }
        scored.sort(Comparator.comparingDouble(ScoredItem::score).reversed());
        if (scored.size() > n) {
            return List.copyOf(scored.subList(0, n));
        }
        return List.copyOf(scored);
    }

    public Map<String, Object> recommendPayload(long playerId, String category, int topN) {
        List<ScoredItem> items = recommend(playerId, category, topN);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ScoredItem s : items) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("itemId", s.itemId());
            row.put("category", s.category());
            row.put("score", s.score());
            row.put("reason", s.reason());
            rows.add(row);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("category", category);
        body.put("items", rows);
        body.put("profile", profiles.toMap(playerId));
        return body;
    }

    public PlayerProfileService profiles() {
        return profiles;
    }

    private double contentScore(PlayerProfileService.PlayerProfile profile, CatalogItem item) {
        double sum = 0;
        double w = 0;
        for (String tag : item.tags()) {
            double tw = profile.tags().getOrDefault(tag.toLowerCase(Locale.ROOT), 0.1);
            sum += tw;
            w += 1;
        }
        for (Map.Entry<String, Double> e : item.attributes().entrySet()) {
            double pref = profile.tags().getOrDefault(e.getKey().toLowerCase(Locale.ROOT), 0.2);
            sum += pref * e.getValue();
            w += 1;
        }
        return w <= 0 ? 0.3 : Math.min(1.0, sum / w);
    }

    private double collaborativeScore(PlayerProfileService.PlayerProfile profile, String itemId) {
        ConcurrentHashMap<String, Double> neighbors = coOccur.get(itemId);
        if (neighbors == null || neighbors.isEmpty()) {
            return 0.2;
        }
        double score = 0;
        int hits = 0;
        for (PlayerProfileService.BehaviorEvent e : profile.recent()) {
            Double c = neighbors.get(e.itemId());
            if (c != null) {
                score += c;
                hits++;
            }
        }
        if (hits == 0) {
            return 0.2;
        }
        return Math.min(1.0, score / (hits * 5.0));
    }

    private void bump(String a, String b) {
        coOccur.computeIfAbsent(a, k -> new ConcurrentHashMap<>())
                .merge(b, 1.0, Double::sum);
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
