package cn.itcast.demo.mymmorpg.support;

/**
 * 跨模块故障上报钩子，player-service 注入 Micrometer 实现，common 模块默认 no-op。
 */
public interface DispatchFailureReporter {

    default void recordRpcForwardFailure() {
    }

    default void recordPushFailure() {
    }

    default void recordMqPublishFailure() {
    }
}
