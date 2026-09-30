package cn.itcast.demo.mymmorpg.client;

import cn.itcast.demo.mymmorpg.config.AdminFeignConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "scene-service", contextId = "sceneGmClient", configuration = AdminFeignConfiguration.class)
public interface SceneGmClient {

    @GetMapping("/internal/scene/open-world/gm/catalog")
    Map<String, Object> catalog();

    @PostMapping("/internal/scene/open-world/gm/exec")
    Map<String, Object> exec(@RequestBody Map<String, Object> body);

    @PostMapping("/internal/scene/open-world/load/mixed")
    Map<String, Object> mixedLoad(
            @RequestParam(defaultValue = "500") int players,
            @RequestParam(defaultValue = "1000") int gatherOps,
            @RequestParam(defaultValue = "500") int combatLocks,
            @RequestParam(defaultValue = "5000") int moveQueries);
}
