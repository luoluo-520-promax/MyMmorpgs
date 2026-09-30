package cn.itcast.demo.mymmorpg.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 活动域热更探测：活动配置已落库，本接口用于分阶段热更编排与健康确认。
 */
@RestController
@RequestMapping("/internal/ops")
@ConditionalOnProperty(name = "spring.application.name", havingValue = "activity-service")
public class InternalActivityOpsController {

    @PostMapping("/reload")
    public Map<String, Object> reload() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("service", "activity-service");
        body.put("message", "activity config is DB-backed; import path already persisted");
        return body;
    }
}
