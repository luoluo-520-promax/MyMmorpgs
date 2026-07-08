/**
 * 文件维护说明
 * 1) 文件路径：chat-service/src/main/java/cn/itcast/demo/mymmorpg/service/ChatServiceApplication.java
 * 2) 所属模块：chat-service / service
 * 3) 主要职责：聊天微服务 Spring Boot 启动入口，扫描 cn.itcast.demo.mymmorpg 包下 ChatService 等 Bean。
 * 4) 变更建议：修改前先确认 Nacos 服务名 spring.application.name=chat-service 与网关路由一致。
 * 5) 风险提示：scanBasePackages 过宽会加载无关模块 Bean，过窄会漏扫 ChatPortConfiguration。
 */
package cn.itcast.demo.mymmorpg.service; // chat-service 启动类所在包，与 ChatService、ChatAsyncDbService 同包

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // 仅当 spring.application.name=chat-service 时注册本启动类 Bean
import org.springframework.boot.SpringApplication; // 启动 Spring 容器并阻塞 main 线程直至进程退出
import org.springframework.boot.autoconfigure.SpringBootApplication; // 启用自动配置、组件扫描与内嵌 Tomcat

/**
 * 聊天微服务启动类：独立进程运行 cmd=602/603 聊天协议处理。
 */
@ConditionalOnProperty(name = "spring.application.name", havingValue = "chat-service") // player-service 同 classpath 时不重复注册本 Application
@SpringBootApplication(scanBasePackages = "cn.itcast.demo.mymmorpg") // 扫描 ChatService、ChatPolicyConfiguration、GroovyChatPolicy、ChatPortConfiguration
public class ChatServiceApplication { // JVM main 入口类，打包为 chat-service-boot.jar 可执行

    /**
     * 标准 Java 入口：创建 SpringApplication 并启动聊天服务进程。
     *
     * @param args 命令行参数，可传入 --spring.profiles.active=dev 切换配置
     */
    public static void main(String[] args) { // JVM 启动 chat-service 微服务的唯一入口
        SpringApplication.run(ChatServiceApplication.class, args); // 加载 application.yml、注册 Redis/JPA Bean、监听 HTTP/WebSocket 端口
    }
}
