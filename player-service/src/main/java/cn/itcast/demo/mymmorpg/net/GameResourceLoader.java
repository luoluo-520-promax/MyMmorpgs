/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/net/GameResourceLoader.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/net
 * 3) 主要职责：游戏 YAML 配置资源加载策略接口，供 ServerConfigEnvironmentPostProcessor 启动前注入。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.net; // player-service Netty/WebSocket 网络层与 YAML 配置加载

import org.springframework.core.io.Resource; // Spring 统一资源抽象，供 YamlPropertySourceLoader 读取

/**
 * 游戏配置资源加载策略：按相对路径返回 config/common.yml 等 Resource，供 ServerConfigEnvironmentPostProcessor 使用。
 */
public interface GameResourceLoader { // FileSystem/ClassPath 两种实现，resolveConfig 按 exists() 选择

    /** @param relativePath 如 config/server.yml，相对工作目录或 classpath 根 */
    Resource load(String relativePath); // 返回 Resource 供 exists() 与 YamlPropertySourceLoader.load 使用
} // 编译单元结束
