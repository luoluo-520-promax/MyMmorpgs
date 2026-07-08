/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/config/PolicyConfiguration.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/config
 * 3) 主要职责：注册玩家域可热替换的业务策略 Bean（登录准入）。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */

package cn.itcast.demo.mymmorpg.config;

import cn.itcast.demo.mymmorpg.support.LoginPolicy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PolicyConfiguration {

    @Bean
    @ConditionalOnMissingBean(LoginPolicy.class)
    LoginPolicy loginPolicy() {
        return accountName -> accountName != null && !accountName.isBlank();
    }
}
