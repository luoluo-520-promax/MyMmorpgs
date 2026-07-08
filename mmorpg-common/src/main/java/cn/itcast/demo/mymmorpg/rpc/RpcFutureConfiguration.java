/**
 * 文件说明
 * 模块：mmorpg-common / RPC 通信
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/rpc/RpcFutureConfiguration.java
 * 类型：类
 * 职责：RPC Future/回调相关 Spring 配置占位。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.rpc;

import org.springframework.context.annotation.Configuration;

/**
 * RPC Future/回调相关配置占位。
 * <p>
 * {@link CallBackService} 已通过 {@code @Component} 由组件扫描注册，
 * 本配置类保留扩展点，便于后续集中配置 RPC 超时、线程池等跨服异步回调策略。
 * </p>
 */
@Configuration
public class RpcFutureConfiguration {
    // 当前无额外 Bean；CallBackService + RequestResponseFuture 构成同步 RPC 等待链路
}
