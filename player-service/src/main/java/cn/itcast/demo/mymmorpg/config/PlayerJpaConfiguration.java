/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/config/PlayerJpaConfiguration.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/config
 * 3) 主要职责：仅在 player-service 进程内启用 JPA 仓库与实体扫描，统一管理 admin RBAC 与游戏实体。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */

package cn.itcast.demo.mymmorpg.config; // player-service 配置层：策略/缓存/Redis/dev 种子/Netty 开关

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // 按 spring.application.name 限定仅在 player-service 生效
import org.springframework.boot.autoconfigure.domain.EntityScan; // 扫描 cn.itcast.demo.mymmorpg.entity 下的 @Entity 映射
import org.springframework.context.annotation.Configuration; // 标记为 Spring 配置类，在容器刷新阶段注册 JPA 基础设施
import org.springframework.data.jpa.repository.config.EnableJpaRepositories; // 为 repository 包生成 JpaRepository 代理实现
/**
 * player-service JPA 数据访问层扫描配置。
 * 单体模式下嵌入 battle-service、activity-service，需由本服务统一声明仓库与实体扫描，
 * 避免多个 {@code @EnableJpaRepositories} 重复注册同一 Bean。
 */

@Configuration // 本类无显式 @Bean，仅通过注解向 Spring Data JPA 注册扫描范围
@ConditionalOnProperty(name = "spring.application.name", havingValue = "player-service") // 子模块若以其他 application.name 启动则跳过，防止重复扫描
@EnableJpaRepositories(basePackages = "cn.itcast.demo.mymmorpg.repository") // 含 AdminUserRepository 等 RBAC 仓储与 PlayerRepository 等游戏仓储

@EntityScan(basePackages = "cn.itcast.demo.mymmorpg.entity") // 含 AdminUser/AdminRole 等后台实体与 Player/ItemConfig 等游戏实体

public class PlayerJpaConfiguration { // PlayerJpaConfiguration 类型定义
    // 空类体：JPA 启用完全由类级别注解驱动，无需额外 Bean 定义
} // PlayerJpaConfiguration 类体结束
