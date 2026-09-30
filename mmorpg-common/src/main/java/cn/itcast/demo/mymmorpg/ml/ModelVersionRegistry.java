package cn.itcast.demo.mymmorpg.ml;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型版本注册与回滚：绑定模型名到当前版本，支持快速切换。
 */
public final class ModelVersionRegistry {

    public record ModelVersion(String modelName, String version, String artifactUri, long registeredAtMs, boolean active) {
    }

    private final ConcurrentHashMap<String, ModelVersion> current = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, ModelVersion>> history = new ConcurrentHashMap<>();

    public synchronized ModelVersion register(String modelName, String version, String artifactUri) {
        if (modelName == null || modelName.isBlank() || version == null || version.isBlank()) {
            throw new IllegalArgumentException("modelName/version required");
        }
        ModelVersion mv = new ModelVersion(modelName, version, artifactUri == null ? "" : artifactUri,
                System.currentTimeMillis(), true);
        history.computeIfAbsent(modelName, k -> new ConcurrentHashMap<>()).put(version, mv);
        current.put(modelName, mv);
        return mv;
    }

    public Optional<ModelVersion> current(String modelName) {
        return Optional.ofNullable(current.get(modelName));
    }

    public synchronized Optional<ModelVersion> rollback(String modelName, String version) {
        ConcurrentHashMap<String, ModelVersion> hist = history.get(modelName);
        if (hist == null) {
            return Optional.empty();
        }
        ModelVersion prev = hist.get(version);
        if (prev == null) {
            return Optional.empty();
        }
        ModelVersion activated = new ModelVersion(prev.modelName(), prev.version(), prev.artifactUri(),
                System.currentTimeMillis(), true);
        current.put(modelName, activated);
        return Optional.of(activated);
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        current.forEach((k, v) -> m.put(k, Map.of(
                "version", v.version(),
                "artifactUri", v.artifactUri(),
                "registeredAtMs", v.registeredAtMs())));
        return m;
    }
}
