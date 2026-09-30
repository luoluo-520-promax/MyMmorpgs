package cn.itcast.demo.mymmorpg.world.endgame;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 不对称竞技：躲猫猫（HIDE_SEEK）+ 载具/钩锁竞速（Racing ZSET 排名）。
 */
@Service
public class AsymmetricPlayService {

    public static final String MODE_HIDE_SEEK = "HIDE_SEEK";
    public static final String MODE_RACING = "RACING";

    private final PropTransformService props;
    private final ConcurrentHashMap<String, List<Long>> matchRooms = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> roles = new ConcurrentHashMap<>();
    /** raceId → playerId → finishMs（ZSET 语义） */
    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, Long>> raceZset = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<String>> checkpoints = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, Integer>> checkpointIndex =
            new ConcurrentHashMap<>();

    public AsymmetricPlayService() {
        this(new PropTransformService());
    }

    public AsymmetricPlayService(PropTransformService props) {
        this.props = props == null ? new PropTransformService() : props;
    }

    public PropTransformService props() {
        return props;
    }

    public Map<String, Object> startHideSeek(String matchId, List<Long> hiders, List<Long> seekers) {
        List<Long> all = new ArrayList<>();
        if (hiders != null) {
            all.addAll(hiders);
            for (Long id : hiders) {
                roles.put(id, "HIDER");
                props.transform(id, "barrel");
            }
        }
        if (seekers != null) {
            all.addAll(seekers);
            for (Long id : seekers) {
                roles.put(id, "SEEKER");
            }
        }
        matchRooms.put(matchId, all);
        return Map.of("ok", true, "gameMode", MODE_HIDE_SEEK, "matchId", matchId,
                "hiders", hiders == null ? List.of() : hiders,
                "seekers", seekers == null ? List.of() : seekers);
    }

    public Map<String, Object> scannerSkill(long seekerId, float x, float z, float radiusM) {
        if (!"SEEKER".equals(roles.get(seekerId))) {
            return Map.of("ok", false, "error", "not_seeker");
        }
        List<Map<String, Object>> hits = new ArrayList<>();
        for (Map.Entry<Long, String> e : roles.entrySet()) {
            if (!"HIDER".equals(e.getValue())) {
                continue;
            }
            if (props.isProp(e.getKey())) {
                hits.add(Map.of(
                        "playerId", e.getKey(),
                        "propId", props.propId(e.getKey()),
                        "highlight", true,
                        "inRadius", true));
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("skill", "SCANNER_SKILL");
        body.put("seekerId", seekerId);
        body.put("radiusM", radiusM);
        body.put("origin", Map.of("x", x, "z", z));
        body.put("hits", hits);
        return body;
    }

    public Map<String, Object> startRace(String raceId, List<String> cps) {
        checkpoints.put(raceId, cps == null ? List.of("cp1", "cp2", "finish") : List.copyOf(cps));
        raceZset.put(raceId, new ConcurrentHashMap<>());
        checkpointIndex.put(raceId, new ConcurrentHashMap<>());
        return Map.of("ok", true, "gameMode", MODE_RACING, "raceId", raceId,
                "checkpoints", checkpoints.get(raceId));
    }

    public Map<String, Object> passCheckpoint(String raceId, long playerId, String cpId, long nowMs) {
        List<String> cps = checkpoints.get(raceId);
        if (cps == null) {
            return Map.of("ok", false, "error", "race_not_found");
        }
        ConcurrentHashMap<Long, Integer> idxMap = checkpointIndex.get(raceId);
        int expect = idxMap.getOrDefault(playerId, 0);
        if (expect >= cps.size() || !cps.get(expect).equals(cpId)) {
            return Map.of("ok", false, "error", "checkpoint_order");
        }
        idxMap.put(playerId, expect + 1);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("checkpoint", cpId);
        body.put("index", expect);
        body.put("atMs", nowMs);
        if (expect + 1 >= cps.size()) {
            raceZset.get(raceId).put(playerId, nowMs);
            body.put("finished", true);
            body.put("finishMs", nowMs);
        } else {
            body.put("finished", false);
        }
        return body;
    }

    public Map<String, Object> raceRanking(String raceId) {
        ConcurrentHashMap<Long, Long> zset = raceZset.get(raceId);
        if (zset == null) {
            return Map.of("ok", false, "error", "race_not_found");
        }
        List<Map<String, Object>> ranks = zset.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(e -> Map.<String, Object>of("playerId", e.getKey(), "finishMs", e.getValue()))
                .toList();
        List<Map<String, Object>> rewards = new ArrayList<>();
        for (int i = 0; i < ranks.size() && i < 3; i++) {
            rewards.add(Map.of("playerId", ranks.get(i).get("playerId"),
                    "title", "限时称号-竞速第" + (i + 1), "ttlHours", 72));
        }
        return Map.of("ok", true, "raceId", raceId, "zset", ranks, "titles", rewards);
    }
}
