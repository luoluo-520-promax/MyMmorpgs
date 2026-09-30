package cn.itcast.demo.mymmorpg.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(UploadStorageProperties.class)
public class UploadStorageConfiguration {
}
