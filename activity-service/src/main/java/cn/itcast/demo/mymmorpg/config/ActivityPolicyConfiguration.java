/**
 * 文件维护说明
 * 1) 文件路径：activity-service/src/main/java/cn/itcast/demo/mymmorpg/config/ActivityPolicyConfiguration.java
 * 2) 所属模块：activity-service / config
 * 3) 主要职责：在未提供自定义 ActivityPolicy Bean 时注册默认空实现
 * 4) 系统位置：Spring 配置层，供 ActivityService 领奖策略扩展使用
 * 5) 变更建议：Groovy 脚本或 Java 扩展策略时实现 ActivityPolicy 并注册为 Bean 即可覆盖
 */
package cn.itcast.demo.mymmorpg.config;

import cn.itcast.demo.mymmorpg.support.ActivityPolicy; // 活动领取策略接口
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean; // 缺少 Bean 时才注册
import org.springframework.context.annotation.Bean; // 声明 Spring Bean
import org.springframework.context.annotation.Configuration; // 配置类标记

/**
 * 活动领取策略的默认 Bean 配置。
 */
@Configuration(proxyBeanMethods = false) // 轻量配置，不代理 @Bean 方法
public class ActivityPolicyConfiguration { // 策略默认实现注册

    /**
     * 注册默认 ActivityPolicy（全部允许领取）。
     *
     * @return 使用接口 default 方法的空实现实例
     */
    @Bean // 注册为 Spring Bean
    @ConditionalOnMissingBean(ActivityPolicy.class) // 仅当容器中尚无 ActivityPolicy 时生效
    ActivityPolicy activityPolicy() {
        return new ActivityPolicy() { // 匿名类，继承 default allowClaimReward 行为
        }; // 返回默认策略实例
    }
}
