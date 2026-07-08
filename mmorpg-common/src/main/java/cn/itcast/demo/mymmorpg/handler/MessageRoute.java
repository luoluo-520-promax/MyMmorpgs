/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/handler/MessageRoute.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/handler
 * 3) 主要职责：类 MessageRoute，承载模块内相关实现。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.handler; // player-service 协议 Facade 与消息 dispatch 管道
import java.lang.annotation.ElementType; // 注解只能标在 Facade 类上，标识协议模块归属
import java.lang.annotation.Retention; // 保留策略 RUNTIME，供 GameMessageFactory 启动扫描
import java.lang.annotation.RetentionPolicy; // 运行时可见，反射读取 module 编号
import java.lang.annotation.Target; // 限定注解作用目标为 TYPE（类级别）
/**
 * 标记 Facade 所属协议模块（module），对应 Modules 常量如 AUTH/SCENE/BAG。
 * <p>约束：abs(module) &lt; 326，否则 signedMsgId 计算越界。</p>
 */
@Target(ElementType.TYPE) // 标在 AuthFacade/SceneFacade 等类上，启动时被 GameMessageFactory 发现
@Retention(RetentionPolicy.RUNTIME) // 与 @RequestHandler 一起在 @PostConstruct 阶段注册 msgId 路由表
public @interface MessageRoute { // Facade 类级注解，module 与 @RequestHandler(cmd) 计算 signedMsgId
    /** 协议模块编号，符号决定 msgId 正负（客户端/服务端方向） */
    int module(); // 如 Modules.AUTH=1，与 cmd 组合 msgId=101
} // 编译单元结束
