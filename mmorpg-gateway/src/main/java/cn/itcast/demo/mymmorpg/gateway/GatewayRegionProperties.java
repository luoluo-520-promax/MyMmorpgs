package cn.itcast.demo.mymmorpg.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * game.gateway.region.*：全球同服就近接入应用层配置。
 */
@Component
@ConfigurationProperties(prefix = "game.gateway.region")
public class GatewayRegionProperties {

    /** 是否注入 X-Region / X-Edge-Pop */
    private boolean enabled = true;

    private String defaultRegion = "cn-east";

    private String defaultEdgePop = "pop-shanghai";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDefaultRegion() {
        return defaultRegion;
    }

    public void setDefaultRegion(String defaultRegion) {
        this.defaultRegion = defaultRegion;
    }

    public String getDefaultEdgePop() {
        return defaultEdgePop;
    }

    public void setDefaultEdgePop(String defaultEdgePop) {
        this.defaultEdgePop = defaultEdgePop;
    }
}
