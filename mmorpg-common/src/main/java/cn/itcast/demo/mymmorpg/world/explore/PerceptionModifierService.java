package cn.itcast.demo.mymmorpg.world.explore;

import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.world.time.WorldTimeService;
import cn.itcast.demo.mymmorpg.world.traverse.InputConfidenceAnalyzer;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * P19 感官干扰系统：天气/洞穴/光线适应对视听与操控的影响。
 */
@Service
public class PerceptionModifierService {

    public static final double HEAVY_RAIN_AUDIO_MUFFLE = 0.6;
    public static final double HEAVY_RAIN_VISUAL_BLUR = 0.3;
    public static final long EYE_ADAPTATION_MS = 2500L;
    public static final float ADAPTATION_SENSITIVITY_MUL = 0.7f;

    private RegionImpactService regions;
    private InputConfidenceAnalyzer inputConfidence;

    public void bindRegionImpact(RegionImpactService regions) {
        this.regions = regions;
    }

    public void bindInputConfidence(InputConfidenceAnalyzer analyzer) {
        this.inputConfidence = analyzer;
    }

    /**
     * 根据天气下发视听干扰参数。
     */
    public Map<String, Object> resolveWeatherModifiers(WorldTimeService.Weather weather) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("weather", weather == null ? WorldTimeService.Weather.CLEAR.name() : weather.name());
        if (weather == WorldTimeService.Weather.RAIN || weather == WorldTimeService.Weather.THUNDER) {
            body.put("event", "PERCEPTION_WEATHER");
            body.put("msgId", MessageId.PERCEPTION_MODIFIER_SC_NOTIFY);
            body.put("audioMuffle", HEAVY_RAIN_AUDIO_MUFFLE);
            body.put("visualBlur", HEAVY_RAIN_VISUAL_BLUR);
            body.put("hideEnemyRedDot", true);
            body.put("lodDowngradeExtra", 0.2f);
        } else if (weather == WorldTimeService.Weather.SNOW) {
            body.put("audioMuffle", 0.75);
            body.put("visualBlur", 0.15);
            body.put("hideEnemyRedDot", false);
        } else {
            body.put("audioMuffle", 1.0);
            body.put("visualBlur", 0.0);
            body.put("hideEnemyRedDot", false);
        }
        return body;
    }

    /**
     * 洞穴/室内声学：environmentAudioProfile + 回声延迟。
     */
    public Map<String, Object> resolveCaveAcoustics(String biome, int echoDelayMs) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("biome", biome == null ? "OPEN" : biome);
        if ("CAVE".equalsIgnoreCase(biome)) {
            body.put("environmentAudioProfile", "CAVE_REVERB");
            body.put("echoDelayMs", Math.max(80, echoDelayMs));
            body.put("caveDepthHint", echoDelayMs > 200 ? "DEEP" : "SHALLOW");
            body.put("serverEchoValidated", true);
        } else {
            body.put("environmentAudioProfile", "OPEN_AIR");
            body.put("echoDelayMs", 0);
        }
        return body;
    }

    /**
     * 亮暗切换：EYE_ADAPTATION_CURVE + 降低移动灵敏度。
     */
    public Map<String, Object> eyeAdaptation(long playerId, boolean enteringDark, long nowMs) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("event", "EYE_ADAPTATION_CURVE");
        body.put("msgId", MessageId.EYE_ADAPTATION_SC_NOTIFY);
        body.put("playerId", playerId);
        body.put("enteringDark", enteringDark);
        body.put("durationMs", EYE_ADAPTATION_MS);
        body.put("curve", enteringDark ? "DARKEN" : "BRIGHTEN");
        body.put("moveSensitivityMul", ADAPTATION_SENSITIVITY_MUL);
        if (inputConfidence != null) {
            body.put("effectiveWindowMs", InputConfidenceAnalyzer.effectiveWindowMs(16));
        }
        body.put("atMs", nowMs);
        return body;
    }

    public Map<String, Object> resolveForPlayer(
            long playerId, String regionId, WorldTimeService.Weather weather,
            String biome, int echoDelayMs, boolean enteringDark, long nowMs) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("regionId", regionId == null ? "" : regionId);
        body.putAll(resolveWeatherModifiers(weather));
        body.put("cave", resolveCaveAcoustics(biome, echoDelayMs));
        if (enteringDark) {
            body.put("eyeAdaptation", eyeAdaptation(playerId, true, nowMs));
        }
        if (regions != null && regionId != null) {
            body.put("regionSafety", regions.safetyOf(regionId).name());
        }
        return body;
    }
}
