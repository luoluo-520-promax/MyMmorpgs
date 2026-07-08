/**
 * MQ Handler 自动注册：Spring 容器初始化每个 Bean 后，若实现了 MqMessageHandler，
 * 则自动注册到 MqMessageDispatcher 与 MqMessageTypeRegistry，无需手写 register 代码。
 */
package cn.itcast.demo.mymmorpg.support; // MMORPG 公共支撑包：放置消息处理、注册与分发等基础设施组件

import org.springframework.beans.BeansException; // Spring Bean 生命周期处理时可能抛出的异常类型
import org.springframework.beans.factory.config.BeanPostProcessor; // Spring Bean 初始化前后插入钩子，用于自动扫描消息处理器
import org.springframework.core.Ordered; // 让该注册器在 BeanPostProcessor 链中按指定顺序执行
import org.springframework.stereotype.Component; // 将自动注册器交给 Spring 托管为基础设施组件

import java.util.Set; // 记录已经注册过的 Bean 名称，避免同一个 MQ 处理器被重复绑定
import java.util.concurrent.ConcurrentHashMap; // 并发环境下安全维护已注册处理器集合

@Component // 由 Spring 扫描装配，确保服务启动时自动执行 MQ 处理器注册
public class MqHandlerAutoRegistrar implements BeanPostProcessor, Ordered { // Bean 后置处理器：在 MMORPG 服务启动时自动接管消息处理器注册

    private final MqMessageDispatcher dispatcher; // 负责把具体消息对象分发给对应战斗/场景/任务处理器
    private final MqMessageTypeRegistry typeRegistry; // 维护消息类型到 Java 类的映射，供消费端反序列化
    /** 已注册的 beanName，防止同一 Bean 被 postProcess 多次重复注册 */ // 记录已接入 MQ 基础设施的处理器实例名
    private final Set<String> registeredBeans = ConcurrentHashMap.newKeySet(); // 并发安全的集合，保证多线程初始化时也不会重复注册

    public MqHandlerAutoRegistrar(MqMessageDispatcher dispatcher, MqMessageTypeRegistry typeRegistry) { // 构造注入分发器与消息类型注册表
        this.dispatcher = dispatcher; // 保存消息分发器，后续注册具体消息处理逻辑
        this.typeRegistry = typeRegistry; // 保存消息类型注册表，后续登记协议消息的实际类型
    }

    @SuppressWarnings("unchecked") // 这里会把泛型处理器收窄为具体消息类型，属于 Spring 基础设施层的受控转换
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException { // Bean 初始化完成后，检查是否是 MMORPG MQ 处理器
        if (!(bean instanceof MqMessageHandler<?> handler)) { // 只有实现了消息处理器接口的 Bean 才需要纳入 MQ 分发体系
            return bean; // 非消息处理 Bean 直接返回，避免影响战斗、场景等其他服务 Bean 的初始化
        }
        if (!registeredBeans.add(beanName)) { // 同名 Bean 已经完成注册，跳过重复处理
            return bean; // 避免同一个战斗消息处理器被多次挂载到分发器
        }
        MqMessageHandler<? extends MqMessage> cast = (MqMessageHandler<? extends MqMessage>) handler; // 将通用处理器转为可登记的具体消息处理器
        dispatcher.registerHandler(cast); // 在消息分发器中建立“消息类型 -> 处理器”的路由关系
        typeRegistry.register((Class<? extends MqMessage>) cast.messageType()); // 注册消息 Java 类型，供 RocketMQ 消费端解析战斗/场景消息
        return bean; // 保持 Spring Bean 原样返回，不改变业务对象本身
    }

    @Override
    public int getOrder() { // 决定该后置处理器在 Spring 容器中的执行优先级
        return Ordered.LOWEST_PRECEDENCE; // 尽量最后执行，确保处理器依赖的其他 Bean 都已完成注入
    }
}
