package cn.itcast.demo.mymmorpg.config; // skill-service 模块 Spring 配置包

import cn.itcast.demo.mymmorpg.port.NoOpPlayerDataLoadPort; // 空实现 PlayerDataLoadPort：isReady 恒 true，独立 skill-service 不门控预加载
import cn.itcast.demo.mymmorpg.port.NoOpPlayerNotificationPort; // 空实现 PlayerNotificationPort：send 空操作，无 WebSocket 下行
import cn.itcast.demo.mymmorpg.port.PlayerDataLoadPort; // 玩家数据预加载状态端口，SkillService.handleGetPlayerSkills 检查 DataType.SKILL 是否就绪
import cn.itcast.demo.mymmorpg.port.PlayerNotificationPort; // 玩家推送端口，SkillService 推送 SKILL_COOLDOWN_SC_NOTIFY / SKILL_LEARN_SC_NOTIFY
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // 限定本配置仅在 skill-service 进程生效
import org.springframework.context.annotation.Bean; // 注册 NoOp Port Bean
import org.springframework.context.annotation.Configuration; // 声明配置类

@Configuration(proxyBeanMethods = false) // 无 @Bean 互相调用，关闭 CGLIB 代理
@ConditionalOnProperty(name = "spring.application.name", havingValue = "skill-service") // 与 SkillServiceApplication 同条件，player-service 聚合部署时不加载
public class SkillPortConfiguration { // 为 skill-service 独立部署提供 PlayerNotificationPort / PlayerDataLoadPort 占位实现

    @Bean
    @ConditionalOnProperty(name = "game.port.remote.enabled", havingValue = "false", matchIfMissing = true)
    PlayerNotificationPort playerNotificationPort() { // 工厂方法：返回不推送消息的通知端口
        return new NoOpPlayerNotificationPort(); // 独立 skill-service 施法后 CD/Learn notify 不会下发客户端
    }

    @Bean
    @ConditionalOnProperty(name = "game.port.remote.enabled", havingValue = "false", matchIfMissing = true)
    PlayerDataLoadPort playerDataLoadPort() { // 工厂方法：返回不跟踪预加载状态的端口
        return new NoOpPlayerDataLoadPort(); // isReady 恒 true，handleGetPlayerSkills 不会返回 loading=true 占位
    }
}
