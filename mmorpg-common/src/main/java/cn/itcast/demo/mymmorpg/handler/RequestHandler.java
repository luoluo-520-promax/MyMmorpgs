/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/handler/RequestHandler.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/handler
 * 3) 主要职责：类 RequestHandler，承载模块内相关实现。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.handler; // player-service 协议 Facade 与消息 dispatch 管道
import java.lang.annotation.ElementType; // 注解只能标注 Facade 方法，不能标在类或字段
import java.lang.annotation.Retention; // 保留策略 RUNTIME，供 GameMessageFactory 启动扫描
import java.lang.annotation.RetentionPolicy; // 运行时可见，反射读取 @RequestHandler(cmd)
import java.lang.annotation.Target; // 限定注解作用目标为 METHOD
/**
 * 标记 Facade 内的 protobuf 消息处理方法。
 * <p>约束：方法必须是 public，且位于 {@link MessageRoute} 标记的 Facade 类中。</p>
 */
@Target(ElementType.METHOD) // 仅 Facade 业务方法可携带，与 @MessageRoute(module) 组合注册路由
@Retention(RetentionPolicy.RUNTIME) // 启动时 GameMessageFactory 反射扫描并绑定 MethodHandle
public @interface RequestHandler { // Facade 方法级注解，cmd 须与 PayloadPacket @MessageMeta 一致
    /**
     * 模块内命令 ID（cmd），与 @MessageRoute.module 组合计算全局 msgId：abs(module)*100+cmd。
     */
    int cmd(); // 如 AuthFacade accountLogin cmd=1 -> msgId 101
} // 编译单元结束
