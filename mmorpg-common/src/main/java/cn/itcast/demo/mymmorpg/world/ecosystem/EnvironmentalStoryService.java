package cn.itcast.demo.mymmorpg.world.ecosystem;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 环境叙事：通过场景物品摆放与状态无声讲述故事。
 */
@Service
public class EnvironmentalStoryService {

    public enum PropState {
        INTACT, DISTURBED, BROKEN, OFFERING_PLACED, FRESH, WEATHERED
    }

    public record StoryProp(
            String propId,
            String regionId,
            String title,
            String silentNarrative,
            float x, float y, float z,
            PropState defaultState,
            List<String> clueTags) {

        public StoryProp {
            defaultState = defaultState == null ? PropState.INTACT : defaultState;
            clueTags = clueTags == null ? List.of() : List.copyOf(clueTags);
        }
    }

    private final ConcurrentHashMap<String, StoryProp> props = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Set<String>> inspected = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PropState> worldState = new ConcurrentHashMap<>();

    public void register(StoryProp prop) {
        if (prop != null && prop.propId() != null) {
            props.put(prop.propId(), prop);
            worldState.putIfAbsent(prop.propId(), prop.defaultState());
        }
    }

    public Map<String, Object> inspect(long playerId, String propId) {
        StoryProp prop = props.get(propId);
        if (prop == null) {
            return Map.of("ok", false, "error", "prop_not_found");
        }
        inspected.computeIfAbsent(playerId, id -> ConcurrentHashMap.newKeySet()).add(propId);
        PropState state = worldState.getOrDefault(propId, prop.defaultState());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("propId", propId);
        body.put("title", prop.title());
        body.put("state", state.name());
        body.put("silentNarrative", narrativeForState(prop, state));
        body.put("clueTags", prop.clueTags());
        body.put("firstInspect", !inspected.get(playerId).contains(propId + ":seen"));
        inspected.get(playerId).add(propId + ":seen");
        return body;
    }

    public Map<String, Object> setPropState(String propId, PropState state) {
        StoryProp prop = props.get(propId);
        if (prop == null) {
            return Map.of("ok", false, "error", "prop_not_found");
        }
        worldState.put(propId, state);
        return Map.of("ok", true, "propId", propId, "state", state.name(),
                "narrative", narrativeForState(prop, state));
    }

    public Map<String, Object> regionClues(String regionId) {
        List<Map<String, Object>> rows = props.values().stream()
                .filter(p -> p.regionId().equals(regionId))
                .map(p -> {
                    PropState st = worldState.getOrDefault(p.propId(), p.defaultState());
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("propId", p.propId());
                    row.put("title", p.title());
                    row.put("state", st.name());
                    row.put("x", p.x());
                    row.put("y", p.y());
                    row.put("z", p.z());
                    row.put("clueTags", p.clueTags());
                    return row;
                })
                .toList();
        return Map.of("ok", true, "regionId", regionId, "props", rows);
    }

    private static String narrativeForState(StoryProp prop, PropState state) {
        return switch (state) {
            case INTACT -> prop.silentNarrative();
            case DISTURBED -> "地面留有凌乱脚印，似乎有人匆忙离开。";
            case BROKEN -> "残破的器物诉说着一场未完的冲突。";
            case OFFERING_PLACED -> "新鲜的花束表明近期仍有人来此悼念。";
            case FRESH -> "一切如新，仿佛时间在此停滞。";
            case WEATHERED -> "风雨侵蚀的痕迹，默默记录着岁月的流逝。";
        };
    }
}
