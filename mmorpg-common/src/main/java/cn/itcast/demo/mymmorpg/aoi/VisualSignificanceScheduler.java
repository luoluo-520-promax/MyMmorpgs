package cn.itcast.demo.mymmorpg.aoi;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务端驱动的视觉重要性调度：高密度同屏时下发 VISUAL_DEGRADE 指令。
 */
@Service
public class VisualSignificanceScheduler {

    public static final int ENTITY_THRESHOLD = 50;
    public static final int DEGRADE_COUNT = 20;

    public record EntitySignificance(
            long entityId,
            float distance,
            boolean inCombat,
            float hpPercent,
            boolean locked,
            float score) {
    }

    /**
     * @param entities 每个实体：id, distance, inCombat, hpPercent, locked
     */
    public Map<String, Object> computeDegrade(
            long viewerId, float viewerX, float viewerZ,
            List<EntitySignificance> entities) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("viewerId", viewerId);
        if (entities == null || entities.size() <= ENTITY_THRESHOLD) {
            body.put("visualDegrade", false);
            body.put("entityCount", entities == null ? 0 : entities.size());
            return body;
        }
        List<EntitySignificance> sorted = new ArrayList<>(entities);
        sorted.sort(Comparator
                .comparing((EntitySignificance e) -> e.inCombat() ? 0 : 1)
                .thenComparing(e -> e.locked() ? 0 : 1)
                .thenComparing(EntitySignificance::hpPercent, Comparator.reverseOrder())
                .thenComparing(EntitySignificance::distance, Comparator.reverseOrder()));

        List<Map<String, Object>> degradeList = new ArrayList<>();
        int count = 0;
        for (EntitySignificance e : sorted) {
            if (e.inCombat() || e.locked()) {
                continue;
            }
            if (count >= DEGRADE_COUNT) {
                break;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("entityId", e.entityId());
            row.put("lod", "LOW_POLY_SILHOUETTE");
            row.put("showNameplateOnly", false);
            row.put("distance", Math.round(e.distance() * 10f) / 10f);
            degradeList.add(row);
            count++;
        }
        body.put("visualDegrade", true);
        body.put("event", "VISUAL_DEGRADE");
        body.put("entityCount", entities.size());
        body.put("degradeCount", degradeList.size());
        body.put("degradedEntities", degradeList);
        body.put("clientHint", "远距离非战斗玩家已降级为低模剪影");
        return body;
    }

    public float significanceScore(boolean inCombat, float hpPercent, boolean locked, float distance) {
        float score = 0f;
        if (inCombat) {
            score += 100f;
        }
        if (locked) {
            score += 50f;
        }
        score += hpPercent * 30f;
        score -= distance * 0.1f;
        return score;
    }
}
