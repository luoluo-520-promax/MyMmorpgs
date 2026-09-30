package cn.itcast.demo.mymmorpg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "game.port.remote")
public class PortRemoteProperties {

    private boolean enabled = false;
    private String playerServiceUrl = "http://127.0.0.1:8989";
    private String sceneServiceUrl = "http://127.0.0.1:8981";
    private String bagServiceUrl = "http://127.0.0.1:8983";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPlayerServiceUrl() {
        return playerServiceUrl;
    }

    public void setPlayerServiceUrl(String playerServiceUrl) {
        this.playerServiceUrl = playerServiceUrl;
    }

    public String getSceneServiceUrl() {
        return sceneServiceUrl;
    }

    public void setSceneServiceUrl(String sceneServiceUrl) {
        this.sceneServiceUrl = sceneServiceUrl;
    }

    public String getBagServiceUrl() {
        return bagServiceUrl;
    }

    public void setBagServiceUrl(String bagServiceUrl) {
        this.bagServiceUrl = bagServiceUrl;
    }
}
