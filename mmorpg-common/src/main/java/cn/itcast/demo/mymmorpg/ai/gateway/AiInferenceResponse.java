package cn.itcast.demo.mymmorpg.ai.gateway;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一推理响应：含降级标记、版本、延迟。
 */
public record AiInferenceResponse(
        boolean ok,
        String modelName,
        String modelVersion,
        String backend,
        Object result,
        boolean degraded,
        String degradeReason,
        long latencyMs,
        Map<String, Object> meta) {

    public AiInferenceResponse {
        meta = meta == null ? Map.of() : Map.copyOf(meta);
    }

    public static AiInferenceResponse success(String modelName, String version, String backend,
                                              Object result, long latencyMs) {
        return new AiInferenceResponse(true, modelName, version, backend, result, false, null, latencyMs, Map.of());
    }

    public static AiInferenceResponse degraded(String modelName, String version, String backend,
                                               Object result, String reason, long latencyMs) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("degraded", true);
        return new AiInferenceResponse(true, modelName, version, backend, result, true, reason, latencyMs, meta);
    }

    public static AiInferenceResponse fail(String modelName, String reason) {
        return new AiInferenceResponse(false, modelName, null, "none", null, true, reason, 0L, Map.of());
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", ok);
        m.put("modelName", modelName);
        m.put("modelVersion", modelVersion);
        m.put("backend", backend);
        m.put("result", result);
        m.put("degraded", degraded);
        if (degradeReason != null) {
            m.put("degradeReason", degradeReason);
        }
        m.put("latencyMs", latencyMs);
        m.put("meta", meta);
        return m;
    }
}
