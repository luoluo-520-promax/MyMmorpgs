/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/net/FileSystemResourceLoaderFactory.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/net
 * 3) 主要职责：类 FileSystemResourceLoaderFactory，承载模块内相关实现。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.net; // player-service Netty/WebSocket 网络层与 YAML 配置加载
import org.springframework.core.io.FileSystemResource; // 相对 JVM 工作目录解析 config/*.yml
import org.springframework.core.io.Resource; // Spring 统一资源抽象
/**
 * 文件系统配置加载工厂：运维在部署目录放置 config/server.yml 即可覆盖 jar 内默认，无需重新打包。
 */
public class FileSystemResourceLoaderFactory extends ResourceLoaderFactory { // ServerConfigEnvironmentPostProcessor 优先使用
    @Override // 实现接口/父类方法
    protected GameResourceLoader createLoader() { // 模板方法由子类提供 FileSystem 加载器
        return FileSystemLoader.INSTANCE; // 单例枚举 FileSystemLoader
    } // 编译单元结束

    private enum FileSystemLoader implements GameResourceLoader { // 从进程工作目录读 config/
        INSTANCE; // 枚举单例
        @Override // 实现接口/父类方法
        public Resource load(String relativePath) { // 如 config/common.yml
            return new FileSystemResource(relativePath); // ./config/server.yml，exists() 判断外置配置是否存在
        } // 编译单元结束
    } // 编译单元结束
} // 编译单元结束
