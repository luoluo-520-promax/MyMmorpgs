package cn.itcast.demo.mymmorpg.ai.gateway;

import cn.itcast.demo.mymmorpg.metrics.AiMetrics;
import cn.itcast.demo.mymmorpg.ml.ModelVersionRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一 AI 推理网关：路由模型后端、版本绑定、缓存、熔断与降级。
 * <p>
 * 管道 A（战斗 Tick）禁止经本网关调用 LLM；本网关面向管道 B 与离线/准实时任务。
 */
public final class AiInferenceGateway {

    private final List<AiModelBackend> backends = new ArrayList<>();
    private final RuleEngineBackend ruleFallback = new RuleEngineBackend();
    private final ModelVersionRegistry versionRegistry;
    private final AiMetrics metrics;
    private final ConcurrentHashMap<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Cached> cache = new ConcurrentHashMap<>();
    private final long cacheTtlMs;

    public AiInferenceGateway() {
        this(new ModelVersionRegistry(), new AiMetrics(), 5_000L);
    }

    public AiInferenceGateway(ModelVersionRegistry versionRegistry, AiMetrics metrics, long cacheTtlMs) {
        this.versionRegistry = versionRegistry == null ? new ModelVersionRegistry() : versionRegistry;
        this.metrics = metrics == null ? new AiMetrics() : metrics;
        this.cacheTtlMs = Math.max(0L, cacheTtlMs);
        registerBackend(ruleFallback);
        this.versionRegistry.register("rule-default", "rules-v1", "local://rule-engine");
        this.versionRegistry.register("churn_xgb", "rules-v1", "local://rules/churn");
        this.versionRegistry.register("recommend_cf", "rules-v1", "local://rules/recommend");
    }

    public synchronized void registerBackend(AiModelBackend backend) {
        if (backend != null) {
            backends.removeIf(b -> b.name().equals(backend.name()));
            backends.add(backend);
        }
    }

    public AiInferenceResponse infer(AiInferenceRequest request) {
        if (request == null || request.modelName() == null || request.modelName().isBlank()) {
            return AiInferenceResponse.fail("unknown", "invalid_request");
        }
        String cacheKey = cacheKey(request);
        if (cacheTtlMs > 0) {
            Cached hit = cache.get(cacheKey);
            if (hit != null && System.currentTimeMillis() - hit.atMs < cacheTtlMs) {
                metrics.recordDecision("gateway_cache_hit");
                return hit.response;
            }
        }

        AiModelBackend backend = resolveBackend(request.modelName());
        CircuitBreaker breaker = breakers.computeIfAbsent(backend.name(), k -> new CircuitBreaker());
        long t0 = System.nanoTime();

        if (!breaker.allow()) {
            metrics.recordDecision("gateway_circuit_open");
            AiInferenceResponse degraded = ruleFallback.infer(request);
            return AiInferenceResponse.degraded(
                    request.modelName(),
                    versionOf(request.modelName()),
                    RuleEngineBackend.NAME,
                    degraded.result(),
                    "circuit_open:" + backend.name(),
                    (System.nanoTime() - t0) / 1_000_000L);
        }

        try {
            AiInferenceResponse resp = backend.infer(request);
            if (!resp.ok()) {
                breaker.onFailure();
                metrics.recordDecision("gateway_fail");
                return fallback(request, "backend_fail", t0);
            }
            breaker.onSuccess();
            metrics.recordDecision("gateway_ok");
            String ver = resp.modelVersion() != null ? resp.modelVersion() : versionOf(request.modelName());
            AiInferenceResponse withVer = new AiInferenceResponse(
                    true, request.modelName(), ver, resp.backend(), resp.result(),
                    resp.degraded(), resp.degradeReason(), resp.latencyMs(), resp.meta());
            if (cacheTtlMs > 0) {
                cache.put(cacheKey, new Cached(withVer, System.currentTimeMillis()));
            }
            return withVer;
        } catch (Exception e) {
            breaker.onFailure();
            metrics.recordDecision("gateway_exception");
            return fallback(request, "exception:" + e.getClass().getSimpleName(), t0);
        }
    }

    public Map<String, Object> health() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("backends", backends.stream().map(AiModelBackend::name).toList());
        Map<String, Object> cb = new LinkedHashMap<>();
        breakers.forEach((k, v) -> cb.put(k, Map.of("state", v.state().name(), "failures", v.failures())));
        m.put("circuitBreakers", cb);
        m.put("cacheSize", cache.size());
        m.put("models", versionRegistry.snapshot());
        m.put("metrics", metrics.snapshot());
        return m;
    }

    public ModelVersionRegistry versions() {
        return versionRegistry;
    }

    public AiMetrics metrics() {
        return metrics;
    }

    public void clearCache() {
        cache.clear();
    }

    private AiInferenceResponse fallback(AiInferenceRequest request, String reason, long t0) {
        AiInferenceResponse degraded = ruleFallback.infer(request);
        return AiInferenceResponse.degraded(
                request.modelName(),
                versionOf(request.modelName()),
                RuleEngineBackend.NAME,
                degraded.result(),
                reason,
                (System.nanoTime() - t0) / 1_000_000L);
    }

    private AiModelBackend resolveBackend(String modelName) {
        for (AiModelBackend b : backends) {
            if (b.supports(modelName) && !(b instanceof RuleEngineBackend)) {
                return b;
            }
        }
        for (AiModelBackend b : backends) {
            if (b.supports(modelName)) {
                return b;
            }
        }
        return ruleFallback;
    }

    private String versionOf(String modelName) {
        Optional<ModelVersionRegistry.ModelVersion> cur = versionRegistry.current(modelName);
        return cur.map(ModelVersionRegistry.ModelVersion::version).orElse("rules-v1");
    }

    private static String cacheKey(AiInferenceRequest r) {
        return r.modelName() + "|" + r.task() + "|" + r.playerId() + "|" + r.features().hashCode();
    }

    private record Cached(AiInferenceResponse response, long atMs) {
    }
}
