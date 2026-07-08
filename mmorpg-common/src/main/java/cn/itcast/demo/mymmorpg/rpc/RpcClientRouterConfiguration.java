/**
 * 文件说明
 * 模块：mmorpg-common / RPC 通信
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/rpc/RpcClientRouterConfiguration.java
 * 类型：类
 * 职责：将负载均衡策略注入 {@link RpcClientRouter}。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.rpc;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * RPC 客户端路由配置：启动后将 {@link BalanceStrategy} 绑定到 {@link RpcClientRouter}。
 * <p>
 * 游戏服转发客户端战斗包时，Router 按策略从多个战斗服 Session 中选一条发送 RPC。
 * </p>
 */
@Configuration
public class RpcClientRouterConfiguration {

    /** 战斗服/中心服 Session 路由表，维护 serverId → Netty 连接 */
    @Autowired
    private RpcClientRouter rpcClientRouter;

    /** 负载均衡策略（轮询、随机、一致性哈希等），决定选哪台战斗服 */
    @Autowired
    private BalanceStrategy balanceStrategy;

    /**
     * Spring 容器就绪后，将选服策略注入 Router，供 forward 战斗 RPC 时使用。
     */
    @PostConstruct
    public void bindStrategy() {
        rpcClientRouter.setBalanceStrategy(balanceStrategy); // 绑定后 RpcClientRouter 选战斗服 Session 时调用 strategy
    }
}
