/**
 * 标记 EventBus 订阅方法：被标注的 public void method(BaseEvent子类) 在 {@link EventBus#register} 时自动注册，
 * 事件发布后在独立线程池中异步反射调用，不阻塞 publish 调用方。
 */
package jforgame.commons.eventbus;

import java.lang.annotation.ElementType; // 注解只能贴在方法上

import java.lang.annotation.Retention; // 控制注解生命周期

import java.lang.annotation.RetentionPolicy; // RUNTIME 保留到运行时，EventBus 通过反射读取

import java.lang.annotation.Target; // 指定注解作用目标

/**
 * 用法示例：
 * <pre>
 * {@code @Subscribe public void onRpcConnected(RpcConnectedEvent e) { ... }}
 * </pre>
 * 方法须为 public、返回 void、且仅有一个 BaseEvent 子类参数。
 */
@Retention(RetentionPolicy.RUNTIME) // 运行时可见，register() 扫描时读取
@Target(ElementType.METHOD) // 仅允许标注在方法上，不能标在类或字段
public @interface Subscribe {
    // 标记注解，无属性；EventBus 仅通过存在与否识别订阅方法
}
