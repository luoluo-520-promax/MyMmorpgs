package cn.itcast.demo.mymmorpg.config;

import cn.itcast.demo.mymmorpg.port.remote.InternalApiRestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(PortRemoteProperties.class)
@ConditionalOnProperty(name = "game.port.remote.enabled", havingValue = "true")
public class RemotePortAutoConfiguration {

    @Bean
    RestTemplate portRemoteRestTemplate() {
        return new RestTemplate();
    }

    @Bean
    InternalApiRestClient internalApiRestClient(RestTemplate portRemoteRestTemplate,
                                                InternalApiAuthProperties authProperties) {
        return new InternalApiRestClient(portRemoteRestTemplate, authProperties);
    }
}
