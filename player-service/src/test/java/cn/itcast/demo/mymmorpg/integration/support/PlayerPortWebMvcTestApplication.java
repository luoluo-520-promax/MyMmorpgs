package cn.itcast.demo.mymmorpg.integration.support;

import cn.itcast.demo.mymmorpg.web.InternalPlayerPortController;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@Import(InternalPlayerPortController.class)
public class PlayerPortWebMvcTestApplication {
}
