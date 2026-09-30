package cn.itcast.demo.mymmorpg.service.integration.support;

import cn.itcast.demo.mymmorpg.web.InternalSceneController;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@Import(InternalSceneController.class)
public class SceneWebMvcTestApplication {
}
