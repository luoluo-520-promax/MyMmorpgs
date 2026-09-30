package cn.itcast.demo.mymmorpg.client;

import cn.itcast.demo.mymmorpg.config.AdminFeignConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Map;

@FeignClient(name = "update-service", contextId = "updateOpsClient", configuration = AdminFeignConfiguration.class)
public interface UpdateOpsClient {

    @PostMapping("/internal/ops/reload")
    Map<String, Object> reload();
}
