package cn.itcast.demo.mymmorpg.config;

import cn.itcast.demo.mymmorpg.ai.platform.AiPlatformFacade;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 单体模式：在 player-service 进程内嵌入 AI 平台门面（不依赖独立 ai-service 进程）。
 */
@Configuration
@ConditionalOnProperty(name = "spring.application.name", havingValue = "player-service")
public class PlayerAiPlatformConfiguration {

    @Bean
    @ConditionalOnProperty(name = "game.ai.remote.enabled", havingValue = "false", matchIfMissing = true)
    public AiPlatformFacade aiPlatformFacade() {
        return AiPlatformFacade.createDefault();
    }
}
