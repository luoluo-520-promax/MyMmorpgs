/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/net/ClassPathResourceLoaderFactory.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/net
 * 3) 主要职责：类 ClassPathResourceLoaderFactory，承载模块内相关实现。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.net; // player-service Netty/WebSocket 网络层与 YAML 配置加载
import org.springframework.core.io.ClassPathResource; // 从 jar resources 根加载 config/*.yml
import org.springframework.core.io.Resource; // Spring 统一资源抽象
/**
 * 类路径配置加载工厂：FileSystem 不存在时回退到 src/main/resources/config 打包进 jar 的默认 YAML。
 */
public class ClassPathResourceLoaderFactory extends ResourceLoaderFactory { // resolveConfig 磁盘不存在时的回退
    @Override // 实现接口/父类方法
    protected GameResourceLoader createLoader() { // 模板方法由子类提供 ClassPath 加载器
        return ClassPathLoader.INSTANCE; // 单例枚举 ClassPathLoader，无状态线程安全
    } // 编译单元结束

    private enum ClassPathLoader implements GameResourceLoader { // 从 jar classpath 读 config/*.yml
        INSTANCE; // 枚举单例
        @Override // 实现接口/父类方法
        public Resource load(String relativePath) { // 如 config/common.yml
            return new ClassPathResource(relativePath); // classpath:config/server.yml，供 YamlPropertySourceLoader 读取
        } // 编译单元结束
    } // 编译单元结束
} // 编译单元结束
