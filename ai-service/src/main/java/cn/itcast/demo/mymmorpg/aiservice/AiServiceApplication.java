package cn.itcast.demo.mymmorpg.aiservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 统一 AI 推理微服务：推荐、NPC 记忆、客服 RAG、战术辅助、MLOps。
 * 不承载战斗 Tick（管道 A），仅服务准实时/交互式 AI（管道 B + 运营）。
 */
@ConditionalOnProperty(name = "spring.application.name", havingValue = "ai-service")
@SpringBootApplication(
        scanBasePackages = "cn.itcast.demo.mymmorpg.aiservice",
        exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
@EnableFeignClients(basePackages = "cn.itcast.demo.mymmorpg.aiservice")
public class AiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
