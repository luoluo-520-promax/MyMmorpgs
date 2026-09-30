package cn.itcast.demo.mymmorpg.config;

import cn.itcast.demo.mymmorpg.web.InternalApiAuthFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
@EnableConfigurationProperties(InternalApiAuthProperties.class)
@ConditionalOnProperty(name = "game.internal-api.enabled", havingValue = "true", matchIfMissing = true)
public class InternalApiAuthAutoConfiguration {

    @Bean
    FilterRegistrationBean<InternalApiAuthFilter> internalApiAuthFilter(InternalApiAuthProperties properties) {
        FilterRegistrationBean<InternalApiAuthFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new InternalApiAuthFilter(properties));
        bean.addUrlPatterns("/internal/*");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return bean;
    }
}
