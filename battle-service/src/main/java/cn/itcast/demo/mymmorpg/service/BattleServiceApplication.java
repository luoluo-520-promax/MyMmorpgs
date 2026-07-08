/**
 * 文件说明：battle-service 模块的 Spring Boot 启动入口类。
 * 职责：以独立进程启动战斗微服务，扫描 common 与 battle 模块的全部 Bean，并启用 OpenFeign 远程调用。
 * 注意：JPA 扫描配置在 {@link cn.itcast.demo.mymmorpg.config.BattleJpaConfiguration} 中单独声明。
 */
package cn.itcast.demo.mymmorpg.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.SpringApplication; // Spring Boot 应用启动器
import org.springframework.boot.autoconfigure.SpringBootApplication; // 自动配置入口注解
import org.springframework.cloud.openfeign.EnableFeignClients; // 启用 OpenFeign 远程调用客户端

/**
 * battle-service 独立进程入口：启动 Web 容器、JPA、FightRpcServer 等组件。
 */
@ConditionalOnProperty(name = "spring.application.name", havingValue = "battle-service")
@SpringBootApplication(scanBasePackages = "cn.itcast.demo.mymmorpg") // 扫描 common + battle 模块全部 Bean
@EnableFeignClients(basePackages = "cn.itcast.demo.mymmorpg") // 注册 Feign 客户端接口
public class BattleServiceApplication { // battle-service 独立进程入口类

    /**
     * JVM 主函数：启动 Spring Boot 应用。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) { // JVM 主入口
        SpringApplication.run(BattleServiceApplication.class, args); // 启动 Web、JPA、FightRpcServer 等
    }
}
