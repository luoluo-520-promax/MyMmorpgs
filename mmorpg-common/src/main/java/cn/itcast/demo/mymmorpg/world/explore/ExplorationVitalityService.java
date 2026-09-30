package cn.itcast.demo.mymmorpg.world.explore;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 探索活力值：每日随机刷新调查点，附带叙事纸条与小奖励，强化探索过程惊喜感。
 */
@Service
public class ExplorationVitalityService {

    public record SurveyPoint(
            String pointId,
            String regionId,
            String narrativeNote,
            float x, float y, float z,
            String rewardType,
            int reputationBonus) {
    }

    private final RegionProgressService regionProgress;
    private final ConcurrentHashMap<Long, List<SurveyPoint>> dailyPoints = new ConcurrentHashMap<>();
    private volatile long lastRefreshDay = -1;

    public ExplorationVitalityService() {
        this(new RegionProgressService());
    }

    public ExplorationVitalityService(RegionProgressService regionProgress) {
        this.regionProgress = regionProgress == null ? new RegionProgressService() : regionProgress;
    }

    public Map<String, Object> refreshDaily(long playerId, String regionId, long nowMs) {
        long day = nowMs / 86_400_000L;
        if (day != lastRefreshDay) {
            dailyPoints.clear();
            lastRefreshDay = day;
        }
        List<SurveyPoint> points = dailyPoints.computeIfAbsent(playerId, id -> new ArrayList<>());
        if (points.isEmpty()) {
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            int count = 2 + rng.nextInt(3);
            String[] notes = {
                    "有人在此留下脚印，指向北方的裂隙……",
                    "破损的日记残页：「月圆之夜，石门会自行开启」",
                    "巢穴痕迹新鲜，小心埋伏"
            };
            for (int i = 0; i < count; i++) {
                points.add(new SurveyPoint(
                        "survey-" + regionId + "-" + day + "-" + i,
                        regionId,
                        notes[rng.nextInt(notes.length)],
                        100f + rng.nextFloat() * 200f,
                        rng.nextFloat() * 10f,
                        100f + rng.nextFloat() * 200f,
                        "RELIC_FRAGMENT",
                        5 + rng.nextInt(6)));
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("regionId", regionId);
        body.put("vitalityDay", day);
        body.put("surveyPoints", points.stream().map(p -> Map.of(
                "pointId", p.pointId(),
                "narrativeNote", p.narrativeNote(),
                "x", p.x(), "y", p.y(), "z", p.z(),
                "rewardType", p.rewardType(),
                "reputationBonus", p.reputationBonus())).toList());
        return body;
    }

    public Map<String, Object> completeSurvey(long playerId, String pointId, long nowMs) {
        for (List<SurveyPoint> list : dailyPoints.values()) {
            for (SurveyPoint p : list) {
                if (p.pointId().equals(pointId)) {
                    list.remove(p);
                    regionProgress.markCollectible(playerId, p.regionId(), "vitality-" + pointId);
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("ok", true);
                    body.put("pointId", pointId);
                    body.put("rewardType", p.rewardType());
                    body.put("reputationBonus", p.reputationBonus());
                    body.put("narrativeNote", p.narrativeNote());
                    body.put("grantPlans", List.of(Map.of(
                            "itemId", "relic_fragment_random",
                            "count", 1 + ThreadLocalRandom.current().nextInt(2),
                            "idempotencyKey", "vitality:" + playerId + ":" + pointId)));
                    return body;
                }
            }
        }
        return Map.of("ok", false, "error", "survey_not_found");
    }
}
