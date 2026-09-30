package cn.itcast.demo.mymmorpg.world.explore;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 神瞳共鸣：收集达 70% 时触发地图闪烁，提示剩余神瞳大致范围。
 */
@Service
public class OculiResonanceService {

    public static final int RESONANCE_THRESHOLD_PERCENT = 70;
    public static final long PULSE_DURATION_MS = 5_000L;
    public static final float PULSE_RADIUS_M = 80f;

    private final CollectibleService collectibles;
    private final ConcurrentHashMap<Long, Long> lastPulse = new ConcurrentHashMap<>();

    public OculiResonanceService() {
        this(new CollectibleService());
    }

    public OculiResonanceService(CollectibleService collectibles) {
        this.collectibles = collectibles == null ? new CollectibleService() : collectibles;
    }

    public Map<String, Object> evaluateResonance(long playerId, int regionId, long nowMs) {
        int total = collectibles.countOculiInRegion(regionId);
        int collected = collectibles.collectedOculiInRegion(playerId, regionId);
        int percent = total <= 0 ? 0 : (collected * 100) / total;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("regionId", regionId);
        body.put("oculiPercent", percent);
        body.put("resonanceThreshold", RESONANCE_THRESHOLD_PERCENT);

        if (percent < RESONANCE_THRESHOLD_PERCENT || percent >= 100) {
            body.put("resonanceTriggered", false);
            return body;
        }

        Long last = lastPulse.get(playerId);
        if (last != null && nowMs - last < 3600_000L) {
            body.put("resonanceTriggered", false);
            body.put("cooldownRemainMs", 3600_000L - (nowMs - last));
            return body;
        }

        lastPulse.put(playerId, nowMs);
        List<Map<String, Object>> hints = collectibles.remainingOculiHints(playerId, regionId, PULSE_RADIUS_M);
        body.put("resonanceTriggered", true);
        body.put("event", "OCULI_RESONANCE_PULSE");
        body.put("pulseDurationMs", PULSE_DURATION_MS);
        body.put("pulseRadiusM", PULSE_RADIUS_M);
        body.put("remainingHints", hints);
        body.put("clientHint", "共鸣波：地图上短暂闪烁剩余神瞳大致范围");
        return body;
    }
}
