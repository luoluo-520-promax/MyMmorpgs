package cn.itcast.demo.mymmorpg.client;

import cn.itcast.demo.mymmorpg.config.AdminFeignConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Map;

@FeignClient(name = "quest-service", contextId = "questOpsClient", configuration = AdminFeignConfiguration.class)
public interface QuestOpsClient {

    @PostMapping("/internal/quest/ops/reload")
    Map<String, Object> reload();
}
