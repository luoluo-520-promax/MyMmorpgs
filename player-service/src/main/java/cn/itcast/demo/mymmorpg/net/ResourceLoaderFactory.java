/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/net/ResourceLoaderFactory.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/net
 * 3) 主要职责：类 ResourceLoaderFactory，承载模块内相关实现。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.net; // player-service Netty/WebSocket 网络层与 YAML 配置加载
import org.springframework.core.io.Resource; // 委托 GameResourceLoader 返回 FileSystem 或 ClassPath Resource
/**
 * 工厂方法模式：子类决定 FileSystem 或 ClassPath 加载器，ServerConfigEnvironmentPostProcessor 按优先级选用。
 */
public abstract class ResourceLoaderFactory { // ServerConfigEnvironmentPostProcessor 启动前加载 game YAML
    /** 由 FileSystemResourceLoaderFactory / ClassPathResourceLoaderFactory 实现 */
    protected abstract GameResourceLoader createLoader(); // 子类返回 FileSystem 或 ClassPath GameResourceLoader
    /** 模板方法：createLoader().load(relativePath) */
    public Resource load(String relativePath) { // resolveConfig 调用，如 config/common.yml
        return createLoader().load(relativePath); // 委托具体 GameResourceLoader 解析 Resource 路径
    } // 编译单元结束
} // 编译单元结束
