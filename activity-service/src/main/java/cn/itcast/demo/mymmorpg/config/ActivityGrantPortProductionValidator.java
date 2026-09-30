package cn.itcast.demo.mymmorpg.config;

import cn.itcast.demo.mymmorpg.port.ActivityItemGrantPort;
import cn.itcast.demo.mymmorpg.port.NoOpActivityItemGrantPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 生产环境禁止活动服以 NoOp 发奖端口启动（领奖会静默失败）。
 */
@Component
@ConditionalOnProperty(name = "spring.application.name", havingValue = "activity-service")
public class ActivityGrantPortProductionValidator {

    private final Environment environment;
    private final ActivityItemGrantPort activityItemGrantPort;

    public ActivityGrantPortProductionValidator(Environment environment, ActivityItemGrantPort activityItemGrantPort) {
        this.environment = environment;
        this.activityItemGrantPort = activityItemGrantPort;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validateOnStartup() {
        if (!isProdProfile()) {
            return;
        }
        if (activityItemGrantPort instanceof NoOpActivityItemGrantPort) {
            throw new IllegalStateException(
                    "生产环境 activity-service 发奖端口为 NoOp，领奖链路不可用。"
                            + " 请设置 GAME_PORT_REMOTE_ENABLED=true 并配置 BAG_SERVICE_URL"
                            + "（参考 .env.example / DEPLOYMENT.md）。");
        }
        String bagUrl = environment.getProperty("game.port.remote.bag-service-url");
        if (!StringUtils.hasText(bagUrl)) {
            throw new IllegalStateException(
                    "生产环境 activity-service 缺少 game.port.remote.bag-service-url / BAG_SERVICE_URL。");
        }
    }

    private boolean isProdProfile() {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }
}
