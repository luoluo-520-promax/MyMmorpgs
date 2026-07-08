/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/config/SocketServerAutoConfiguration.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/config
 * 3) 主要职责：Socket 层装配占位配置，与文档 SocketServerAutoConfiguration 对应，触发 net 包组件扫描。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */

package cn.itcast.demo.mymmorpg.config; // player-service 配置层：策略/缓存/Redis/dev 种子/Netty 开关

import org.springframework.context.annotation.Configuration; // 标记配置类，纳入 @SpringBootApplication 扫描范围
/**
 * Socket 层装配入口（与文档 {@code SocketServerAutoConfiguration} 对应）：
 * Netty {@link cn.itcast.demo.mymmorpg.net.BaseServer} 由 {@link cn.itcast.demo.mymmorpg.net.ServerStartup} 启动，
 * PlayerBinaryWebSocketHandler、ResourceLoaderFactory 等处理器由同级 net 包 @Component 扫描自动注册。
 */

@Configuration // 空配置体即可拉取 net 包下 @Component；若移除需确认 ServerStartup 仍被扫描

public class SocketServerAutoConfiguration { // SocketServerAutoConfiguration 类型定义
    //  intentionally empty：Netty 端口与 idle 检测在 net 包独立 Bean 中配置，避免与 WebSocketConfig 循环依赖
} // SocketServerAutoConfiguration 类体结束
