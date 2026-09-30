package cn.itcast.demo.mymmorpg.world.explore;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「探索-奖励」循环：大世界谜题/宝箱/收集品/景观 → 抽卡资源、培养素材、外观。
 * <p>
 * 权威在 Scene；发奖由下游 bag/mail 幂等兑现（本服务只产出 grantPlans）。
 */
@Service
public class ExplorationRewardLoopService {

    private final ConcurrentHashMap<String, ExplorationPoint> points = new ConcurrentHashMap<>();
    /** playerId → 已发现 pointId */
    private final ConcurrentHashMap<Long, Set<String>> discovered = new ConcurrentHashMap<>();
    /** playerId → 探索技能集合 */
    private final ConcurrentHashMap<Long, Set<String>> playerSkills = new ConcurrentHashMap<>();

    public void register(ExplorationPoint point) {
        points.put(point.pointId(), point);
    }

    public void grantExploreSkill(long playerId, String skillId) {
        if (playerId <= 0 || skillId == null || skillId.isBlank()) {
            return;
        }
        playerSkills.computeIfAbsent(playerId, id -> ConcurrentHashMap.newKeySet()).add(skillId.trim());
    }

    public Set<String> skillsOf(long playerId) {
        return Set.copyOf(playerSkills.getOrDefault(playerId, Set.of()));
    }

    /**
     * 发现并结算探索点；距离与技能门槛由服务端校验。
     */
    public Map<String, Object> discover(
            long playerId, String pointId,
            float px, float py, float pz, long nowMs) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (playerId <= 0 || pointId == null) {
            body.put("ok", false);
            body.put("error", "invalid_args");
            return body;
        }
        ExplorationPoint point = points.get(pointId);
        if (point == null) {
            body.put("ok", false);
            body.put("error", "point_not_found");
            return body;
        }
        Set<String> done = discovered.computeIfAbsent(playerId, id -> ConcurrentHashMap.newKeySet());
        if (point.oneShot() && done.contains(pointId)) {
            body.put("ok", false);
            body.put("error", "already_discovered");
            body.put("pointId", pointId);
            return body;
        }
        float dx = px - point.x();
        float dy = py - point.y();
        float dz = pz - point.z();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist > point.interactRadius()) {
            body.put("ok", false);
            body.put("error", "out_of_range");
            body.put("distance", dist);
            body.put("radius", point.interactRadius());
            return body;
        }
        String need = point.requiredExploreSkill();
        if (need != null && !need.isBlank()
                && !playerSkills.getOrDefault(playerId, Set.of()).contains(need)) {
            body.put("ok", false);
            body.put("error", "missing_explore_skill");
            body.put("requiredSkill", need);
            return body;
        }
        done.add(pointId);
        List<Map<String, Object>> grantPlans = new ArrayList<>(point.rewards());
        body.put("ok", true);
        body.put("pointId", pointId);
        body.put("kind", point.kind().name());
        body.put("grantPlans", grantPlans);
        body.put("discoveredCount", done.size());
        body.put("exploreScore", done.size() * 10);
        body.put("settledAtMs", nowMs);
        body.put("idempotencyKey", "explore:" + playerId + ":" + pointId);
        return body;
    }

    public Map<String, Object> progress(long playerId) {
        Set<String> done = discovered.getOrDefault(playerId, Set.of());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("discoveredCount", done.size());
        body.put("totalPoints", points.size());
        body.put("exploreScore", done.size() * 10);
        body.put("skills", List.copyOf(skillsOf(playerId)));
        body.put("discovered", List.copyOf(done));
        return body;
    }

    public List<ExplorationPoint> listPoints() {
        return List.copyOf(points.values());
    }

    public void clearPlayer(long playerId) {
        discovered.remove(playerId);
        playerSkills.remove(playerId);
    }
}
