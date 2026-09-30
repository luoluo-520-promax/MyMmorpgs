package cn.itcast.demo.mymmorpg.world.battle;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 战斗回放历史配置快照：按 config_version 索引，供回放加载当时规则。
 */
@Component
public class HistoricalConfigSnapshotStore {

    public record ConfigBaseline(String configVersion, String gitCommitSha, long serverEpoch, Map<String, Object> payload) {
    }

    private final ConcurrentHashMap<String, ConfigBaseline> baselines = new ConcurrentHashMap<>();

    public void save(String configVersion, String gitCommitSha, long serverEpoch, Map<String, Object> payload) {
        if (configVersion == null || configVersion.isBlank()) {
            return;
        }
        baselines.put(configVersion.trim(), new ConfigBaseline(
                configVersion.trim(),
                gitCommitSha == null ? "" : gitCommitSha.trim(),
                serverEpoch,
                payload == null ? Map.of() : Map.copyOf(payload)));
    }

    public ConfigBaseline find(String configVersion) {
        if (configVersion == null || configVersion.isBlank()) {
            return null;
        }
        return baselines.get(configVersion.trim());
    }

    public boolean exists(String configVersion) {
        return find(configVersion) != null;
    }

    public void remove(String configVersion) {
        if (configVersion != null && !configVersion.isBlank()) {
            baselines.remove(configVersion.trim());
        }
    }
}
