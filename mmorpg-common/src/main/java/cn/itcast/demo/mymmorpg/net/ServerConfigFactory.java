/**
 * 文件说明
 * 模块：mmorpg-common / 网络
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/net/ServerConfigFactory.java
 * 类型：类
 * 职责：定义 ServerConfigFactory，供各业务模块复用与扩展。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.net;


import org.springframework.boot.context.properties.EnableConfigurationProperties;

import org.springframework.context.annotation.Configuration;

/**
 * 注册 {@link ServerConfig}，实际属性由 {@link ServerConfigEnvironmentPostProcessor}
 * 合并 YAML 后绑定
 */
@Configuration
@EnableConfigurationProperties(ServerConfig.class)
public class ServerConfigFactory {
}
