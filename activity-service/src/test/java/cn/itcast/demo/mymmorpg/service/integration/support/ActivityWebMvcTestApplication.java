/**
 * WebMvc 切片测试专用最小 Spring Boot 配置，避免加载完整 ActivityServiceApplication 触发全包扫描。
 */
package cn.itcast.demo.mymmorpg.service.integration.support;

import cn.itcast.demo.mymmorpg.web.InternalActivityController;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@Import(InternalActivityController.class)
public class ActivityWebMvcTestApplication {
}
