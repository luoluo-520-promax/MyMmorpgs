package cn.itcast.demo.mymmorpg.client;

import cn.itcast.demo.mymmorpg.config.AdminFeignConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "ai-service", contextId = "adminAiPlatformClient",
        configuration = AdminFeignConfiguration.class)
public interface AdminAiPlatformClient {

    @PostMapping("/internal/ai/retention/evaluate")
    Map<String, Object> retentionEvaluate(@RequestBody Map<String, Object> body);

    @PostMapping("/internal/ai/content/generate")
    Map<String, Object> contentGenerate(@RequestBody Map<String, Object> body);

    @PostMapping("/internal/ai/content/validate")
    Map<String, Object> contentValidate(@RequestBody Map<String, Object> body);
}
