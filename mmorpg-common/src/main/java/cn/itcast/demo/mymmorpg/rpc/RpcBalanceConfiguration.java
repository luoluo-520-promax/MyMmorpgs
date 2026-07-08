/**
 * 文件说明
 * 模块：mmorpg-common / RPC 通信
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/rpc/RpcBalanceConfiguration.java
 * 类型：类
 * 职责：根据 application.yml 中的 rpc.balanceStrategy 注入战斗服负载均衡 Bean。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.rpc;

import cn.itcast.demo.mymmorpg.net.ServerConfig;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Spring 配置：读取服务器 RPC 配置并注册 {@link BalanceStrategy} 实现。
 */
@Configuration
public class RpcBalanceConfiguration {

    /**
     * 创建战斗服 RPC 负载均衡策略 Bean，供 RpcClientRouter 注入使用。
     *
     * @param serverConfig 全局服务器配置，含 rpc.balanceStrategy（RANDOM 或默认 ROUND）
     */
    @Bean
    public BalanceStrategy balanceStrategy(ServerConfig serverConfig) {
        String strategy = serverConfig.getRpc().getBalanceStrategy(); // 读取 yml 中配置的均衡策略名
        if (StringUtils.hasText(strategy) && "RANDOM".equalsIgnoreCase(strategy.trim())) { // 显式配置为随机策略
            return new RandomBalanceStrategy(); // 注册随机选取战斗服会话的实现
        }
        return new RoundBalanceStrategy(); // 未配置或非 RANDOM 时默认使用轮询策略
    }
}
