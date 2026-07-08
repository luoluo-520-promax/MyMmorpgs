/**
 * 文件说明
 * 模块：mmorpg-common / RPC 通信
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/rpc/RpcBeansConfiguration.java
 * 类型：类
 * 职责：RPC 基础 Spring Bean 装配。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.rpc;

import jforgame.commons.eventbus.EventBus;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

import org.springframework.context.annotation.Bean;

import org.springframework.context.annotation.Configuration;

/**
 * 跨服 RPC 模块基础 Bean 装配。
 * <p>
 * 为各服（游戏/战斗/中心）提供 RPC 层共用的 EventBus，用于跨服事件通知（如节点上下线）。
 * </p>
 */
@Configuration
public class RpcBeansConfiguration {

    /**
     * 当容器内尚无 EventBus 时，注册 RPC 专用事件总线。
     *
     * @return 用于跨服 RPC 相关异步事件（目录变更、连接状态等）的分发器
     */
    @Bean
    @ConditionalOnMissingBean(EventBus.class)
    public EventBus rpcEventBus() {
        return new EventBus(); // 默认实现，业务模块可订阅战斗服列表更新等 RPC 事件
    }
}
