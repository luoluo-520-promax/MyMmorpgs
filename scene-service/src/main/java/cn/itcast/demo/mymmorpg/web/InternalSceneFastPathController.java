package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.service.SceneActorService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 战斗/移动消息快速路径：Gateway 将 X-Msg-Id 1400~2000 号段路由至此，绕过 player-service MVC 线程池。
 */
@RestController
@ConditionalOnProperty(name = "spring.application.name", havingValue = "scene-service")
@RequestMapping("/internal/scene/fast-path")
public class InternalSceneFastPathController {

    private final SceneActorService sceneActorService;

    public InternalSceneFastPathController(SceneActorService sceneActorService) {
        this.sceneActorService = sceneActorService;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("fastPath", true);
        body.put("performance", sceneActorService.performanceSnapshot());
        return body;
    }

    @PostMapping("/move-ack")
    public Map<String, Object> moveAckBatch(@RequestBody Map<String, Object> body) {
        Map<String, Object> perf = sceneActorService.performanceSnapshot();
        Map<String, Object> rsp = new LinkedHashMap<>();
        rsp.put("ok", true);
        rsp.put("fastPath", true);
        rsp.put("mergedMoveAck", perf.get("mergedMoveAck"));
        return rsp;
    }
}
