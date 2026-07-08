/**
 * 文件维护说明
 * 1) 文件路径：mmorpg-gateway/src/main/java/cn/itcast/demo/mymmorpg/gateway/GatewayObserveConfiguration.java
 * 2) 所属模块：mmorpg-gateway / main/java/cn/itcast/demo/mymmorpg/gateway
 * 3) 主要职责：注册带观测能力的 WebSocketService Bean，替换 Gateway 默认实现。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.gateway;

import org.springframework.boot.context.properties.EnableConfigurationProperties; // 显式启用 GatewayObserveProperties 绑定（与 @ConfigurationPropertiesScan 互补）
import org.springframework.context.annotation.Configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary; // 同名 WebSocketService 类型存在多个 Bean 时优先注入本 Bean

import org.springframework.web.reactive.socket.server.WebSocketService;


@Configuration
@EnableConfigurationProperties(GatewayObserveProperties.class) // 确保 game.gateway.observe.* 注入到 GatewayObserveProperties
public class GatewayObserveConfiguration {

    /**
     * 用 ObservabilityWebSocketService 包装 Spring 默认 WebSocketService，
     * @Primary 使 Gateway 在 WS 升级时使用带 GW_WS 日志的装饰器。
     *
     * @param delegate Spring 自动配置的原始 WebSocketService（实际处理握手与帧转发）
     * @param props    观测开关与采样率配置
     * @return 装饰后的 WebSocketService Bean
     */
    @Bean
    @Primary
    public WebSocketService observabilityWebSocketService(WebSocketService delegate, GatewayObserveProperties props) {
        return new ObservabilityWebSocketService(delegate, props);
    }
}
