package cn.itcast.demo.mymmorpg.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AiPlatformClientFallbackFactory implements FallbackFactory<AiPlatformClient> {

    private static final Logger log = LoggerFactory.getLogger(AiPlatformClientFallbackFactory.class);

    @Override
    public AiPlatformClient create(Throwable cause) {
        log.warn("ai-service 不可用，Feign 降级: {}", cause == null ? "unknown" : cause.getMessage());
        return new AiPlatformClient() {
            @Override
            public Map<String, Object> recommend(Map<String, Object> body) {
                return Map.of("ok", false, "error", "ai_service_unavailable", "degraded", true);
            }

            @Override
            public Map<String, Object> recordBehavior(Map<String, Object> body) {
                return Map.of("ok", false, "error", "ai_service_unavailable");
            }

            @Override
            public Map<String, Object> profile(long playerId) {
                return Map.of("ok", false, "error", "ai_service_unavailable", "playerId", playerId);
            }

            @Override
            public Map<String, Object> supportAsk(Map<String, Object> body) {
                return Map.of("ok", true, "answer", "智能客服暂不可用，请稍后再试或联系人工客服。",
                        "source", "fallback", "degraded", true);
            }

            @Override
            public Map<String, Object> tacticalAdvise(Map<String, Object> body) {
                return Map.of("ok", true, "hint", "保持输出循环，注意走位。", "priority", "dps", "degraded", true);
            }

            @Override
            public Map<String, Object> retentionEvaluate(Map<String, Object> body) {
                return Map.of("ok", false, "error", "ai_service_unavailable");
            }

            @Override
            public Map<String, Object> contentGenerate(Map<String, Object> body) {
                return Map.of("ok", false, "error", "ai_service_unavailable");
            }

            @Override
            public Map<String, Object> contentValidate(Map<String, Object> body) {
                return Map.of("ok", false, "error", "ai_service_unavailable");
            }

            @Override
            public Map<String, Object> infer(Map<String, Object> body) {
                return Map.of("ok", false, "error", "ai_service_unavailable");
            }

            @Override
            public Map<String, Object> health() {
                return Map.of("ok", false, "error", "ai_service_unavailable");
            }

            @Override
            public Map<String, Object> narrativeGenerate(Map<String, Object> body) {
                return Map.of("ok", false, "error", "ai_service_unavailable", "degraded", true);
            }

            @Override
            public Map<String, Object> companionDialogue(Map<String, Object> body) {
                return Map.of("ok", true, "degraded", true,
                        "dialogue", Map.of("text", "伙伴暂时失联，稍后再聊。", "msgId", 2500));
            }

            @Override
            public Map<String, Object> analystFeedback(Map<String, Object> body) {
                return Map.of("ok", false, "error", "ai_service_unavailable", "degraded", true);
            }

            @Override
            public Map<String, Object> screenWaypoint(Map<String, Object> body) {
                return Map.of("ok", false, "error", "ai_service_unavailable", "degraded", true);
            }

            @Override
            public Map<String, Object> personaStyle(Map<String, Object> body) {
                return Map.of("ok", false, "error", "ai_service_unavailable");
            }
        };
    }
}
