/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/support/ConfigManager.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/support
 * 3) 主要职责：非 Spring 组件（如 Netty Handler）读取 application.yml 的单例配置入口。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */

package cn.itcast.demo.mymmorpg.support; // player-service 业务策略接口与 JMX/ConfigManager

import org.springframework.core.env.Environment; // Environment，ConfigManager.java 编译依赖
/**
 * 配置管理器单例：Netty BaseServer 等无法 @Value 注入的代码通过 getInstance() 读 game.* 配置。
 * 与 Groovy 策略热替换互补——GroovyLoginPolicy 等 @Component 脚本可热更新业务规则（登录/技能/聊天策略），
 * 本单例则让 Netty pipeline 等非 Spring 代码同步读取同一份 Environment 中的端口/超时等 YAML 参数，
 * 运维改 yml 后重启或通过 ConfigManagerBinder 重新 bind 即可生效，无需改 Java 源码。
 */

public final class ConfigManager { // ConfigManager 类型定义
    private static final ConfigManager INSTANCE = new ConfigManager(); // 进程级唯一实例，Netty 线程安全读取
    /** volatile 保证 bind 后对 Netty worker 线程可见 */

    private volatile Environment environment; // Spring 完整配置源，含 yml/env/cmdline
    private ConfigManager() { // 构造 ConfigManager，注入 无参
    } // ConfigManager 方法体结束

    public static ConfigManager getInstance() { // 读取 Instance（Instance）
        return INSTANCE; // Netty Handler / Groovy 脚本外部代码的统一配置入口
    } // getInstance 方法体结束
    /**
     * 由 ConfigManagerBinder 在 ApplicationReadyEvent 时注入一次 Environment。
     */

    public void bind(Environment environment) { // ConfigManager.bind：Environment environment
        if (environment != null) { // ConfigManager.if：environment != null
            this.environment = environment; // 启动完成后挂载 Spring 配置，供 Netty 读 game.netty.port 等
        } // if 方法体结束
    } // bind 方法体结束
    /**
     * 读字符串配置：environment 未 bind 时回落 defaultValue，避免 Netty 早于 Spring 启动 NPE。
     */

    @SuppressWarnings("NullAway") // @SuppressWarnings 注解
    public String getString(String key, String defaultValue) { // 读取 String（String）
        Environment env = environment; // 局部副本避免 bind 与 read 之间的可见性抖动
        String def = defaultValue != null ? defaultValue : ""; // 缺失键时的业务默认值
        if (env == null) { // ConfigManager.if：env == null
            return def; // Spring 尚未就绪，Netty 启动阶段使用硬编码默认
        } // if 方法体结束
        return env.getProperty(key) != null ? env.getProperty(key) : def; // 读取 game.* / server.* 等业务配置
    } // getString 方法体结束
    /** 读整数配置：非数字或缺失时回落 defaultValue */

    public int getInt(String key, int defaultValue) { // 读取 Int（Int）
        String v = getString(key, ""); // 先取字符串再解析为整数
        try { // 代码块开始
            return Integer.parseInt(v.trim()); // 如 game.netty.port 转为监听端口
        } catch (NumberFormatException e) { // ConfigManager.catch：NumberFormatException e
            return defaultValue; // 配置值非法时使用业务默认端口/超时
        } // catch 方法体结束
    } // 块 代码块结束
} // getInt 方法体结束
