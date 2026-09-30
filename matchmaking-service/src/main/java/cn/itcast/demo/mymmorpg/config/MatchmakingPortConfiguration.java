/**
 * 文件维护说明：`matchmaking-service` 的端口装配配置类。
 * <p>
 * 路径：`matchmaking-service/src/main/java/cn/itcast/demo/mymmorpg/config/MatchmakingPortConfiguration.java`
 * <br>模块：匹配服基础装配
 * <br>职责：在仅启动匹配服时，为本模块提供匹配所需的端口实现，并在未开启远程端口时回退为本地空实现，保证单服务调试与集成测试可运行。
 * <br>变更建议：仅在匹配服需要接入新的外部能力或切换远程端口策略时修改；若增加远程依赖，应同步补充配置开关与测试覆盖。
 * <br>风险提示：错误替换端口实现可能导致匹配流程在本地缓存、远程玩家数据或内部接口依赖上产生行为偏差。
 */
package cn.itcast.demo.mymmorpg.config; // 匹配服配置包，负责装配匹配相关基础 Bean

import cn.itcast.demo.mymmorpg.port.NoOpPlayerCachePort; // 本地空实现，用于未启用远程玩家缓存时兜底
import cn.itcast.demo.mymmorpg.port.PlayerCachePort; // 玩家缓存端口，用于匹配时读取玩家等级与战力
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // 按配置开关控制 Bean 与配置类加载
import org.springframework.context.annotation.Bean; // 声明 Spring Bean 的工厂方法注解
import org.springframework.context.annotation.Configuration; // 标识 Spring 配置类

/**
 * 匹配服端口装配配置，控制玩家缓存端口的默认实现
 */
@Configuration(proxyBeanMethods = false) // 声明该类为配置类，避免代理开销提升启动效率
@ConditionalOnProperty(name = "spring.application.name", havingValue = "matchmaking-service") // 仅在匹配服进程中生效，避免污染其他模块容器
public class MatchmakingPortConfiguration {
    /**
     * 提供玩家缓存端口的默认实现
     * @return
     */
    @Bean // 将端口实现注册为 Spring 容器 Bean，供匹配服务注入使用
    @ConditionalOnProperty(name = "game.port.remote.enabled", havingValue = "false", matchIfMissing = true) // 未开启远程端口时使用本地空实现，保障单机启动
    PlayerCachePort playerCachePort() {
        return new NoOpPlayerCachePort(); // 关闭远程依赖时返回空实现，避免匹配流程因缓存缺失而失败
    }
}
