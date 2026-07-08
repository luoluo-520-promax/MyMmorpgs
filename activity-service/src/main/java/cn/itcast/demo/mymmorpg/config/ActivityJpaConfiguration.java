/**
 * 文件维护说明
 * 1) 文件路径：activity-service/src/main/java/cn/itcast/demo/mymmorpg/config/ActivityJpaConfiguration.java
 * 2) 所属模块：activity-service / config
 * 3) 主要职责：显式配置 JPA 仓库与实体扫描路径（主类位于子包时需额外声明）
 * 4) 系统位置：Spring 配置层，启动时注册 ActivityRepository 与 Activity 等实体
 * 5) 变更建议：新增 repository/entity 包路径时同步更新 basePackages
 */
package cn.itcast.demo.mymmorpg.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan; // 扫描 @Entity 实体类
import org.springframework.context.annotation.Configuration; // Spring 配置类标记
import org.springframework.data.jpa.repository.config.EnableJpaRepositories; // 扫描 JpaRepository 接口

/**
 * 活动服务主类位于子包，需显式扫描 common 中的 JPA 仓库与实体。
 */
@Configuration // 标记为 Spring 配置类
@ConditionalOnProperty(name = "spring.application.name", havingValue = "activity-service")
@EnableJpaRepositories(basePackages = "cn.itcast.demo.mymmorpg.repository") // ActivityRepository 等在 common 包
@EntityScan(basePackages = "cn.itcast.demo.mymmorpg.entity") // Activity、Player 等实体在 common 包
public class ActivityJpaConfiguration { // 无方法，仅注解驱动扫描
}
