package cn.itcast.demo.mymmorpg.ai.gateway;

import org.testng.Assert;
import org.testng.annotations.Test;

public class AiInferenceGatewayTest {

    @Test
    public void inferRiskAndCache() {
        AiInferenceGateway gw = new AiInferenceGateway();
        AiInferenceRequest req = AiInferenceRequest.builder()
                .modelName("churn_xgb")
                .task("churn")
                .playerId(1001)
                .feature("loginDays7", 1)
                .feature("daysSinceLastLogin", 10)
                .feature("questCompletionRate", 0.1)
                .build();
        AiInferenceResponse r1 = gw.infer(req);
        Assert.assertTrue(r1.ok());
        Assert.assertNotNull(r1.result());
        AiInferenceResponse r2 = gw.infer(req);
        Assert.assertTrue(r2.ok());
        Assert.assertTrue(gw.health().containsKey("backends"));
    }

    @Test
    public void circuitOpensAndDegrades() {
        AiInferenceGateway gw = new AiInferenceGateway();
        gw.registerBackend(new AiModelBackend() {
            @Override
            public String name() {
                return "broken";
            }

            @Override
            public boolean supports(String modelName) {
                return "broken-model".equals(modelName);
            }

            @Override
            public AiInferenceResponse infer(AiInferenceRequest request) {
                throw new RuntimeException("boom");
            }
        });
        AiInferenceRequest req = AiInferenceRequest.builder()
                .modelName("broken-model")
                .task("faq")
                .feature("question", "怎么升级")
                .build();
        for (int i = 0; i < 6; i++) {
            AiInferenceResponse r = gw.infer(req);
            Assert.assertTrue(r.ok());
            Assert.assertTrue(r.degraded());
        }
    }
}
