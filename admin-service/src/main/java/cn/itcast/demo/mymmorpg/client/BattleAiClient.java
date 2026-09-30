package cn.itcast.demo.mymmorpg.client;

import cn.itcast.demo.mymmorpg.config.AdminFeignConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(name = "battle-service", contextId = "battleAiClient", configuration = AdminFeignConfiguration.class)
public interface BattleAiClient {

    @PostMapping("/internal/battle/ai-teammate/bt/load")
    Map<String, Object> loadBehaviorTree(@RequestParam("treeId") String treeId,
                                         @RequestBody(required = false) List<Map<String, Object>> nodes);

    @GetMapping("/internal/battle/ai-teammate/bt/inspect")
    Map<String, Object> inspectBehaviorTree(@RequestParam("treeId") String treeId);

    @PostMapping("/internal/battle/ai-teammate/configure-boss-tree")
    Map<String, Object> configureBossTree(@RequestParam("botId") String botId,
                                          @RequestBody(required = false) List<Map<String, Object>> nodes);
}
