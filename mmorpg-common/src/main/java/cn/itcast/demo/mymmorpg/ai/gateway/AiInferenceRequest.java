package cn.itcast.demo.mymmorpg.ai.gateway;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一推理请求：模型名 + 输入特征 + 可选上下文。
 */
public record AiInferenceRequest(
        String modelName,
        String task,
        long playerId,
        Map<String, Object> features,
        Map<String, Object> context,
        long timeoutMs) {

    public AiInferenceRequest {
        features = features == null ? Map.of() : Map.copyOf(features);
        context = context == null ? Map.of() : Map.copyOf(context);
        if (timeoutMs <= 0) {
            timeoutMs = 3_000L;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String modelName = "rule-default";
        private String task = "infer";
        private long playerId;
        private Map<String, Object> features = new LinkedHashMap<>();
        private Map<String, Object> context = new LinkedHashMap<>();
        private long timeoutMs = 3_000L;

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder task(String task) {
            this.task = task;
            return this;
        }

        public Builder playerId(long playerId) {
            this.playerId = playerId;
            return this;
        }

        public Builder feature(String key, Object value) {
            this.features.put(key, value);
            return this;
        }

        public Builder features(Map<String, Object> features) {
            if (features != null) {
                this.features.putAll(features);
            }
            return this;
        }

        public Builder context(Map<String, Object> context) {
            if (context != null) {
                this.context.putAll(context);
            }
            return this;
        }

        public Builder timeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        public AiInferenceRequest build() {
            return new AiInferenceRequest(modelName, task, playerId, features, context, timeoutMs);
        }
    }
}
