/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/event/EventBusConfiguration.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/event
 * 3) 主要职责：向 Spring 容器注册 jforgame EventBus 单例，供领域事件发布与 @Subscribe 订阅。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */

package cn.itcast.demo.mymmorpg.event; // player-service 领域事件与 EventBus 注册

import jforgame.commons.eventbus.EventBus; // 领域事件 EventBus 发布/订阅
import org.springframework.context.annotation.Bean; // Spring 容器 ApplicationContext/Event
import org.springframework.context.annotation.Configuration; // Spring 容器 ApplicationContext/Event

@Configuration // 配置类集中装配 Bean

public class EventBusConfiguration { // EventBusConfiguration 类型定义
    /**
     * 进程内 EventBus：FunctionService、PlayerProgressService 等 post 事件，
     * EventAuditHandlers、PlayerDataAsyncPreloadHandler 等 @Subscribe 消费。
     */

    @Bean // 注册 Spring 单例 Bean
    public EventBus eventBus() { // EventBusConfiguration.eventBus：无参
        return new EventBus(); // 默认同步派发；getOwner 相同的事件串行处理
    } // eventBus 方法体结束
} // EventBusConfiguration 类体结束
