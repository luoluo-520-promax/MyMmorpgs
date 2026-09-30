package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.ai.platform.AiPlatformFacade;
import cn.itcast.demo.mymmorpg.client.AdminAiPlatformClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Admin 侧 AI 平台桥接：本地门面或远程 ai-service。
 */
@Service
public class AdminAiPlatformBridge {

    private final boolean remoteEnabled;
    private final ObjectProvider<AdminAiPlatformClient> remote;
    private volatile AiPlatformFacade local;

    public AdminAiPlatformBridge(
            @Value("${game.ai.remote.enabled:false}") boolean remoteEnabled,
            ObjectProvider<AdminAiPlatformClient> remote) {
        this.remoteEnabled = remoteEnabled;
        this.remote = remote;
    }

    public Map<String, Object> retentionEvaluate(long playerId, Map<String, Object> features) {
        if (useRemote()) {
            features.put("playerId", playerId);
            return remote.getObject().retentionEvaluate(features);
        }
        return localFacade().retentionEvaluate(playerId, features);
    }

    public Map<String, Object> contentGenerate(String type, String theme, Map<String, Object> knobs) {
        if (useRemote()) {
            return remote.getObject().contentGenerate(Map.of("type", type, "theme", theme, "knobs", knobs));
        }
        return localFacade().contentGenerate(type, theme, knobs);
    }

    public Map<String, Object> contentValidate(Map<String, Object> draft) {
        if (useRemote()) {
            return remote.getObject().contentValidate(draft);
        }
        return localFacade().contentValidate(draft);
    }

    private boolean useRemote() {
        return remoteEnabled && remote.getIfAvailable() != null;
    }

    private AiPlatformFacade localFacade() {
        if (local == null) {
            synchronized (this) {
                if (local == null) {
                    local = AiPlatformFacade.createDefault();
                }
            }
        }
        return local;
    }
}
