/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/support/ConfigManagerBinder.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/support
 * 3) 主要职责：ApplicationReady 后将 Spring Environment 挂载到 ConfigManager 单例。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */

package cn.itcast.demo.mymmorpg.support; // player-service 业务策略接口与 JMX/ConfigManager

import org.springframework.context.ApplicationListener; // Spring 容器 ApplicationContext/Event
import org.springframework.boot.context.event.ApplicationReadyEvent; // Spring Boot 自动配置与 CommandLineRunner
import org.springframework.core.Ordered; // Ordered，ConfigManagerBinder.java 编译依赖
import org.springframework.core.env.Environment; // Environment，ConfigManagerBinder.java 编译依赖
import org.springframework.stereotype.Component; // Spring 组件 stereotype 注解
import org.springframework.lang.NonNull; // NonNull，ConfigManagerBinder.java 编译依赖
/**
 * 启动完成后挂载 ConfigManager：保证 Netty ServerStartup 启动前 Environment 已可用，
 * 与 Groovy 策略 Bean 并行——Groovy 脚本热替换业务规则，ConfigManager 同步 YAML 参数给 Netty。
 */

@Component // Spring 单例组件

public class ConfigManagerBinder implements ApplicationListener<ApplicationReadyEvent>, Ordered { // ConfigManagerBinder 类型定义
    private final Environment environment; // 完整配置源，含 application.yml 与 Groovy 共存的环境
    public ConfigManagerBinder(Environment environment) { // 构造 ConfigManagerBinder，注入 Environment environment
        this.environment = environment; // 构造器注入 Spring 环境，供 bind 传递给单例
    } // ConfigManagerBinder 方法体结束

    @Override // 实现接口/父类方法
    public void onApplicationEvent(@NonNull ApplicationReadyEvent event) { // ConfigManagerBinder.onApplicationEvent：@NonNull ApplicationReadyEvent event
        ConfigManager.getInstance().bind(environment); // 一次性挂载，Netty 随后可读 game.netty.* 配置
    } // onApplicationEvent 方法体结束

    @Override // 实现接口/父类方法
    public int getOrder() { // 读取 Order（Order）
        return 0; // 尽早挂载，便于同阶段其它 Ready 监听器通过 ConfigManager 读配置
    } // getOrder 方法体结束
} // ConfigManagerBinder 类体结束
