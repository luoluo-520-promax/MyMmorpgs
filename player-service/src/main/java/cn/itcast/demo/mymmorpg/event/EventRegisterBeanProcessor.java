/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/event/EventRegisterBeanProcessor.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/event
 * 3) 主要职责：Bean 初始化前自动 eventBus.register(bean)，扫描 @Subscribe 方法无需手动注册。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */

package cn.itcast.demo.mymmorpg.event; // player-service 领域事件与 EventBus 注册

import jforgame.commons.eventbus.EventBus; // 领域事件 EventBus 发布/订阅
import org.springframework.beans.BeansException; // BeansException，EventRegisterBeanProcessor.java 编译依赖
import org.springframework.beans.factory.config.BeanPostProcessor; // Bean 后置处理器与依赖注入
import org.springframework.context.ApplicationContext; // Spring 容器 ApplicationContext/Event
import org.springframework.context.ApplicationContextAware; // Spring 容器 ApplicationContext/Event
import org.springframework.core.Ordered; // Ordered，EventRegisterBeanProcessor.java 编译依赖
import org.springframework.stereotype.Component; // Spring 组件 stereotype 注解
/**
 * Spring Bean 自动注册到 EventBus：每个 Bean 在 initializeBean 之前 register，反射发现 @Subscribe。
 */

@Component // Spring 单例组件

public class EventRegisterBeanProcessor implements BeanPostProcessor, ApplicationContextAware, Ordered { // EventRegisterBeanProcessor 类型定义
    /** 全局 EventBus，与 EventBusConfiguration.eventBus 同一实例 */

    private final EventBus eventBus; // FunctionFacade / EventAuditHandlers 等订阅者注册目标
    @SuppressWarnings("unused") // @SuppressWarnings 注解
    private ApplicationContext applicationContext; // 预留按事件类型延迟绑定订阅者
    public EventRegisterBeanProcessor(EventBus eventBus) { // 构造 EventRegisterBeanProcessor，注入 EventBus eventBus
        this.eventBus = eventBus; // 注入进程内事件总线单例
    } // EventRegisterBeanProcessor 方法体结束
    /**
     * 在 Bean @PostConstruct 之前 register，保证首次 post 事件时 @Subscribe 已就绪。
     */

    @Override // 实现接口/父类方法
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException { // EventRegisterBeanProcessor.postProcessBeforeInitialization：Object bean, String beanName
        eventBus.register(bean); // 扫描 bean 类及父类中带 @Subscribe 的 public 方法
        return bean; // 不替换 Bean 实例，仅完成 EventBus 注册
    } // postProcessBeforeInitialization 方法体结束

    @Override // 实现接口/父类方法
    public int getOrder() { // 读取 Order（Order）
        return 0; // 尽早注册订阅者，与 ConfigManagerBinder 同级优先级
    } // getOrder 方法体结束

    @Override // 实现接口/父类方法
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException { // 写入 ApplicationContext（ApplicationContext）
        this.applicationContext = applicationContext; // 预留扩展：按类型查找订阅 Bean
    } // setApplicationContext 方法体结束
} // EventRegisterBeanProcessor 类体结束
