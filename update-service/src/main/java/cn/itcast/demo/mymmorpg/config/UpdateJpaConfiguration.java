package cn.itcast.demo.mymmorpg.config; // update-service 的 JPA 配置，仅负责实体和仓库扫描

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // 仅在 update-service 进程中启用该配置
import org.springframework.boot.autoconfigure.domain.EntityScan; // 指定实体扫描范围
import org.springframework.context.annotation.Configuration; // 声明这是 Spring 配置类
import org.springframework.data.jpa.repository.config.EnableJpaRepositories; // 启用 JPA 仓库扫描

@Configuration // 将 JPA 扫描配置注册进 Spring 容器
@ConditionalOnProperty(name = "spring.application.name", havingValue = "update-service") // 仅在 update-service 应用名下生效
@EntityScan(basePackages = "cn.itcast.demo.mymmorpg.entity") // 扫描更新服务共享的实体包
@EnableJpaRepositories(basePackages = "cn.itcast.demo.mymmorpg.repository") // 扫描更新服务仓库接口包
public class UpdateJpaConfiguration { // 更新服务专用的 JPA 扫描配置
}
