package cn.itcast.demo.mymmorpg.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 配置变更 Diff：对比导入前后 JSON，输出增删改键路径。
 */
@Service
public class ConfigDiffService {

    private final ObjectMapper objectMapper;

    public ConfigDiffService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> diffJson(String beforeJson, String afterJson) {
        Map<String, Object> before = parse(beforeJson);
        Map<String, Object> after = parse(afterJson);
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(flatten(before, "").keySet());
        keys.addAll(flatten(after, "").keySet());
        List<Map<String, Object>> added = new ArrayList<>();
        List<Map<String, Object>> removed = new ArrayList<>();
        List<Map<String, Object>> changed = new ArrayList<>();
        Map<String, Object> flatBefore = flatten(before, "");
        Map<String, Object> flatAfter = flatten(after, "");
        for (String key : keys) {
            boolean inBefore = flatBefore.containsKey(key);
            boolean inAfter = flatAfter.containsKey(key);
            if (!inBefore && inAfter) {
                added.add(Map.of("path", key, "value", String.valueOf(flatAfter.get(key))));
            } else if (inBefore && !inAfter) {
                removed.add(Map.of("path", key, "value", String.valueOf(flatBefore.get(key))));
            } else if (!Objects.equals(flatBefore.get(key), flatAfter.get(key))) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("path", key);
                row.put("before", String.valueOf(flatBefore.get(key)));
                row.put("after", String.valueOf(flatAfter.get(key)));
                changed.add(row);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("added", added);
        out.put("removed", removed);
        out.put("changed", changed);
        out.put("addedCount", added.size());
        out.put("removedCount", removed.size());
        out.put("changedCount", changed.size());
        return out;
    }

    private Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception e) {
            return Map.of("_raw", json);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> flatten(Map<String, Object> src, String prefix) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (src == null) {
            return out;
        }
        for (Map.Entry<String, Object> e : src.entrySet()) {
            String path = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            Object v = e.getValue();
            if (v instanceof Map<?, ?> nested) {
                out.putAll(flatten((Map<String, Object>) nested, path));
            } else {
                out.put(path, v);
            }
        }
        return out;
    }
}
