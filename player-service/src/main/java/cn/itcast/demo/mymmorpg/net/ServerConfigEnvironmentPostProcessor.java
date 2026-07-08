/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/net/ServerConfigEnvironmentPostProcessor.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/net
 * 3) 主要职责：类 ServerConfigEnvironmentPostProcessor，承载模块内相关实现。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.net; // player-service Netty/WebSocket 网络层与 YAML 配置加载
import cn.itcast.demo.mymmorpg.net.ClassPathResourceLoaderFactory; // jar 内 config/*.yml 回退
import cn.itcast.demo.mymmorpg.net.FileSystemResourceLoaderFactory; // 工作目录 config/*.yml 优先
import cn.itcast.demo.mymmorpg.net.ResourceLoaderFactory; // 工厂方法加载 YAML 资源
import org.springframework.boot.SpringApplication; // EnvironmentPostProcessor 回调参数
import org.springframework.boot.env.EnvironmentPostProcessor; // Spring Boot 启动最早阶段注入配置
import org.springframework.boot.env.YamlPropertySourceLoader; // 解析 common.yml/server.yml 为 PropertySource
import org.springframework.core.env.ConfigurableEnvironment; // 可变 PropertySources
import org.springframework.core.env.MapPropertySource; // http.port -> server.port 覆盖
import org.springframework.core.env.MutablePropertySources; // addFirst 保证 YAML 优先级
import org.springframework.core.io.Resource; // YAML 文件资源
import java.io.IOException; // YamlPropertySourceLoader.load 异常
import java.util.HashMap; // syncSpringServerPort 临时 map
import java.util.List; // YAML 可能拆成多个 PropertySource（--- 文档分隔）
import java.util.Map; // MapPropertySource 键值
/**
 * 启动前加载游戏 YAML：common.yml -> 按 server.type 加载 center/gate/server.yml -> 同步 http.port 到 server.port。
 * <p>在 Spring @Value 与 Netty BaseServer 读端口之前完成，保证 MessageIoDispatcher 等 Bean 配置正确。</p>
 */
public class ServerConfigEnvironmentPostProcessor implements EnvironmentPostProcessor { // META-INF/spring 注册，早于 @Value 注入
    private static final YamlPropertySourceLoader YAML_LOADER = new YamlPropertySourceLoader(); // 解析 config/*.yml 为 PropertySource
    /** 优先读磁盘 config/，便于运维外置配置覆盖 jar 内默认值 */
    private static final ResourceLoaderFactory FILE_SYSTEM_FACTORY = new FileSystemResourceLoaderFactory(); // 外置 config/ 优先
    /** jar classpath 内 config/ 作为回退 */
    private static final ResourceLoaderFactory CLASS_PATH_FACTORY = new ClassPathResourceLoaderFactory(); // jar 内默认 YAML
    @Override // 实现接口/父类方法
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) { // Spring Boot 启动最早回调
        MutablePropertySources sources = environment.getPropertySources(); // 可变 PropertySources，addFirst 提高优先级
        Resource common = resolveConfig("config/common.yml"); // 全服通用：Redis、dispatch、idempotency 等
        if (common != null && common.exists()) { // 外置或 classpath 存在 common.yml
            addYaml(sources, "gameConfigCommon", common); // 注入 gameConfigCommon PropertySource
        } // 编译单元结束

        int typeCode = environment.getProperty("server.type", Integer.class, 1); // 默认 1=GAME，来自 application.properties
        ServerType type = ServerType.fromCode(typeCode); // 映射 CENTRE/GATE/GAME/FIGHT
        String specificFile = switch (type) { // 按进程角色选 YAML
            case CENTRE -> "config/center.yml"; // 中心服端口/RPC
            case GATE -> "config/gate.yml"; // 网关服
            case GAME, FIGHT -> "config/server.yml"; // 游戏/战斗服共用模板
        }; // 编译单元结束（含分号）
        Resource specific = resolveConfig(specificFile); // 解析角色专属 YAML
        if (specific != null && specific.exists()) { // 存在则加载
            addYaml(sources, "gameConfigSpecific", specific); // 后加载覆盖 common 同键配置
        } // 编译单元结束

        syncSpringServerPort(environment, sources); // 游戏 http.port 映射 Spring Boot server.port（WebSocket 端口）
    } // 编译单元结束

    /** 将 YAML 中 http.port 写入 server.port，Spring MVC/WebSocket 与游戏配置共用端口语义 */
    private static void syncSpringServerPort(ConfigurableEnvironment environment, MutablePropertySources sources) { // WebSocket 与 HTTP 共用端口
        Integer httpPort = environment.getProperty("http.port", Integer.class); // 从已加载 YAML 读 http.port
        if (httpPort != null && httpPort > 0) { // 有效端口才覆盖
            Map<String, Object> map = new HashMap<>(); // 单键 MapPropertySource
            map.put("server.port", httpPort); // Spring Boot 内置 server.port 键
            sources.addFirst(new MapPropertySource("gameHttpPortOverride", map)); // 最高优先级，WebSocket 与 MVC 同端口
        } // 编译单元结束
    } // 编译单元结束

    /** 解析 YAML 并 addFirst 到 PropertySources，后加载的 specific 覆盖 common */
    private static void addYaml(MutablePropertySources sources, String name, Resource resource) { // common/specific 共用
        try { // 代码块开始
            List<org.springframework.core.env.PropertySource<?>> loaded = YAML_LOADER.load(name, resource); // 可能多文档 --- 分隔
            for (org.springframework.core.env.PropertySource<?> ps : loaded) { // 逐个 PropertySource
                if (ps != null) { // 跳过空项
                    sources.addFirst(ps); // 游戏 YAML 优先于 application.properties
                } // 块 代码块结束
            } // 编译单元结束
        } catch (IOException e) { // YAML 损坏或不可读
            throw new IllegalStateException("Failed to load YAML: " + resource, e); // 启动失败，避免错误 dispatch/netty 配置
        } // 编译单元结束
    } // 编译单元结束

    /** 文件系统存在则用外置 config，否则 classpath；均不存在时返回 fs Resource 供 exists() 判断 */
    private static Resource resolveConfig(String relativePath) { // config/common.yml 等
        Resource fs = FILE_SYSTEM_FACTORY.load(relativePath); // 先试工作目录 ./config/
        if (fs.exists()) { // 外置配置存在
            return fs; // 运维外置 YAML 优先
        } // 编译单元结束

        Resource cp = CLASS_PATH_FACTORY.load(relativePath); // 回退 jar classpath
        if (cp.exists()) { // jar 内打包的默认配置
            return cp; // classpath config/*.yml
        } // 编译单元结束

        return fs; // 均不存在时返回 fs Resource（exists=false），调用方跳过加载
    } // 编译单元结束
} // 编译单元结束
