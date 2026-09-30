/**
 * 文件维护说明
 * 1) 文件路径：quest-service/src/main/java/cn/itcast/demo/mymmorpg/config/QuestPortConfiguration.java
 * 2) 所属模块：quest-service / config
 * 3) 主要职责：在 quest-service 启动时按开关注入任务进度与玩家缓存端口的本地降级实现。
 * 4) 变更建议：如果后续接入远程 player-service，请同步检查 game.port.remote.enabled 的默认值与测试覆盖。
 * 5) 风险提示：端口 Bean 的选择会直接影响任务领奖、玩家查询与集成测试行为，修改前需确认部署模式。
 */
package cn.itcast.demo.mymmorpg.config; // quest-service 端口装配：决定任务服务使用本地 NoOp 还是远程实现

import cn.itcast.demo.mymmorpg.port.NoOpPlayerCachePort; // 玩家缓存端口的本地降级实现，避免无远程依赖时启动失败
import cn.itcast.demo.mymmorpg.port.NoOpPlayerProgressPort; // 玩家成长端口的本地降级实现，避免无远程依赖时启动失败
import cn.itcast.demo.mymmorpg.port.PlayerCachePort; // 任务服务读取玩家信息所依赖的抽象端口
import cn.itcast.demo.mymmorpg.port.PlayerProgressPort; // 任务领奖时发放经验金币所依赖的抽象端口
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // 根据配置开关决定是否注册 Bean
import org.springframework.context.annotation.Bean; // 声明任务服务所需的端口 Bean
import org.springframework.context.annotation.Configuration; // 标记这是 Spring 配置类

/**
 * 负责为任务服务提供默认端口实现
 */
@Configuration(proxyBeanMethods = false) // 纯装配类，不需要代理增强，减少启动开销
@ConditionalOnProperty(name = "spring.application.name", havingValue = "quest-service") // 仅 quest-service 进程启用本配置
public class QuestPortConfiguration {

    @Bean // 暴露 PlayerCachePort 供 QuestService 查询玩家
    @ConditionalOnProperty(name = "game.port.remote.enabled", havingValue = "false", matchIfMissing = true) // 远程端口未开启时使用本地降级
    PlayerCachePort playerCachePort() { // 任务服务读取玩家缓存信息的默认实现
        return new NoOpPlayerCachePort(); // 本地无状态降级：不触发远程 player-service 调用
    }

    @Bean // 暴露 PlayerProgressPort 供 QuestService 发放经验与金币
    @ConditionalOnProperty(name = "game.port.remote.enabled", havingValue = "false", matchIfMissing = true) // 远程端口未开启时使用本地降级
    PlayerProgressPort playerProgressPort() { // 任务领奖时写入玩家成长数据的默认实现
        return new NoOpPlayerProgressPort(); // 本地无状态降级：不触发远程 player-service 调用
    }
}
