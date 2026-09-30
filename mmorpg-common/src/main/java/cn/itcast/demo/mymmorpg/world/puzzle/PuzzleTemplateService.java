package cn.itcast.demo.mymmorpg.world.puzzle;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 解谜库：内置模板 + JSON 配置实例化，策划无需写死代码。
 */
@Service
public class PuzzleTemplateService {

    public enum PuzzleType {
        SHOOTING_TARGET,
        PRESSURE_PLATE,
        ELEMENT_OBELISK,
        FOLLOW_SPIRIT,
        TILE_PUZZLE,
        MUSIC_RUNE,
        TORCH_SEQUENCE,
        WEIGHT_BALANCE,
        MIRROR_REFLECT,
        TIMED_SWITCH
    }

    public record TemplateMeta(
            PuzzleType type,
            String title,
            String description,
            List<String> requiredParams,
            Map<String, Object> defaultConfig) {
    }

    public record PuzzleInstance(
            String puzzleId,
            PuzzleType type,
            int worldId,
            int sceneId,
            float x, float y, float z,
            Map<String, Object> config,
            List<Map<String, Object>> rewards) {
    }

    private final ConcurrentHashMap<PuzzleType, TemplateMeta> templates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PuzzleInstance> instances = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, Integer>> progress =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, java.util.Set<String>> solved = new ConcurrentHashMap<>();

    public PuzzleTemplateService() {
        seedBuiltinTemplates();
    }

    public List<Map<String, Object>> listTemplates() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (TemplateMeta t : templates.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", t.type().name());
            m.put("title", t.title());
            m.put("description", t.description());
            m.put("requiredParams", t.requiredParams());
            m.put("defaultConfig", t.defaultConfig());
            out.add(m);
        }
        return out;
    }

    /**
     * 由策划 JSON 生成谜题实例。
     */
    public Map<String, Object> instantiateFromJson(Map<String, Object> json) {
        if (json == null || json.get("puzzleId") == null || json.get("type") == null) {
            return Map.of("ok", false, "error", "puzzleId_and_type_required");
        }
        PuzzleType type;
        try {
            type = PuzzleType.valueOf(String.valueOf(json.get("type")).trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return Map.of("ok", false, "error", "unknown_type", "type", String.valueOf(json.get("type")));
        }
        TemplateMeta meta = templates.get(type);
        if (meta == null) {
            return Map.of("ok", false, "error", "template_missing");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> cfgIn = json.get("config") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        Map<String, Object> cfg = new LinkedHashMap<>(meta.defaultConfig());
        cfg.putAll(cfgIn);
        for (String need : meta.requiredParams()) {
            if (!cfg.containsKey(need) || cfg.get(need) == null) {
                return Map.of("ok", false, "error", "missing_param", "param", need);
            }
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rewards = json.get("rewards") instanceof List<?> list
                ? list.stream().filter(Map.class::isInstance).map(v -> (Map<String, Object>) v).toList()
                : List.of(Map.of("itemId", "primogem", "count", 5));
        PuzzleInstance inst = new PuzzleInstance(
                String.valueOf(json.get("puzzleId")),
                type,
                asInt(json.get("worldId"), 1),
                asInt(json.get("sceneId"), 1),
                asFloat(json.get("x")),
                asFloat(json.get("y")),
                asFloat(json.get("z")),
                cfg,
                rewards);
        instances.put(inst.puzzleId(), inst);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("puzzle", toView(inst));
        return body;
    }

    public Map<String, Object> advance(long playerId, String puzzleId, Map<String, Object> input) {
        PuzzleInstance inst = instances.get(puzzleId);
        if (inst == null) {
            return Map.of("ok", false, "error", "puzzle_not_found");
        }
        if (solved.getOrDefault(playerId, java.util.Set.of()).contains(puzzleId)) {
            return Map.of("ok", false, "error", "already_solved", "puzzleId", puzzleId);
        }
        int need = asInt(inst.config().get("steps"), stepsFor(inst.type()));
        int cur = progress.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>())
                .merge(puzzleId, 1, Integer::sum);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("puzzleId", puzzleId);
        body.put("type", inst.type().name());
        body.put("step", cur);
        body.put("need", need);
        body.put("input", input == null ? Map.of() : input);
        if (cur >= need) {
            solved.computeIfAbsent(playerId, id -> ConcurrentHashMap.newKeySet()).add(puzzleId);
            body.put("solved", true);
            body.put("grantPlans", inst.rewards());
            body.put("idempotencyKey", "puzzle:" + playerId + ":" + puzzleId);
        } else {
            body.put("solved", false);
        }
        return body;
    }

    public Map<String, Object> progress(long playerId) {
        return Map.of(
                "ok", true,
                "playerId", playerId,
                "solvedCount", solved.getOrDefault(playerId, java.util.Set.of()).size(),
                "totalInstances", instances.size(),
                "solved", List.copyOf(solved.getOrDefault(playerId, java.util.Set.of())),
                "templates", templates.size());
    }

    public List<Map<String, Object>> listInstances() {
        return instances.values().stream().map(this::toView).toList();
    }

    private void seedBuiltinTemplates() {
        put(PuzzleType.SHOOTING_TARGET, "射击靶", "用远程攻击命中悬浮靶激活机关",
                List.of("targetCount"), Map.of("targetCount", 3, "steps", 3));
        put(PuzzleType.PRESSURE_PLATE, "压力板", "站立/放置重物触发联动门",
                List.of("plateCount"), Map.of("plateCount", 2, "steps", 2));
        put(PuzzleType.ELEMENT_OBELISK, "元素方碑", "按顺序注入对应元素",
                List.of("sequence"), Map.of("sequence", List.of("FIRE", "CRYO", "ANEMO"), "steps", 3));
        put(PuzzleType.FOLLOW_SPIRIT, "跟随仙灵", "护送光点抵达巢穴",
                List.of("waypoints"), Map.of("waypoints", 4, "steps", 4));
        put(PuzzleType.TILE_PUZZLE, "地面拼图", "踩亮正确地砖序列",
                List.of("pattern"), Map.of("pattern", "NWES", "steps", 4));
        put(PuzzleType.MUSIC_RUNE, "音乐符石", "按提示音序敲击符石",
                List.of("notes"), Map.of("notes", List.of("Do", "Mi", "Sol"), "steps", 3));
        put(PuzzleType.TORCH_SEQUENCE, "火把序列", "按壁画顺序点燃火把",
                List.of("order"), Map.of("order", List.of(1, 3, 2, 4), "steps", 4));
        put(PuzzleType.WEIGHT_BALANCE, "砝码天平", "两侧重量相等解锁",
                List.of("targetWeight"), Map.of("targetWeight", 10, "steps", 1));
        put(PuzzleType.MIRROR_REFLECT, "镜面折光", "旋转镜子将光束导入核心",
                List.of("mirrors"), Map.of("mirrors", 3, "steps", 3));
        put(PuzzleType.TIMED_SWITCH, "限时连动开关", "时限内按顺序扳动开关",
                List.of("windowMs"), Map.of("windowMs", 15000, "steps", 3));
    }

    private void put(PuzzleType type, String title, String desc,
                     List<String> required, Map<String, Object> defaults) {
        templates.put(type, new TemplateMeta(type, title, desc, required, defaults));
    }

    private static int stepsFor(PuzzleType type) {
        return switch (type) {
            case WEIGHT_BALANCE -> 1;
            case PRESSURE_PLATE, MUSIC_RUNE, SHOOTING_TARGET, ELEMENT_OBELISK,
                    TIMED_SWITCH, MIRROR_REFLECT -> 3;
            default -> 4;
        };
    }

    private Map<String, Object> toView(PuzzleInstance p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("puzzleId", p.puzzleId());
        m.put("type", p.type().name());
        m.put("worldId", p.worldId());
        m.put("sceneId", p.sceneId());
        m.put("x", p.x());
        m.put("y", p.y());
        m.put("z", p.z());
        m.put("config", p.config());
        m.put("rewards", p.rewards());
        return m;
    }

    private static int asInt(Object v, int dft) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return dft;
        }
    }

    private static float asFloat(Object v) {
        if (v instanceof Number n) {
            return n.floatValue();
        }
        try {
            return Float.parseFloat(String.valueOf(v));
        } catch (Exception e) {
            return 0f;
        }
    }
}
