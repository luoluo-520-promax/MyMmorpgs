/**
 * 文件说明：战斗服务 JPA 数据访问层扫描配置。
 * 职责：显式声明 JpaRepository 与 Entity 的扫描包路径，避免主类位于子包时扫描不到 common 模块中的实体与仓库。
 * 注意：配置独立于 {@code BattleServiceApplication}，以免 {@code @WebMvcTest} 切片测试误解析 JPA 却无 EntityManagerFactory。
 */
package cn.itcast.demo.mymmorpg.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan; // 扫描 @Entity 实体类
import org.springframework.context.annotation.Configuration; // 标记为 Spring 配置类
import org.springframework.data.jpa.repository.config.EnableJpaRepositories; // 扫描 JpaRepository 接口

/**
 * 战斗服务主类位于子包，需显式扫描 common 中的 JPA 仓库与实体。
 * 不放在 {@code BattleServiceApplication} 上，以免 {@code @WebMvcTest} 切片解析到 JPA 却无 {@code EntityManagerFactory}。
 */
@Configuration // 独立配置类，由 Spring 容器加载
@ConditionalOnProperty(name = "spring.application.name", havingValue = "battle-service")
@EnableJpaRepositories(basePackages = "cn.itcast.demo.mymmorpg.repository") // 扫描 PlayerRepository、MonsterConfigRepository 等
@EntityScan(basePackages = "cn.itcast.demo.mymmorpg.entity") // 扫描 Player、MonsterConfig 等实体
public class BattleJpaConfiguration { // 空类，仅通过注解驱动 JPA 扫描
}
