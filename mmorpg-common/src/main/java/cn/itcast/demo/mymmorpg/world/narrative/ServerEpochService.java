package cn.itcast.demo.mymmorpg.world.narrative;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 服务器唯一纪元：Redis 语义 {@code epoch_version}，重大抉择永久覆写本服世界状态，全分线同步。
 */
@Service
public class ServerEpochService {

    private final AtomicInteger epochVersion = new AtomicInteger(0);
    private final ConcurrentHashMap<String, Integer> globalFlags = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Map<String, Object>> worldOverrides = new ConcurrentHashMap<>();
    private final List<Map<String, Object>> history = new ArrayList<>();

    public int epochVersion() {
        return epochVersion.get();
    }

    public Map<String, Object> bumpFlag(String flag, int delta) {
        int v = globalFlags.merge(flag == null ? "" : flag.trim(), delta, Integer::sum);
        return Map.of("ok", true, "flag", flag, "value", v, "epoch", epochVersion.get());
    }

    public int flagValue(String flag) {
        return globalFlags.getOrDefault(flag == null ? "" : flag.trim(), 0);
    }

    /**
     * 达阈值后执行服务器级 WorldState 覆写（雕像/天气等），纪元+1。
     */
    public Map<String, Object> tryAdvanceEpoch(
            String flag, int threshold, Map<String, Object> worldStatePatch) {
        int v = flagValue(flag);
        if (v < threshold) {
            return Map.of("ok", false, "error", "threshold_not_met", "value", v, "need", threshold);
        }
        int next = epochVersion.incrementAndGet();
        Map<String, Object> patch = worldStatePatch == null
                ? Map.of() : new LinkedHashMap<>(worldStatePatch);
        patch.put("epoch_version", next);
        patch.put("triggerFlag", flag);
        worldOverrides.put("server", patch);
        Map<String, Object> row = new LinkedHashMap<>(patch);
        row.put("atEpoch", next);
        synchronized (history) {
            history.add(row);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("epoch_version", next);
        body.put("worldOverride", patch);
        body.put("syncAllLines", true);
        body.put("redisKey", "epoch_version");
        return body;
    }

    public Map<String, Object> currentOverride() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("epoch_version", epochVersion.get());
        body.put("worldOverride", worldOverrides.getOrDefault("server", Map.of()));
        body.put("flags", Map.copyOf(globalFlags));
        return body;
    }
}
