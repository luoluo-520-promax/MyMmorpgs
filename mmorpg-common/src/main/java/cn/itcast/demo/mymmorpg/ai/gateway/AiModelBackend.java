package cn.itcast.demo.mymmorpg.ai.gateway;

/**
 * 模型后端抽象：规则引擎 / 传统 ML / LLM / RL 均可实现。
 */
public interface AiModelBackend {

    String name();

    boolean supports(String modelName);

    AiInferenceResponse infer(AiInferenceRequest request);
}
