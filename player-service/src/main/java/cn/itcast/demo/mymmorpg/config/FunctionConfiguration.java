/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/config/FunctionConfiguration.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/config
 * 3) 主要职责：启用 functions.* 配置绑定，将 function.yml 中的功能解锁规则注入 FunctionConfigService。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */

package cn.itcast.demo.mymmorpg.config; // player-service 配置层：策略/缓存/Redis/dev 种子/Netty 开关

import org.springframework.boot.context.properties.EnableConfigurationProperties; // 注册 FunctionConfigProperties 为可 @Autowired 的配置 Bean
import org.springframework.context.annotation.Configuration; // 功能解锁模块 Spring 入口

@Configuration(proxyBeanMethods = false) // 无 @Bean 方法互调，禁用 CGLIB 代理加快启动
@EnableConfigurationProperties(FunctionConfigProperties.class) // 绑定 application.yml / function.yml 中 functions.list 数组

public class FunctionConfiguration { // FunctionConfiguration 类型定义
    // 类体 intentionally empty：FunctionConfigService 直接注入 FunctionConfigProperties 读取解锁条件
} // FunctionConfiguration 类体结束
