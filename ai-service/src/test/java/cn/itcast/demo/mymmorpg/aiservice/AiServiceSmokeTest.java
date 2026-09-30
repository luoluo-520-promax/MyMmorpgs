package cn.itcast.demo.mymmorpg.aiservice;

import cn.itcast.demo.mymmorpg.ai.gateway.AiInferenceGateway;
import cn.itcast.demo.mymmorpg.ai.gateway.AiInferenceRequest;
import cn.itcast.demo.mymmorpg.ai.recommend.RecommendationEngine;
import cn.itcast.demo.mymmorpg.aiservice.config.AiPlatformConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Map;

/**
 * 不启动完整 Spring Boot（避免 Nacos），仅装配平台配置 Bean。
 */
public class AiServiceSmokeTest {

    @Test
    public void platformBeansWire() {
        System.setProperty("spring.application.name", "ai-service");
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(AiPlatformConfiguration.class);
            ctx.refresh();
            AiInferenceGateway gw = ctx.getBean(AiInferenceGateway.class);
            Assert.assertTrue(gw.infer(AiInferenceRequest.builder()
                    .modelName("rule-default")
                    .task("tactical")
                    .feature("bossHpRatio", 0.2)
                    .build()).ok());
            RecommendationEngine engine = ctx.getBean(RecommendationEngine.class);
            Map<String, Object> rec = engine.recommendPayload(7, "equip", 3);
            Assert.assertTrue((Boolean) rec.get("ok"));
        } finally {
            System.clearProperty("spring.application.name");
        }
    }
}
